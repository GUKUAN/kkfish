# KKFish 重构优化计划

> 基于 **app-style-minecraft-plugin-architecture** 架构原则制定
> 目标：将 manager 重量级插件重构为可扩展的 APP-style 模块化架构

---

## 一、现状核心问题

| 问题类别 | 严重度 | 典型表现 |
|---------|-------|---------|
| God Class | 🔴 高 | `Fish.java`(2740行)、`GUI.java`(2726行)、`Cmd.java`(1852行)、`GameSession.java`(837行)、`kkfish.java` |
| 死代码 | 🔴 高 | managers/ 5个空文件、listeners/ 3个空文件、misc/ 1个空文件 |
| 同名跨包混淆 | 🟠 中 | `SoundManager`/`Fishing`/`GUIMenuLoader`/`DependencyManager`/`ItemCraft` 在多包存在 |
| 紧耦合 | 🔴 高 | 所有 manager 通过 `plugin.getXxx()` 互访，`GUIHolder` 依赖 `managers.GUI`（循环依赖风险） |
| 状态分散 | 🔴 高 | `Fish.java` 持有 14+ Map，`GameSession` 20+ 字段，无统一 PlayerContext |
| 缺失事件总线 | 🟠 中 | manager 间直接方法调用，`MinigameManager.endGame()` 内联 8 个跨域操作 |
| 缺失生命周期 | 🟠 中 | Config/ItemValue/AuraSkills/MessageManager/Metrics 等无 close 方法 |
| 代码重复 | 🟠 中 | `getMaterialFromType`、`teleportAsync` 反射、import 语句重复 |
| Folia 安全隐患 | 🔴 高 | `EntityBatchProcessor` 直接用 `Particle` 枚举和 `spawnParticle()`；`HookMechanic` 反射 `teleportAsync`；`Bukkit.broadcastMessage`/`sendTitle` 非区域感知 |
| 反射重 | 🟠 中 | AuraSkills/NBTUtil/XSeriesUtil/DependencyManager/kkfish/HookMechanic 大量反射，无 Method 缓存 |
| 版本检测分散 | 🟡 低 | 5 处独立版本检测逻辑 |
| 无清晰分层 | 🔴 高 | UI 直接访问 DB/Economy，Domain 直接访问 UI/Persistence |
| 规则违反 | 🔴 高 | `EntityBatchProcessor` 违反 XSeries 规则（直接 `Particle` 枚举 + `spawnParticle`） |

**亮点保留**：Scheduler 抽象、Competition 域模型（会话+策略）、HookMechanic 策略工厂、DB 缓存重连、MessageManager 多语言。

---

## 二、目标架构

### 2.1 模块所有权树

```
KkfishPlugin (JavaPlugin, 仅生命周期委托)
  └── RootService (AutoCloseable, 组合根 + 关闭顺序)
        ├── ConfigurationService        // 不可变配置快照
        ├── SchedulerService            // 已有 SchedulerProvider, 包装统一入口
        ├── VersionService              // 统一版本检测（合并 5 处）
        ├── MessageService              // 渲染消息（含 PlaceholderAPI 隔离）
        ├── EventBus                    // 根事件总线 + 平台监听桥
        ├── DependencyService           // 运行时 jar 加载（原 DependencyManager）
        ├── EconomyService              // Vault + PlayerPoints 统一经济门面
        ├── SeasonsService              // RealisticSeasons 反射隔离
        ├── AuraSkillsService           // AuraSkills 反射隔离 + Method 缓存
        ├── DataSource                  // 持久化抽象（原 DB, 快照模式）
        ├── PlayerContextStore          // 玩家上下文创建/加载/保存/关闭
        ├── ItemFactory                 // 鱼竿/鱼饵/鱼物品模板 + 触发器注册
        ├── UiRuntime                   // 每玩家 UI 资源生命周期
        │     ├── MenuRegistry          // 菜单定义注册表
        │     ├── ScoreboardService     // 竞赛记分板
        │     └── ActionBarService      // 持久 ActionBar（原 ActionBarUtil）
        ├── HookMechanicRegistry        // 鱼钩机制注册表（原 HookMechanicFactory）
        └── DomainServices
              ├── FishingService        // 钓鱼流程编排（拆自 Fish.java）
              ├── MinigameService       // 小游戏会话编排（拆自 MinigameManager）
              ├── CompetitionService    // 竞赛编排（原 Compete）
              ├── SellService           // 出售流程（拆自 Cmd/GUIListener）
              └── RewardService         // 奖励发放（拆自 MinigameManager.endGame）
```

