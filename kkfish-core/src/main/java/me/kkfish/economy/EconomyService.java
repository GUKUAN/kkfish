package me.kkfish.economy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 统一经济服务门面，封装 Vault 和 PlayerPoints。
 *
 * <p>将 kkfish.java 中分散的 setupEconomy()、setupPlayerPoints() 逻辑收敛到此处，
 * 提供单一入口查询经济可用性、存款、取款、查询余额、点数操作。</p>
 */
public class EconomyService {

    private final kkfish plugin;
    private Economy economy;
    private PlayerPointsAPI playerPointsAPI;

    public EconomyService(kkfish plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 Vault 经济和 PlayerPoints 点数系统。
     */
    public void initialize() {
        setupEconomy();
        setupPlayerPoints();
    }

    private void setupEconomy() {
        MessageManager mm = plugin.getMessageManager();
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            kkfish.log(mm.getMessageWithoutPrefix("log.no_economy",
                    "Vault or economy plugin not found! Economy features will be unavailable."));
            economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            kkfish.log(mm.getMessageWithoutPrefix("log.no_economy",
                    "Vault or economy plugin not found! Economy features will be unavailable."));
            economy = null;
            return;
        }

        economy = rsp.getProvider();
        if (economy != null) {
            kkfish.log(mm.getMessageWithoutPrefix("log.economy_success",
                    "Successfully connected to economy system~"));
        } else {
            kkfish.log(mm.getMessageWithoutPrefix("log.no_economy",
                    "Vault or economy plugin not found! Economy features will be unavailable."));
        }
    }

    private void setupPlayerPoints() {
        MessageManager mm = plugin.getMessageManager();
        Plugin playerPointsPlugin = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPointsPlugin == null) {
            kkfish.log(mm.getMessageWithoutPrefix("log.player_points_not_found",
                    "PlayerPoints plugin not found, point purchase features will be unavailable."));
            playerPointsAPI = null;
            return;
        }

        try {
            Method getAPIMethod = playerPointsPlugin.getClass().getMethod("getAPI");
            if (Modifier.isStatic(getAPIMethod.getModifiers())) {
                playerPointsAPI = (PlayerPointsAPI) getAPIMethod.invoke(null);
            } else {
                playerPointsAPI = (PlayerPointsAPI) getAPIMethod.invoke(playerPointsPlugin);
            }
            kkfish.log(mm.getMessageWithoutPrefix("log.player_points_success",
                    "Successfully connected to PlayerPoints system~"));
        } catch (Exception e) {
            kkfish.log("§e" + mm.getMessageWithoutPrefix("log.player_points_failed",
                    "Failed to get PlayerPoints API: %s", e.getMessage()));
            e.printStackTrace();
            playerPointsAPI = null;
        }
    }

    /**
     * @return Vault 经济是否可用
     */
    public boolean isEconomyEnabled() {
        return economy != null;
    }

    /**
     * @return PlayerPoints 是否可用
     */
    public boolean isPlayerPointsEnabled() {
        return playerPointsAPI != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlayerPointsAPI getPlayerPointsAPI() {
        return playerPointsAPI;
    }

    /**
     * 给玩家存款。
     *
     * @param player 目标玩家
     * @param amount 金额
     * @return 是否成功
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * 从玩家取款。
     *
     * @param player 目标玩家
     * @param amount 金额
     * @return 是否成功
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * 查询玩家余额。
     *
     * @param player 目标玩家
     * @return 余额，经济不可用时返回 0
     */
    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    /**
     * 给玩家加点数。
     *
     * @param playerId 玩家 UUID
     * @param amount   点数
     * @return 是否成功
     */
    public boolean givePoints(java.util.UUID playerId, int amount) {
        if (playerPointsAPI == null) return false;
        return playerPointsAPI.give(playerId, amount);
    }

    /**
     * 查询玩家点数。
     *
     * @param playerId 玩家 UUID
     * @return 点数，不可用时返回 0
     */
    public int getPoints(java.util.UUID playerId) {
        if (playerPointsAPI == null) return 0;
        return playerPointsAPI.look(playerId);
    }
}
