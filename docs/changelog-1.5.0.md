## 1.5.0

**Add:**
> Added lava and void fishing with strategy pattern, including new config sections `lava-fishing.*`, `void-fishing.*`, `styles.*` and independent `pools.yml`
> Added auto-complete feature for missing config defaults (broadcast, fish-animation, pools)

**Fix:**
> Fixed double event registration causing every Fishing handler to fire twice
> Fixed `checkRodDurability` always returned true (broken rods could still start charging)
> Fixed fragile string comparison `Action.name().contains("RIGHT_CLICK")` replaced with enum equality
> Fixed rod durability precision loss by storing remaining durability in PDC
> Fixed `levelName.contains("")` always true causing incorrect rarity display in broadcasts
> Fixed duplicate 16-entry block blacklist extracted to shared method
> Fixed EntityBatchProcessor never flushed (added 20-tick timer)
> Fixed empty catch blocks (particle spawn failures now logged)
> Fixed entity right-click swallowed mini-game green bar clicks — added `PlayerInteractEntityEvent` handler
> Removed unused `PlayerChatEvent` import

**Change:**
> Removed dead config nodes (13 entries), 6 unimplemented sound sections, 3 dead getters
> Aligned 14 code defaults with config.yml (trigger-mode, seasonal.enabled, increase-speed, etc.)