### 2.2 分层边界（强制）

```
┌─────────────────────────────────────────┐
│  Commands / Listeners (入口适配层)        │  仅：解析参数 → 调服务 → 渲染结果
├─────────────────────────────────────────┤
│  Domain Services (业务编排层)             │  仅：业务规则 + 会话生命周期 + 事件发布
├─────────────────────────────────────────┤
│  PlayerContext / Sessions (上下文层)      │  仅：玩家/会话状态 + 生命周期标签
├─────────────────────────────────────────┤
│  Infrastructure (基础设施层)              │  DataSource / EconomyService / Scheduler
└─────────────────────────────────────────┘
```

**禁止规则**：
- Commands 不得直接访问 DataSource/NMS/Session 内部 Map
- Listeners 不得直接持久化或渲染 UI（必须路由到服务）
- Domain Services 不得直接操作 NMS 包或原始配置树
- 持久化层不得持有 `Player`/`Inventory`/`Entity` 等运行时对象

---

## 三、目标包结构

```
me.kkfish
├── KkfishPlugin.java                    // JavaPlugin, 仅 onEnable/onDisable 委托
├── bootstrap
│   └── RootService.java                 // 组合根, AutoCloseable
├── config
│   ├── ConfigurationService.java        // 不可变快照
│   ├── ConfigSnapshot.java
│   └── migrations/                      // 配置迁移
├── scheduler
│   ├── SchedulerService.java            // 已有, 整合 SchedulerProvider
│   ├── SchedulerProvider.java           // 保留
│   ├── SchedulerProviderFactory.java    // 保留
│   └── SchedulerTask.java               // 保留
├── platform
│   ├── VersionService.java              // 统一版本检测
│   ├── NmsAdapter.java                  // NMS 隔离接口
│   └── NbtAdapter.java                  // NBT 隔离（原 NBTUtil）
├── messaging
│   ├── MessageService.java              // 原 MessageManager
│   ├── LanguageRenderer.java
│   └── ActionBarService.java            // 原 ActionBarUtil（实例化, 非 static）
├── events
│   ├── EventBus.java                    // 根事件总线
│   ├── ScopedEventBus.java              // 会话作用域总线
│   ├── FishingEvent.java                // 域事件
│   ├── CompetitionEvent.java
│   └── MinigameEvent.java
├── economy
│   └── EconomyService.java              // Vault + PlayerPoints 门面
├── integrations
│   ├── SeasonsService.java              // RealisticSeasons 隔离
│   ├── AuraSkillsService.java           // AuraSkills 隔离 + Method 缓存
│   └── DependencyService.java           // 原 misc.DependencyManager
├── storage
│   ├── DataSource.java                  // 原 DB, 快照模式
│   ├── PlayerDataSnapshot.java          // 不可变快照
│   ├── PlayerDataStore.java
│   └── cache/                           // 缓存策略集中
├── player
│   ├── PlayerContextStore.java          // 创建/加载/保存/关闭
│   ├── PlayerContext.java               // 身份/持久/会话/运行时/生命周期
│   ├── PlayerIdentity.java
│   ├── PersistentPlayerData.java
│   ├── SessionData.java
│   ├── RuntimeData.java
│   └── LifecycleTag.java                // CREATED→LOADING→ACTIVE→...
├── items
│   ├── ItemFactory.java                 // 物品模板 + 复制
│   ├── ItemTriggerRegistry.java         // 触发器链
│   ├── ItemTriggerContext.java
│   ├── RodItemBuilder.java              // 拆自 Cmd/Fish
│   ├── BaitItemBuilder.java
│   └── FishItemBuilder.java
├── ui
│   ├── UiRuntime.java                   // 每玩家 UI 资源 + 替换规则
│   ├── MenuRegistry.java                // 菜单定义注册表
│   ├── MenuDefinition.java
│   ├── MenuView.java
│   ├── MenuRenderContext.java
│   ├── ScoreboardService.java           // 竞赛记分板
│   ├── ScoreboardBlueprint.java
│   ├── ScoreboardPipeline.java
│   └── menus/                           // 具体菜单定义
│       ├── MainMenu.java
│       ├── FishDexMenu.java
│       ├── HookMaterialMenu.java
│       ├── SellMenu.java
│       ├── CompetitionMenu.java
│       ├── FishRecordMenu.java
│       ├── HelpMenu.java
│       └── RewardPreviewMenu.java
├── fishing
│   ├── FishingService.java              // 钓鱼流程编排
│   ├── HookMechanicRegistry.java        // 原 HookMechanicFactory
│   ├── HookMechanic.java                // 保留接口
│   ├── WaterHookMechanic.java           // 重构: 移除 static Map, 注入依赖
│   ├── LavaHookMechanic.java
│   ├── VoidHookMechanic.java
│   ├── WaterType.java                   // 保留
│   ├── FishingSession.java              // 钓鱼会话（原 Fish.FishingSession）
│   ├── BiteScheduler.java               // 拆自 Fish.scheduleBiteCheck
│   ├── ChargeProgressTracker.java       // 拆自 Fish.ChargeProgressTask
│   ├── FloatingEffectTask.java          // 拆自 Fish.floatingEffectTasks
│   └── TrajectoryTracker.java           // 拆自 Fish.trajectoryTasks
├── minigame
│   ├── MinigameService.java             // 会话编排（拆自 MinigameManager）
│   ├── MinigameSession.java             // 物理模拟（拆自 GameSession, 仅物理）
│   ├── MinigameRenderer.java            // UI 渲染（拆自 GameSession.displayGameUI）
│   ├── FishSelector.java                // 鱼选择（拆自 GameSession.getRandomFish）
│   └── MinigamePhysics.java             // 物理常量配置
├── competition
│   ├── CompetitionService.java          // 编排（原 Compete）
│   ├── Competition.java                 // 保留抽象基类
│   ├── CompetitionConfig.java           // 保留
│   ├── CompetitionData.java             // 保留
│   ├── CompetitionSession.java          // 会话包装
│   └── types/                           // 保留策略实现
│       ├── AmountCompetition.java
│       ├── PointsOnlyCompetition.java
│       ├── SingleValueCompetition.java
│       └── TotalValueCompetition.java
├── sell
│   └── SellService.java                 // 出售流程（拆自 Cmd/GUIListener）
├── reward
│   └── RewardService.java               // 奖励发放（拆自 MinigameManager.endGame）
├── commands
│   ├── CommandAdapter.java              // 命令适配器基类
│   ├── KkfishCommand.java               // 主命令路由
│   └── subcommands/
│       ├── HelpCommand.java
│       ├── ReloadCommand.java
│       ├── GiveCommand.java
│       ├── GuiCommand.java
│       ├── SellCommand.java
│       ├── CompeteCommand.java
│       ├── AddCommand.java
│       ├── UnlockCommand.java
│       ├── ToggleCommand.java
│       └── VersionCommand.java
├── listeners
│   ├── FishingListener.java             // 仅事件路由
│   ├── GuiListener.java                 // 仅事件路由
│   ├── CraftListener.java               // 仅事件路由
│   ├── PlayerJoinListener.java          // 加载 PlayerContext
│   ├── PlayerQuitListener.java          // 保存/关闭 PlayerContext
│   └── PlayerPreLoginListener.java      // 防重复登录
└── utils
    ├── XSeriesUtil.java                 // 保留, 修复 EntityBatchProcessor 违规
    ├── EntityBatchProcessor.java        // 重构: 走 XSeriesUtil
    └── MaterialResolver.java            // 合并重复的 getMaterialFromType
```

