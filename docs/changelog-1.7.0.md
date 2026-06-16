## 1.7.0

**Refactor:**
> Introduced RootService as composition root with explicit startup/shutdown order
> Introduced PlayerContext system unifying 18 scattered state maps with lifecycle state machine
> Introduced EventBus domain event system decoupling MinigameManager cross-domain calls
> Extracted infrastructure services: VersionService, EconomyService, SeasonsService, NmsAdapter, MaterialResolver
> Split GameSession into FishSelector, FishMovementSimulator, MinigameRenderer, FishValueCalculator
> Split Cmd into SellCommandHandler, GiveCommandHandler, CompeteCommandHandler, ConfigCommandHandler, AdminCommandHandler
> Split Fish into ChargeProgressTracker, HookProjectile, BiteCheckScheduler, FishItemFactory, FishAnimationService, FishBroadcastService
> Split GUI into GUICommons, HookMaterialGUIHandler, FishDexGUIHandler, CompetitionGUIHandler

**Fix:**
> Fixed startup NPE caused by MessageManager not initialized before Config
> Fixed Compete init NPE caused by scheduler not injected before manager construction
> Fixed BukkitRunnable cancel throwing "Not scheduled yet" on scheduler fallback path
> Fixed all log language keys silently falling back to hardcoded defaults
> Fixed hardcoded log messages across the codebase
> Fixed GUI permission check log output in non-debug mode

**Improve:**
> Removed 9 empty dead code files causing cross-package name confusion
> Removed ~1000 lines of dead code from GUI
> Fixed XSeries rule violations in EntityBatchProcessor
> Cleaned duplicate imports

---

## 1.7.0

**重构：**
> 引入 RootService 组合根，明确启动/关闭顺序
> 引入 PlayerContext 系统，统一 18 个分散的状态 Map，带生命周期状态机
> 引入 EventBus 域事件系统，解耦小游戏管理器的跨域调用
> 提取基础设施服务：版本检测、经济门面、季节服务、NMS 适配、材质解析
> 拆分小游戏会话为鱼类选择器、鱼类移动模拟器、UI 渲染器、价值计算器
> 拆分命令管理器为出售、给予、竞赛、配置、管理员 5 个子处理器
> 拆分钓鱼管理器为蓄力追踪、鱼钩抛射、咬钩调度、鱼物品工厂、动画服务、广播服务 6 个组件
> 拆分 GUI 管理器为通用工具、鱼钩材质、鱼类图鉴、比赛界面 4 个处理器

**修复：**
> 修复启动时 MessageManager 未在 Config 之前初始化导致的 NPE
> 修复 Compete 初始化时调度器未注入导致的 NPE
> 修复调度降级路径下任务取消抛出 "Not scheduled yet"
> 修复所有日志语言键静默回退到硬编码默认值
> 修复大量硬编码日志内容
> 修复非 debug 模式下 GUI 权限检查日志仍然输出

**优化：**
> 删除 9 个空死代码文件，消除跨包同名混淆
> 删除 GUI 中约 1000 行死代码
> 修复 EntityBatchProcessor 的 XSeries 规则违规
> 清理重复 import
