package me.kkfish.managers;

import me.kkfish.ConfigFixture;
import me.kkfish.misc.minigame.MinigameManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RodIdentityTest {
    private static class Rod extends ItemStack {
        private final ItemMeta meta;
        Rod(String name) {
            super(Material.FISHING_ROD);
            meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class[]{ItemMeta.class},
                    (proxy, method, args) -> method.getName().equals("getDisplayName") ? name : method.getReturnType() == boolean.class ? false : null);
        }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean hasItemMeta() { return false; }
    }

    @Test
    void gameplayUsesConfiguredRodBeforeVanillaFallback() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.loadPack("en_us");
        java.lang.reflect.Field checked = me.kkfish.integrations.CustomItemHook.class.getDeclaredField("iaChecked");
        checked.setAccessible(true);
        boolean previous = checked.getBoolean(null);
        checked.setBoolean(null, true);
        ItemStack rod = new Rod("§5§lMaster Fishing Rod");
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class[]{PlayerInventory.class},
                (proxy, method, args) -> method.getName().equals("getItemInMainHand") ? rod : null);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> method.getName().equals("getInventory") ? inventory : method.getName().equals("getName") ? "test" : null);
        try {
            assertEquals("master-fishing-rod", new MinigameManager(fixture.plugin).getRodNameByPlayer(player));
            assertEquals(1.7, fixture.config.getRodChargeSpeed(fixture.config.getRodType(rod)));
            assertEquals(5, fixture.config.getRodFloatAreaSize(fixture.config.getRodType(rod)));
        } finally {
            checked.setBoolean(null, previous);
        }
    }
}