---

## 四、重构阶段（按依赖顺序）

### 阶段 0：清理与规则修复（低风险，先做）

**目标**：删除死代码、修复规则违反、消除同名跨包混淆。

| 任务 | 文件 | 说明 |
|-----|------|------|
| 删除空文件 | `managers/ItemCraft.java`、`managers/SoundManager.java`、`managers/Fishing.java`、`managers/GUIMenuLoader.java`、`managers/DependencyManager.java`、`listeners/SoundManager.java`、`listeners/DependencyManager.java`、`listeners/GUIMenuLoader.java`、`misc/GUIMenuLoader.java` | 9 个空文件 |
| 修复 XSeries 违规 | `utils/EntityBatchProcessor.java` | 改用 `XSeriesUtil.spawnParticle`，移除 `Particle.REDSTONE`/`DustOptions` 直接使用 |
| 合并重复 import | `MinigameManager.java`、`MessageManager.java`、`UpdateChecker.java`、`AuraSkills.java`、`GUIListener.java`、`ItemValue.java`、`CompetitionConfig.java` | 删除重复 import |
| 合并重复 `getMaterialFromType` | `Fish.java`、`GUI.java` → `utils/MaterialResolver.java` | 统一实现 |
| 合并重复 `teleportAsync` 反射 | `WaterHookMechanic.java`、`LavaHookMechanic.java`、`VoidHookMechanic.java` → `platform/NmsAdapter.java` | 统一 NMS 隔离 |

