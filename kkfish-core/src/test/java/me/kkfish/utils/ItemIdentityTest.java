package me.kkfish.utils;

import me.kkfish.ItemFixture;
import me.kkfish.ConfigFixture;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemIdentityTest {
    @Test
    void tagsAndTypedMetadataRoundTrip() {
        ItemFixture rod = new ItemFixture(Material.FISHING_ROD, "竿");
        assertTrue(NBTUtil.addTags(rod, java.util.Arrays.asList("自定义鱼竿")));
        assertTrue(NBTUtil.hasTag(rod, "自定义鱼竿"));
        assertTrue(NBTUtil.setNBTData(rod, "fish_size", 37.5));
        assertEquals(37.5, NBTUtil.getNBTData(rod, "fish_size"));
    }

    @Test
    void fishIdSurvivesRenaming() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getFishConfig().set("fish.Carp.display-name", "§f鲤鱼");
        ItemFixture fish = new ItemFixture(Material.COD, "玩家改过的名字");
        assertTrue(NBTUtil.setNBTData(fish, "fish_name", "Carp"));
        assertEquals("Carp", fixture.config.getItemConfigId(fish, "fish", "fish_name"));
    }

    @Test
    void persistedRodIdWinsOverTheDefaultDisplayName() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getRodConfig().set("rods.custom.charge-speed", 2.0);
        ItemFixture rod = new ItemFixture(Material.STICK, "默认竿显示名");
        assertTrue(NBTUtil.setNBTData(rod, "kkfish_rod", "custom"));
        assertEquals("custom", fixture.config.getRodType(rod));
    }

    @Test
    void persistedOffhandRodIsSelectedWhenMainHandIsNotARod() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getRodConfig().set("rods.custom.material", "STICK");
        ItemFixture rod = new ItemFixture(Material.STICK, "改名竿");
        NBTUtil.setNBTData(rod, "kkfish_rod", "custom");
        org.bukkit.inventory.PlayerInventory inventory = (org.bukkit.inventory.PlayerInventory) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{org.bukkit.inventory.PlayerInventory.class},
                (proxy, method, args) -> method.getName().equals("getItemInMainHand") ? new org.bukkit.inventory.ItemStack(Material.AIR) : rod);
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> inventory);
        assertSame(rod, fixture.config.getPlayerRod(player));
    }
}
