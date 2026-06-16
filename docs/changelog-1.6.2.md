## 1.6.2

**Fix:**
> Fixed all caught fish having value 1: GameSession used inconsistent default min/max-size (1/3 vs 20/60) causing sizeMultiplier to be ~0.033, making every value round to 1
> Fixed fish item display name showing config key ID instead of display-name field
> Fixed broadcast message showing config key ID instead of fish display-name
> Fixed AuraSkills XP message format error (IllegalFormatConversionException: f != java.lang.String)
> Fixed pools.yml referencing non-existent fish silently falling back to defaults without warning

**Improve:**
> Pool fish validation now runs once during config load with cached results, logging warnings for any fish in pools.yml missing from fish.yml

---

## 1.6.2

**修复：**
> 修复所有钓到的鱼价值都是1的问题：GameSession中min-size/max-size默认值不一致（1/3对比20/60），导致sizeMultiplier约为0.033，所有价值四舍五入后为1
> 修复鱼物品显示名称使用配置ID而非display-name字段的问题
> 修复钓鱼广播消息显示配置ID而非鱼类display-name的问题
> 修复AuraSkills经验值消息格式化错误（IllegalFormatConversionException: f != java.lang.String）
> 修复pools.yml引用不存在的鱼类时静默降级为默认值而无任何警告

**优化：**
> 鱼池校验改为配置加载时一次性执行并缓存结果，对pools.yml中在fish.yml里不存在的鱼类输出警告日志