**验收**：编译通过，功能无回归。

---

### 阶段 1：提取基础设施服务（无行为变更）

**目标**：把分散的基础设施逻辑收敛为服务，为后续解耦打基础。

| 任务 | 输入 | 输出 |
|-----|------|------|
| `VersionService` | `kkfish.detectServerVersion()`、`NBTUtil`、`XSeriesUtil`、`Metrics` 的版本检测 | 统一版本检测入口 |
| `ConfigurationService` | `managers/Config.java` | 不可变 `ConfigSnapshot`，热路径不读原始配置树 |
| `MessageService` | `misc/MessageManager.java` | 实例化（非 static 单例），渲染隔离 |
| `SchedulerService` | 已有 `SchedulerProvider` | 包装统一入口，废弃 `SchedulerUtil` 的 plugin 参数 |
| `EconomyService` | `kkfish.setupEconomy()`、`setupPlayerPoints()` | Vault + PlayerPoints 统一门面 |
| `SeasonsService` | `kkfish.setupRealisticSeasons()`、`getCurrentSeason()` | 反射隔离 + Method 缓存 |
| `AuraSkillsService` | `handlers/AuraSkills.java` | 反射 Method 缓存 |
| `DependencyService` | `misc/DependencyManager.java` | 类加载器操作隔离 |
| `NbtAdapter` | `utils/NBTUtil.java`、`NBTUtilAPI.java` | 接口隔离 |

**验收**：所有原功能通过新服务访问，无 static 单例新增。

---

### 阶段 2：引入 RootService 与启动顺序

**目标**：把 `kkfish.java` 瘦身为生命周期适配器。

**启动顺序（显式）**：
```
load ConfigurationService
  → SchedulerService
  → VersionService
  → DependencyService (加载 jar)
  → MessageService
  → EventBus + ListenerBridge
  → DataSource
  → PlayerContextStore
  → ItemFactory (注册物品模板)
  → UiRuntime + MenuRegistry
  → HookMechanicRegistry
  → DomainServices (Fishing/Minigame/Competition/Sell/Reward)
  → 注册 Listeners 和 Commands
  → 启动 tick 任务
  → 开放玩家接入
```

**关闭顺序（显式）**：
```
停止接受新动作
  → 取消 tick 任务
  → 关闭活跃会话/UI
  → 关闭作用域事件总线
  → 快照并刷写玩家/域数据
  → 关闭存储/网络资源
  → 释放世界资源
  → 清理注册表和 static 引用
```

**验收**：`kkfish.java` 仅含 `onLoad`/`onEnable`/`onDisable` 委托，无业务逻辑字段。

---

### 阶段 3：引入 PlayerContext 与 PlayerContextStore

**目标**：统一玩家状态管理，消除分散 Map。

**PlayerContext 结构**：
```
PlayerContext
  ├── Identity: UUID, name, creationTime
  ├── PersistentData: 钓鱼统计、记录、已购鱼钩、语言偏好
  ├── SessionData: 当前钓鱼会话/小游戏会话/活跃菜单/记分板/ActionBar
  ├── RuntimeData: 冷却、临时标记、钓鱼模式
  ├── Lifecycle: CREATED→LOADING→ACTIVE→QUIT_PENDING→SAVING→DESTROYING→CLEANED
  └── DataLoop: 每玩家异步 IO 串行门
```

**迁移映射**（原 → 新）：
| 原位置 | 字段 | 新位置 |
|-------|------|-------|
| `Fish.activeSessions` | 钓鱼会话 | `SessionData.fishingSession` |
| `Fish.minigameData` | 小游戏数据 | `SessionData.minigameData` |
| `Fish.playerFishRecords` | 钓鱼记录 | `PersistentData.fishRecords` |
| `Fish.playerHookMaterials` | 鱼钩材质 | `SessionData.hookMaterial` |
| `Fish.playerCooldowns` | 冷却 | `RuntimeData.cooldowns` |
| `Fish.playerWaterType` | 水域类型 | `SessionData.waterType` |
| `Fish.playerHookMechanic` | 鱼钩机制 | `SessionData.hookMechanic` |
| `MinigameManager.gameSessions` | 小游戏会话 | `SessionData.minigameSession` |
| `GUI.fishDexPages` 等 | GUI 分页 | `SessionData.menuView` |
| `ActionBarUtil.persistentMessageTasks` | 持久 ActionBar | `SessionData.actionBar` |
| `MessageManager.playerLangCache` | 语言缓存 | `PersistentData.language` |
| `kkfish.playerFishingMode` | 钓鱼模式 | `RuntimeData.fishingMode` |

