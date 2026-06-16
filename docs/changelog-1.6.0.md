## 1.6.0

**Fix:**
> Fixed fish item lore showing raw rarity level instead of configured display name
> Fixed 100% charge being treated as overcharge
> Fixed hook floating effect task never terminating
> Fixed hook trajectory task not tracked, unable to cancel on session end
> Fixed Particle API incompatibility on 1.21+
> Fixed dangerZone array out of bounds in minigame
> Fixed severe performance issue: rod name parsed every tick in minigame
> Fixed duplicate session check missing in 2/3-argument startMinigame
> Fixed OP privilege escalation without try-finally in GUIAction
> Fixed fish selling losing items when player inventory is full
> Fixed ClassCastException from database Number types in GUI stats
> Fixed MySQL incompatibility: INSERT OR REPLACE and AUTOINCREMENT syntax
> Fixed getConnection() returning null causing NullPointerException (19 call sites)
> Fixed sellAllFish not collecting fish UUIDs for database cleanup
> Fixed clearAllCache clearing all players' cache on any player quit
> Fixed isVoidLocation always returning false, void fishing completely broken
> Fixed cleanupAndAnnounce callable twice, causing duplicate rewards
> Fixed PointsOnlyCompetition giving 1.0 points for fish not in fishList
> Fixed reflection method lookup every tick
> Fixed cross-world distance() call throwing IllegalArgumentException
> Fixed fishLevel.split NPE when fishLevel is null
> Fixed hookMaterial.toLowerCase NPE when database returns null
> Fixed isHookPassableBlock using hardcoded strings instead of Set lookup
> Fixed shared StringBuilder/Vector/Location instance fields causing thread safety issues
> Fixed new Random() created frequently instead of reusing instance field
> Fixed charge tick sound never playing due to millisecond precision mismatch
> Fixed checkAndConsumeBait setting null instead of AIR for empty offhand
> Fixed fishSize/maxSize division by zero when maxSize is 0
> Fixed rare fish getting double bonus
> Fixed invincibility period only increasing progress, never decreasing
> Fixed minigame run() without exception protection, leaving zombie sessions
> Fixed displayGameUI dead code with identical if-else branches
> Fixed player offline not handled during minigame, causing exceptions
> Fixed getRodNameByPlayer returning null instead of default "wood"
> Fixed duplicate actualFishValue declaration in inner class
> Fixed HashMap concurrency issues in GUI (6 maps changed to ConcurrentHashMap)
> Fixed GUIType.valueOf() throwing uncaught IllegalArgumentException
> Fixed direct Material enum usage in GUI violating XSeries rules (14 places)
> Fixed itemsPerPage inconsistency between GUI methods
> Fixed sellFishItem using unnecessary reflection for PDC access
> Fixed sellFishItem returning 1 for item rewards without depositing Vault value
> Fixed displayNameToHookNameMap not updating on config reload
> Fixed e.printStackTrace() violating logging rules
> Fixed onInventoryClose clearing search keywords for all GUI types
> Fixed SlotMapping getBorderSlotCount wrong calculation for 27-slot GUI
> Fixed RewardPreview isItemDisplaySlot range too small
> Fixed GUI_SIZE_6 = 6 not being a valid inventory size
> Fixed SQL injection risk from columnName concatenation
> Fixed logFishing/updatePlayerStats using connection field instead of getConnection()
> Fixed cache returning mutable list reference allowing external pollution
> Fixed cache cleanup running every write instead of every 100 writes
> Fixed updatePlayerStats using two database round trips instead of one
> Fixed getConfigurationSection() NPE in Config (5 places)
> Fixed non-debug AuraSkills log output in production
> Fixed Double.parseDouble without exception handling
> Fixed isDebugMode/setDebugMode config path inconsistency
> Fixed duplicated fish filtering logic in getAvailableFish/getAvailableFishFromPool
> Fixed Material.FISHING_ROD direct enum usage in Config
> Fixed parseMaterial returning null without warning in ItemValue
> Fixed Y-axis movement not restricted during fishing
> Fixed durability calculation overflow when maxSingleLoss < baseLoss
> Fixed getRodCurrentDurability missing rodType null check
> Fixed hook Y velocity unlimited descent in water mechanic
> Fixed lava mechanic missing Y<=0 boundary check
> Fixed lava/void hook messages using wrong language key
> Fixed hasEquipmentEffect based on Lore text matching, exploitable by players
> Fixed Compete shared mutable StringBuilder/ArrayList fields
> Fixed Compete containsKey+put TOCTOU race condition
> Fixed scheduleRecurring array index out of bounds on malformed config
> Fixed competition notification tasks not tracked, can't be cancelled
> Fixed scheduledTasks memory leak
> Fixed CompetitionData non-atomic operations on counters
> Fixed AmountCompetition/SingleValueCompetition/TotalValueCompetition not checking fishList filter
> Fixed PointsOnlyCompetition value double-counting in point calculation
> Fixed BarColor/BarStyle parse errors silently swallowed without warning
> Fixed hardcoded Chinese string in Cmd instead of MessageManager
> Fixed getFishValueFromItem querying database every call without cache
> Fixed getItemValue() null check missing in Cmd
> Fixed sellAllFish item rewards given multiple times for stacked items
> Fixed setFishUUIDString using unnecessary reflection for PDC
> Fixed Material.matchMaterial() in Cmd violating XSeries rules
> Removed dead code: addGlowEffect empty method, guiCache, ensureFishAnimationSubConfigs, materialColor variable, empty if-block, empty createFishItem(), redundant initializeItemValueManager wrapper

