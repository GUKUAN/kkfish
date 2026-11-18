package me.kkfish.misc.minigame;

import me.kkfish.ConfigFixture;
import me.kkfish.kkfish;
import me.kkfish.fishing.WaterType;
import me.kkfish.managers.Fish;
import me.kkfish.misc.MessageManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmptyPoolTest {
    public static class FishingState extends Fish {
        boolean ended;
        public FishingState(kkfish plugin) { super(plugin); }
        @Override public Object getActiveSession(Player player) { return null; }
        @Override public WaterType getWaterType(Player player) { return WaterType.WATER; }
        @Override public void endSession(Player player) { ended = true; }
    }

    @Test
    void noCandidatesClosesTheFishingSessionWithoutStartingAMinigame() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getPoolConfig().set("water", Collections.emptyList());
        fixture.config.getFishConfig().set("fish.outside.value", 10);
        MessageManager messages = ConfigFixture.allocate(MessageManager.class);
        ConfigFixture.set(messages, "msgMap", new HashMap<>());
        ConfigFixture.set(messages, "prefix", "");
        ConfigFixture.set(messages, "currLang", "en_us");
        ConfigFixture.set(fixture.plugin, "messageManager", messages);
        FishingState state = ConfigFixture.allocate(FishingState.class);
        ConfigFixture.set(fixture.plugin, "fish", state);
        Block block = (Block) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Block.class},
                (proxy, method, args) -> method.getName().equals("getBiome") ? Biome.RIVER : null);
        World world = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{World.class}, (proxy, method, args) -> {
            if (method.getName().equals("getBlockAt")) return block;
            if (method.getName().equals("getTime")) return 0L;
            return method.getReturnType() == boolean.class ? false : null;
        });
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerInventory.class},
                (proxy, method, args) -> new ItemStack(Material.AIR));
        List<String> feedback = new ArrayList<>();
        UUID id = UUID.randomUUID();
        ConfigFixture.set(messages, "playerLangCache", java.util.Collections.singletonMap(id, "en_us"));
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getUniqueId": return id;
                case "getName": return "test";
                case "getInventory": return inventory;
                case "getLocation":
                case "getEyeLocation": return new Location(world, 0, 64, 0);
                case "sendMessage": feedback.add((String) args[0]); return null;
                default: return null;
            }
        });
        MinigameManager manager = new MinigameManager(fixture.plugin);
        manager.startMinigame(player, 95);
        assertTrue(state.ended);
        assertFalse(manager.isPlayerInGame(player));
        assertEquals(1, feedback.size());
    }
}