**生命周期处理**：
- `PlayerPreLoginEvent`：拒绝旧 context 仍在 SAVING/DESTROYING 的重连
- `PlayerJoinEvent`：异步加载 `PersistentData`，加载完成前 gate 业务动作
- `PlayerQuitEvent`：标记 `QUIT_PENDING`，若无活跃会话则立即快照保存
- 玩家在会话中退出：延迟保存直到会话关闭并快照所有需要的状态

**验收**：`Fish.java` 的 14+ Map 迁移到 PlayerContext，无玩家状态散落在 manager 中。

---

### 阶段 4：引入 EventBus 与域事件

**目标**：解耦 manager 间直接方法调用。

**事件总线模型**：
```
RootEventBus
  ├── 平台监听桥（PlatformListenerBridge）
  ├── subscribe/unsubscribe tokens
  └── 事件类分发

ScopedEventBus (会话作用域)
  ├── 包装根总线
  ├── 按 UUID/World/Region 过滤
  └── 关闭时取消所有 token
```

**域事件定义**：
| 事件 | 发布者 | 订阅者 |
|-----|-------|-------|
| `FishCaughtEvent` | `MinigameService` | `FishingService`(记录)、`CompetitionService`(计分)、`RewardService`(发奖)、`MessageService`(广播) |
| `CompetitionStartedEvent` | `CompetitionService` | `ScoreboardService`、`MessageService` |
| `CompetitionEndedEvent` | `CompetitionService` | `ScoreboardService`、`RewardService` |
| `MinigameStartedEvent` | `MinigameService` | `ActionBarService`、`SoundManager` |
| `MinigameEndedEvent` | `MinigameService` | `ActionBarService`、`SoundManager` |
| `PlayerContextLoadedEvent` | `PlayerContextStore` | 各域服务初始化玩家状态 |
| `PlayerContextClosingEvent` | `PlayerContextStore` | 各域服务清理玩家资源 |

**重构 `MinigameManager.endGame()`**（当前内联 8 个跨域操作）：
```
MinigameService.endGame()
  → 关闭 MinigameSession
  → 发布 FishCaughtEvent(player, fish, value, rarity)
  → 各订阅者各自处理（记录/计分/发奖/广播）
```

**验收**：`MinigameManager.endGame()` 不再直接调用 `plugin.getFish()`/`plugin.getCompete()`/`plugin.getAuraSkills()`。

---

### 阶段 5：拆分 God Class

#### 5.1 拆分 `Fish.java`（2740 行 → 多个聚焦类）

| 拆分目标 | 原职责 | 新类 |
|---------|-------|------|
| 钓鱼流程编排 | `startFishing`/`scheduleBiteCheck`/`handleBite` | `FishingService` |
| 钓鱼会话状态 | `FishingSession` 内部类 | `FishingSession`（独立类） |
| 充能进度 | `ChargeProgressTask` | `ChargeProgressTracker` |
| 浮动效果 | `floatingEffectTasks` | `FloatingEffectTask` |
| 轨迹追踪 | `trajectoryTasks` | `TrajectoryTracker` |
| 鱼记录 | `playerFishRecords` + `FishRecord` | `PlayerContext.PersistentData` |
| 鱼物品创建 | `createFishItem` 等 | `ItemFactory` + `FishItemBuilder` |
| 材质映射 | `getMaterialFromType` | `MaterialResolver` |
| 数据库写入 | `storeFishUUIDValue`/`storeFishEffects` | `DataSource` |
| 广播 | `sendFishBroadcast` | 订阅 `FishCaughtEvent` |

#### 5.2 拆分 `GUI.java`（2726 行 → MenuRegistry + 菜单定义）

| 拆分目标 | 新类 |
|---------|------|
| 菜单注册表 | `MenuRegistry` |
| 菜单定义 | `MenuDefinition` + `menus/*Menu.java` |
| 菜单视图 | `MenuView`（每玩家） |
| 物品构建 | `ItemFactory` |
| 分页/搜索/排序状态 | `MenuView` 内部状态 |
| 鱼钩购买 | `SellService` 或独立 `HookPurchaseService` |

