## 1.6.1

**Fix:**
> Fixed hook float task leaks on session end
> Fixed rod charge-speed and float-area-size having no gameplay effect
> Fixed lava and void minigame difficulty multipliers not applied
> Fixed off-hand bait never consumed, bait detection and fish record parsing dependent on lore text
> Fixed minigame fish selection ignoring water type pools
> Fixed non-sell GUI empty slots accepting items via shift-click
> Fixed sell GUI drag events blocked for all GUIs
> Fixed Cmd add command feedback using string concatenation instead of format placeholders
> Fixed i18n formatting characters hardcoded in fish lore and floating text
> Fixed rod template fallback hardcoded Chinese
> Fixed MySQL migration running SQLite-specific queries before dialect check
> Fixed Math.random() scattered across 10 locations in game logic
> Fixed Material enum direct usage violating cross-version rules
> Fixed pools.yml referencing fish not defined in fish.yml
> Fixed config comments incorrect or incomplete (charge-speed, escape)
> Removed dead code: getRankPrefix(), ~75 lines of lore text parsing, duplicate sell deposit, unused config values

**Add:**
> Added ChargeSessionData for per-session rod attribute storage
> Added cross-version NBT read helpers for bait and fish item identification
> Added 7 i18n keys for fish lore formatting and rod template
> Added Folia/Foila compatibility with dedicated kkfish-folia.jar build

**Optimize:**
> Optimized DB migration to use dialect-specific column detection
> Optimized bait detection and fish record parsing from lore iteration to single NBT reads

---

## 1.6.1

**修复：**
> 修复鱼钩浮动任务在会话结束时泄漏的问题
> 修复鱼竿蓄力速度和浮漂范围属性无实际效果的问题
> 修复岩浆和虚空小游戏难度加成不生效的问题
> 修复副手鱼饵不消耗、鱼饵检测和鱼记录解析依赖lore文本的问题
> 修复小游戏选鱼忽略水域池子的问题
> 修复非卖鱼GUI空槽位可被shift-点击塞入物品的问题
> 修复卖鱼GUI拖拽事件被阻止的问题
> 修复Cmd添加命令反馈使用字符串拼接而非格式占位符的问题
> 修复鱼物品lore和浮空文字中国际化格式字符硬编码的问题
> 修复鱼竿模板fallback硬编码中文的问题
> 修复MySQL迁移先执行SQLite特定查询再检查方言的问题
> 修复游戏逻辑中10处Math.random()分散调用的问题
> 修复Material枚举直接使用违反跨版本规则的问题
> 修复pools.yml引用fish.yml中未定义鱼的问题
> 修复配置注释错误或不完整的问题（charge-speed、escape）
> 删除死代码：getRankPrefix()、约75行lore文本解析、重复存款逻辑、未使用的配置值

**新增：**
> 新增ChargeSessionData用于每次蓄力会话的鱼竿属性存储
> 新增跨版本NBT读取辅助方法用于鱼饵和鱼物品识别
> 新增鱼物品lore格式化和鱼竿模板相关的7个国际化键
> 新增Folia/Foila兼容及专用kkfish-folia.jar构建

**优化：**
> 优化数据库迁移使用方言特定的列检测
> 优化鱼饵检测和鱼记录解析从lore遍历改为单次NBT读取
