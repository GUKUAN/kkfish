## 1.8.4

**feat:**
> Added `lure-speed` to rods, hooks (`effects.lure-speed`) and bait (`lure`). The bonuses stack and shorten bite delay by `1 / (1 + bonus)`; the default minimum delay is 20 ticks
> Added `size-multiplier` to rods. Bait, rod and hook multipliers combine, up to each fish's `max-size`
> Rod, hook and bait descriptions now show lure speed and size bonuses when effective; both bundled config packs include the new settings and messages
> Fish `activity` now controls behavior and dash frequency in the minigame

**fix:**
> Fixed custom rods using default-rod settings during fishing
> Rods now inherit missing settings from `rods.default`; explicit values, including zero and empty lists, are kept
> Fixed rod identification for crafted rods and legacy items
> Fixed charge progress and cast power using different time limits
> Fixed fish level lists ignoring entries after the first
> Fixed fish levels falling back to common, affecting fish selection and minigame difficulty; zero-weight levels stay excluded
> Rare-fish bonuses now adjust eligible rarity weights instead of forcing a fixed 5% upgrade
> Fixed localized and custom rarity IDs in rarity parsing, AuraSkills XP and fish values
> Fixed minigames ignoring fish biome, weather, time and season restrictions
> Configured pools no longer select fish outside the pool when no fish match the current conditions
> Fixed season matching and thunderstorm detection; empty pools now end the fishing session with a message
> Fixed legacy bait values overriding or duplicating newer effect settings
> Bait recognition no longer depends on usage-tip text
> Fish items now retain their config ID, level, size and value; fixed renamed fish losing saturation and item rewards, and catch records relying on lore text
> Fixed hook size bonuses being applied after minigame calculations; item size, XP and catch records now use the same final size
> Hook size and value multipliers can be configured
> Fixed flat hook config paths and paged hook template lookups
> Fixed vanilla-mode catches ignoring the vanilla XP setting; AuraSkills XP is handled separately
> Successful catches now award XP and are recorded even when the inventory is full
> Fixed config completion replacing existing scalar and list values
> Fixed legacy timezone values being overwritten during config upgrades
> Fixed empty GUI actions re-enabling old commands
> Fixed bStats reloads continuing to use stale settings
> Fixed minigame startup clearing hook data before reading the water type and location
> Failed minigame starts now clean up the hook session
> Fixed language upgrades missing new messages; custom messages are kept
> `%charge_speed%` now returns a numeric multiplier after crafting and durability updates
> Fixed the minigame green bar disappearing at the edges and changing width near boundaries
> Fixed the fish movement range stopping at 90% of the bar; fish can now reach both ends and turn at the edges
> Fixed the fish marker hit test using one cell when the marker is three cells wide; progress now rises when any visible part touches the green bar, including at the edges
> Fixed the initial minigame grace period still reducing progress

**Upgrade notes:**
> Existing configs are supported. Replace the jar and restart the server
> `float-area-size` is a percentage from 0 to 100; rods inherit omitted fields from `rods.default`
> Pool restrictions and level weights now affect catches, so fish distributions may change
> Old fish items without a saved config ID are matched by display name
> `bite-rate-multiplier` has been removed from bundled fish configs and new-fish defaults; existing values are ignored
> Rod `enchantability` is not wired into enchantment behavior

---

## 1.8.4

**新增：**
> 鱼竿新增 `lure-speed`，鱼钩新增 `effects.lure-speed`，鱼饵新增 `lure` 效果；三者加算后按 `1 / (1 + 加成)` 缩短咬钩延迟，默认最小延迟为 20 tick
> 鱼竿新增尺寸倍率；鱼饵、鱼竿和鱼钩倍率相乘，最终尺寸不超过鱼类的 `max-size`
> 鱼竿、鱼钩和鱼饵的描述会显示实际生效的诱鱼速度与尺寸加成；中英文配置包同步加入相关配置和提示
> 鱼类 `activity` 现在控制小游戏中的行为切换和冲刺频率

**修复：**
> 修复了自定义鱼竿钓鱼时错误使用默认竿配置的问题
> 鱼竿缺失的配置现在继承 `rods.default`；明确填写的值（包括零值和空列表）会保留
> 修复了合成鱼竿和旧物品的身份识别问题
> 修复了充能进度与抛竿力度使用不同时间上限的问题
> 修复了鱼类等级列表只读取第一项的问题
> 修复了鱼类等级回退为普通、影响鱼种选择和小游戏难度的问题；零权重等级不会被抽中
> 稀有鱼加成现在调整符合条件的等级权重，不再固定以 5% 概率强制升级
> 修复了本地化和自定义稀有度 ID 在解析、AuraSkills 经验和鱼价值计算中的问题
> 修复了小游戏忽略鱼类群系、天气、时间和季节限制的问题
> 鱼池没有符合条件的鱼时，不再从池外抽取
> 修复了季节匹配和雷暴天气识别；空鱼池会提示并结束本次钓鱼
> 修复了旧鱼饵数值覆盖或重复叠加新效果的问题
> 鱼饵识别不再依赖使用提示文字
> 鱼物品现在保存配置 ID、等级、尺寸和价值；修复了改名鱼的饱食度、物品奖励读取失败，以及钓鱼记录依赖描述文字的问题
> 修复了鱼钩尺寸加成在小游戏结算后才应用的问题；物品尺寸、经验和捕获记录现在使用相同的最终尺寸
> 鱼钩尺寸和价值倍率现在可以配置
> 修复了扁平鱼钩配置路径和分页模板读取问题
> 修复了原版模式捕获忽略原版经验开关的问题；AuraSkills 经验独立处理
> 背包满时，成功捕获仍会发放经验并记录
> 修复了配置补齐覆盖已有标量和列表的问题
> 修复了配置升级时旧时区值被覆盖的问题
> 修复了 GUI 空动作重新启用旧命令的问题
> 修复了 bStats 重载后仍使用旧设置的问题
> 修复了小游戏启动前提前清理鱼钩数据的问题
> 小游戏启动失败时会清理鱼钩会话
> 修复了语言文件升级时未补充新提示的问题，同时保留自定义消息
> `%charge_speed%` 现在在合成和耐久更新后也只返回数值倍率
> 修复了小游戏绿条在边界消失及边缘宽度变化的问题
> 修复了鱼的移动范围只到进度条 90% 的问题；鱼现在可以游至两端并在边缘掉头
> 修复了三格宽鱼指示符仍按一格判定的问题；鱼块任意部分碰到绿条（包括边缘）都会增加进度
> 修复了小游戏初始保护期仍会扣除进度的问题

**升级说明：**
> 兼容已有配置，替换插件并重启服务器即可
> `float-area-size` 使用 0–100 的百分比；鱼竿省略的配置会继承 `rods.default`
> 鱼池限制和等级权重现在会影响捕获结果，鱼类分布可能变化
> 没有保存配置 ID 的旧鱼物品会按显示名匹配
> 内置鱼类配置和新建鱼类默认项已移除 `bite-rate-multiplier`；已有配置中的该值不再读取
> 鱼竿 `enchantability` 尚未接入附魔逻辑