**菜单渲染流程**：
```
open 请求
  → 调度到正确线程
  → 创建/获取 MenuView
  → 进入 MenuDefinition
  → 绘制静态节点（一次）
  → 每 tick 绘制动态节点（脏标记或限频）
  → 广播容器变更（仅变更项）
```

**点击流程**：
```
InventoryClickEvent
  → 解析玩家 MenuView
  → 映射 raw slot 到布局节点
  → 构建 MenuRenderContext
  → 运行触发器链
  → 触发器可取消事件或请求菜单切换
```

#### 5.3 拆分 `Cmd.java`（1852 行 → CommandAdapter + 子命令）

| 子命令 | 新类 | 职责 |
|-------|------|------|
| `help` | `HelpCommand` | 渲染帮助 |
| `reload` | `ReloadCommand` | 调 `ConfigurationService.reload()` |
| `give` | `GiveCommand` | 调 `ItemFactory` 创建物品 |
| `gui` | `GuiCommand` | 调 `UiRuntime.openMenu()` |
| `sell` | `SellCommand` | 调 `SellService` |
| `compete` | `CompeteCommand` | 调 `CompetitionService` |
| `add` | `AddCommand` | 调 `ConfigurationService` 修改配置 |
| `unlock`/`lock` | `UnlockCommand` | 调 `PlayerContextStore` 修改持久数据 |
| `toggle` | `ToggleCommand` | 调 `PlayerContextStore` 修改运行时数据 |
| `version` | `VersionCommand` | 调 `VersionService` |

**命令规则**：
- 每个命令方法保持薄
- 解析参数 → 校验权限 → 解析 context → 调服务 → 渲染结果
- 不访问 DataSource/NMS/Session Map
- 返回服务结果对象，不直接 inspect 内部 Map

#### 5.4 拆分 `GameSession.java`（837 行）

| 拆分目标 | 新类 |
|---------|------|
| 物理模拟 | `MinigamePhysics` + `MinigameSession`（仅物理状态） |
| UI 渲染 | `MinigameRenderer` |
| 鱼选择 | `FishSelector` |
| 价值计算 | `FishValueCalculator`（复用 `ItemValue`） |
| 物品创建 | `ItemFactory` |

**验收**：无单个类超过 600 行，每个类单一职责。

---

### 阶段 6：重构持久化层（快照模式）

**目标**：持久化层不持有运行时对象，异步保存使用快照。

**DataSource 模型**：
```
DataSource
  ├── lookup(UUID) → PlayerDataSnapshot
  ├── cached(UUID)
  ├── login(UUID, name) → 异步加载
  ├── logout(UUID) → 异步保存
  ├── save(PlayerDataSnapshot)
  ├── forEachCached(...)
  └── close() → drain 并关闭连接
```

**规则**：
- 永远不从 IO 线程直接保存可变 live context
- 保存前复制持久数据到独立 `PlayerDataSnapshot`
- `Player`/`Inventory`/`Entity`/`Scoreboard`/`Menu` 不得存入持久实体
- 关闭 drain 有界策略 + 长保存日志
- PreLogin 拒绝旧 context 仍在保存/销毁时的重连

**验收**：`DB.java` 重构为 `DataSource`，无运行时对象泄漏到持久层。

---

### 阶段 7：重构 UI 框架（MenuRegistry + 生命周期）

**目标**：菜单由定义驱动，每玩家视图独立，替换前关闭旧资源。

**关键规则**：
- 打开菜单前关闭该玩家当前活跃菜单视图
- 玩家退出/插件禁用必须移除视图并清理缓存
- 静态节点缓存键包含所有影响输出的输入
- 动态布局依赖玩家状态时必须 per-view
- 限制每 tick 渲染工作量，动态节点脏标记或限频
- `InventoryClickEvent`/`InventoryCloseEvent` 显式桥接到菜单运行时

**ScoreboardService**：
- 活跃记分板存入 `SessionData`，替换前关闭旧的
- 使用平台调度器，非 unmanaged executor
- 缓存注解字段绑定和占位符元数据
- 尊重 15 行侧边栏限制
- PlaceholderAPI 视为非线程安全且昂贵，限频或缓存

**验收**：`GUI.java` 拆解完成，`SlotMapping` 硬编码常量由 yml 配置驱动。

---

### 阶段 8：Folia 安全加固

**目标**：消除 Folia 隐患，统一调度边界。