**Add:**
> Added fishList filter check to AmountCompetition, SingleValueCompetition, TotalValueCompetition
> Added clearPlayerCache method to DB for single-player cache clearing
> Added hook_in_lava and hook_in_void language keys
> Added competition_invalid_duration language key
> Added CompetitionUtils.formatDuration shared utility
> Added columnName whitelist validation in DB
> Added fishValueCache in Cmd for database query optimization

---

## 1.6.0

**修复：**
> 修复鱼物品lore显示原始稀有度等级而非配置名称
> 修复100%蓄力被当作过度蓄力
> 修复鱼钩浮动效果任务永不终止
> 修复鱼钩飞行轨迹任务未追踪，会话结束时无法取消
> 修复粒子API在1.21+版本不兼容
> 修复迷你游戏中dangerZone数组越界
> 修复迷你游戏每tick解析物品lore的严重性能问题
> 修复两参数/三参数startMinigame缺少重复会话检查
> 修复GUIAction中OP提权无try-finally保护
> 修复卖鱼时背包满导致物品丢失
> 修复GUI统计中数据库Number类型强转ClassCastException
> 修复MySQL不兼容：INSERT OR REPLACE和AUTOINCREMENT语法
> 修复getConnection()返回null导致NullPointerException（19处调用点）
> 修复sellAllFish未收集鱼UUID导致数据库永不清理
> 修复任何玩家退出时清除所有玩家缓存
> 修复isVoidLocation永远返回false，虚空钓鱼完全失效
> 修复cleanupAndAnnounce可重复调用导致奖励重复发放
> 修复PointsOnlyCompetition对不在fishList的鱼默认给1.0积分
> 修复反射方法每tick重复查找
> 修复跨世界distance()调用抛出IllegalArgumentException
> 修复fishLevel.split空指针异常
> 修复hookMaterial.toLowerCase空指针异常
> 修复isHookPassableBlock使用硬编码字符串而非Set查找
> 修复共享StringBuilder/Vector/Location实例字段的线程安全问题
> 修复频繁创建new Random()而非复用实例字段
> 修复蓄力滴答音效因毫秒精度问题永不播放
> 修复checkAndConsumeBait设置null而非AIR导致空副手
> 修复fishSize/maxSize除零错误
> 修复稀有鱼双重加成
> 修复无敌期间进度只增不减
> 修复迷你游戏run()无异常保护导致僵尸会话
> 修复displayGameUI中if-else完全相同的死代码
> 修复迷你游戏中玩家离线未处理导致异常
> 修复getRodNameByPlayer返回null而非默认值"wood"
> 修复内部类中重复声明actualFishValue
> 修复GUI中HashMap并发问题（6个Map改为ConcurrentHashMap）
> 修复GUIType.valueOf()抛出未捕获的IllegalArgumentException
> 修复GUI中直接使用Material枚举违反XSeries规则（14处）
> 修复GUI分页itemsPerPage不一致
> 修复sellFishItem使用不必要的反射访问PDC
> 修复sellFishItem有物品奖励时未存入Vault价值
> 修复displayNameToHookNameMap在配置重载后不更新
> 修复e.printStackTrace()违反日志规范
> 修复onInventoryClose对所有GUI类型清除搜索关键词
> 修复SlotMapping getBorderSlotCount对27槽位GUI计算错误
> 修复RewardPreview isItemDisplaySlot范围过小
> 修复GUI_SIZE_6 = 6不是有效的物品栏大小
> 修复columnName拼接导致的SQL注入风险
> 修复logFishing/updatePlayerStats直接使用connection字段
> 修复缓存返回可变列表引用导致外部可污染
> 修复缓存清理每次写入都执行而非每100次
> 修复updatePlayerStats使用两次数据库往返
> 修复Config中getConfigurationSection()空指针异常（5处）
> 修复生产环境下AuraSkills调试日志输出
> 修复Double.parseDouble无异常处理
> 修复isDebugMode/setDebugMode配置路径不一致
> 修复getAvailableFish/getAvailableFishFromPool逻辑重复
> 修复Config中直接使用Material.FISHING_ROD枚举
> 修复ItemValue中parseMaterial返回null时无警告日志
> 修复钓鱼时Y轴移动未限制
> 修复耐久度计算溢出
> 修复getRodCurrentDurability缺少rodType空检查
> 修复水中鱼钩Y速度无限下沉
> 修复岩浆鱼钩缺少Y<=0边界检查
> 修复岩浆/虚空鱼钩消息使用了错误的语言键
> 修复hasEquipmentEffect基于Lore文本匹配可被玩家伪造
> 修复Compete共享可变StringBuilder/ArrayList字段
> 修复Compete中containsKey+put的TOCTOU竞态
> 修复scheduleRecurring配置格式错误时数组越界
> 修复比赛通知任务未追踪无法取消
> 修复scheduledTasks内存泄漏
> 修复CompetitionData计数器非原子操作
> 修复AmountCompetition/SingleValueCompetition/TotalValueCompetition不检查fishList过滤
> 修复PointsOnlyCompetition积分计算中value二次叠加
> 修复BarColor/BarStyle解析异常被静默吞没
> 修复Cmd中硬编码中文字符串
> 修复getFishValueFromItem每次调用都查数据库无缓存
> 修复Cmd中getItemValue()空检查缺失
> 修复sellAllFish物品奖励对堆叠物品重复发放
> 修复setFishUUIDString使用不必要的反射访问PDC
> 修复Cmd中使用Material.matchMaterial()违反XSeries规则
> 删除死代码：addGlowEffect空方法、guiCache、ensureFishAnimationSubConfigs、materialColor变量、空if块、空的createFishItem()、冗余initializeItemValueManager包装方法

**新增：**
> 新增AmountCompetition、SingleValueCompetition、TotalValueCompetition的fishList过滤检查
> 新增DB中clearPlayerCache单玩家缓存清除方法
> 新增hook_in_lava和hook_in_void语言键
> 新增competition_invalid_duration语言键
> 新增CompetitionUtils.formatDuration共享工具方法
> 新增DB中columnName白名单校验
> 新增Cmd中fishValueCache数据库查询优化缓存