| 问题 | 修复 |
|-----|------|
| `EntityBatchProcessor` 直接 `Particle` 枚举 + `spawnParticle` | 走 `XSeriesUtil.spawnParticle`，粒子生成走区域调度 |
| `HookMechanic` 反射 `teleportAsync` | 改用 `SchedulerService.runEntityTask` + 实体调度 |
| `Bukkit.broadcastMessage` | 走全局区域调度 |
| `player.sendTitle` | 走实体调度 |
| `Bukkit.getOnlinePlayers()` 遍历操作 | 区域感知 |
| `Competition.startCountdownTask` 直接 `BukkitRunnable` | 统一走 `SchedulerService` |
| `GameSession extends BukkitRunnable` | 改为普通类，由 `SchedulerService` 调度 |

**调度选择**：
```
全局调度: 不修改区域拥有的世界/实体状态的全局簿记
区域调度: 位置相关的方块/区块/世界修改
实体调度: 活跃实体/玩家修改
异步执行: 仅 IO 和纯计算
```

**验收**：Folia 构建通过，所有平台 API 调用走 `SchedulerService`。

---

### 阶段 9：缓存所有权集中

**目标**：每个缓存有明确所有者和失效点。

| 缓存类型 | 所有者 | 失效点 |
|---------|-------|-------|
| 配置缓存 | `ConfigurationService` | 配置加载/重载 |
| 玩家上下文缓存 | `PlayerContextStore` | 退出/保存/关闭 |
| 会话缓存 | 域服务 | 会话开始/结束 |
| UI 缓存 | `UiRuntime` | 打开/替换/退出/禁用 |
| 派生结果缓存 | 源配置/数据变更时失效 | — |
| 冷却/短命缓存 | `PlayerContext.RuntimeData` | TTL 或所有者关闭 |

**缓存键规则**：
- 玩家键：`UUID`
- 世界/竞技场/资源：稳定 id 或位置 id
- 物品：内部 item id
- 菜单/UI：玩家 UUID + UI id 或活跃视图 id
- 派生文本/布局：所有影响输出的输入

**验收**：无缓存键使用 live `Player`/NMS player/Inventory/Chunk/Entity。

---

### 阶段 10：测试与验收

**单元测试**：
- PlayerContext 生命周期（CREATED→...→CLEANED）
- 队列预约失败回退路径
- 关闭行为（会话/UI/事件 token）
- 作用域事件总线过滤
- 物品触发器链
- UI 替换（打开新菜单前关闭旧的）
- 数据快照保存（不可变性）
- 竞赛会话开始/结束/强制结束

**集成测试（服务器验证）**：
- 钓鱼全流程（抛竿→咬钩→小游戏→捕获→记录→广播→发奖）
- 竞赛全流程（开始→记录→结束→排名→发奖）
- 多玩家并发钓鱼
- 玩家退出时保存
- 玩家重连时加载
- Folia 服务器运行
- 跨版本兼容（1.16.5 / 1.20 / 1.21）

---

## 五、迁移策略

### 5.1 渐进式迁移（非大爆炸）

每个阶段独立可交付，保持主分支可编译可运行。

```
阶段0 (清理) → 阶段1 (基础设施) → 阶段2 (RootService)
   → 阶段3 (PlayerContext) → 阶段4 (EventBus)
   → 阶段5 (拆 God Class, 可并行子任务) 
   → 阶段6 (持久化) → 阶段7 (UI) → 阶段8 (Folia)
   → 阶段9 (缓存) → 阶段10 (测试)
```

### 5.2 兼容桥接（迁移期间）

迁移期间允许临时桥接层，但必须在对应阶段完成时移除：
- `kkfish.getInstance()` → 迁移到 `RootService.getInstance()` 后移除
- 旧 manager 方法 → 包装为新服务调用，迁移完成后删除旧 manager
- static 工具方法 → 实例化为服务，迁移完成后删除 static 入口

### 5.3 回归验证

每个阶段完成后必须验证：
- 编译通过（`mvn clean install -DskipTests`）
- Spigot 构建通过
- Folia 构建通过
- 核心功能手动测试（钓鱼/小游戏/竞赛/出售/GUI）
- 无新增 static 单例
- 无新增 God Class

---

## 六、验收清单（最终）

### 6.1 架构验收
- [ ] `KkfishPlugin` 仅含生命周期委托，无业务逻辑字段
- [ ] 启动/关闭顺序显式且有序
- [ ] 每个注册的 listener/token/task 有关闭路径
- [ ] 每个 UI 对象在替换/退出/会话结束/插件禁用时关闭
- [ ] 每个会话作用域属性在会话退出时移除
- [ ] 命令不包含业务逻辑
- [ ] Listener 轻量且早期过滤或作用域化
- [ ] 持久化数据从快照保存，非 live 对象
- [ ] 异步代码不触碰不安全的 Bukkit/NMS API
- [ ] Folia 区域/实体/全局调度边界正确
- [ ] 热路径不反复反射/排序/扫描/解析配置
- [ ] `plugin.yml` 元数据与实际命令/依赖/权限/API/Folia 支持匹配
- [ ] static 单例在 reload 时有 reset/close 行为

### 6.2 代码质量验收
- [ ] 无单个类超过 600 行
- [ ] 无空文件死代码
- [ ] 无同名跨包混淆
- [ ] 无重复 `import` 语句
- [ ] 无重复 `getMaterialFromType` 实现
- [ ] 无重复 `teleportAsync` 反射代码
- [ ] 无直接 `Particle`/`Material`/`Sound` 枚举使用（走 XSeries）
- [ ] 无直接 `World.spawnParticle`/`World.playSound`/`Material.valueOf`
- [ ] 所有日志走 `MessageService` + `ColorUtils`
- [ ] 所有玩家文本走 `MessageService` + `ColorUtils`

### 6.3 功能验收
- [ ] 钓鱼全流程正常（水/岩浆/虚空）
- [ ] 小游戏全流程正常
- [ ] 竞赛全流程正常（4 种类型）
- [ ] 出售流程正常（命令 + GUI）
- [ ] 所有 GUI 菜单正常（8 个）
- [ ] 多语言正常（中/英）
- [ ] 鱼钩购买/解锁正常
- [ ] 鱼竿合成替换正常
- [ ] AuraSkills 集成正常
- [ ] RealisticSeasons 集成正常
- [ ] Vault 经济集成正常
- [ ] PlayerPoints 集成正常
- [ ] bStats 正常
- [ ] 更新检查正常

### 6.4 跨版本验收
- [ ] Spigot 1.16.5 正常
- [ ] Spigot 1.20 正常
- [ ] Spigot 1.21 正常
- [ ] Folia 1.20 正常
- [ ] Folia 1.21 正常

---

## 七、风险与缓解

| 风险 | 缓解措施 |
|-----|---------|
| 迁移期间功能回归 | 每阶段独立可交付，保留兼容桥接层 |
| PlayerContext 引入新 bug | 充分单元测试生命周期标签转换 |
| EventBus 性能 | 避免热路径反射分发，预编译 handler 列表 |
| Folia 调度边界错误 | 所有平台 API 调用走 SchedulerService，代码审查重点检查 |
| 配置迁移破坏旧存档 | ConfigurationService 提供迁移路径，保留旧键兼容 |
| 反射 Method 缓存线程安全 | 缓存初始化在启动时完成，运行时只读 |
| 玩家数据快照不一致 | 快照复制在主线程完成，异步保存仅读取不可变快照 |

---

## 八、亮点保留（不重构）

以下设计良好，重构时保留：
- `SchedulerProvider` + `SchedulerProviderFactory` + `SchedulerTask` 抽象
- `Competition` 抽象基类 + 4 种策略类型
- `HookMechanic` 接口 + 工厂模式（仅修复 static Map 和反射重复）
- `DB` 缓存 + 自动重连（迁移到 DataSource 时保留）
- `MessageManager` 多语言 + 玩家语言缓存（实例化时保留）
- `GUIMenuLoader` yml 驱动菜单配置

---

## 九、优先级与建议执行顺序

**高优先级（先做）**：
1. 阶段 0：清理死代码 + 修复 XSeries 违规（1-2 天）
2. 阶段 1：提取基础设施服务（3-5 天）
3. 阶段 2：引入 RootService（1-2 天）
4. 阶段 3：引入 PlayerContext（5-7 天）

**中优先级**：
5. 阶段 4：EventBus（3-5 天）
6. 阶段 5：拆分 God Class（7-10 天，可并行）
7. 阶段 8：Folia 加固（3-5 天）

**低优先级（后做）**：
8. 阶段 6：持久化重构（3-5 天）
9. 阶段 7：UI 框架（5-7 天）
10. 阶段 9：缓存集中（2-3 天）
11. 阶段 10：测试补全（持续）

---

**文档版本**：v1.0
**基于技能**：app-style-minecraft-plugin-architecture
**适用项目**：KKFish 1.6.2
