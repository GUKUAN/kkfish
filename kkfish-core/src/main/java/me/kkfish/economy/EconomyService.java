package me.kkfish.economy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

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

    public enum RewardType {
        VAULT,
        PLAYER_POINTS,
        NONE
    }

    public static class SellPay {
        private final int vaultAmount;
        private final int pointsAmount;

        public SellPay(int vaultAmount, int pointsAmount) {
            this.vaultAmount = Math.max(0, vaultAmount);
            this.pointsAmount = Math.max(0, pointsAmount);
        }

        public int getVaultAmount() {
            return vaultAmount;
        }

        public int getPointsAmount() {
            return pointsAmount;
        }

        public boolean hasAny() {
            return vaultAmount > 0 || pointsAmount > 0;
        }

        public boolean hasBoth() {
            return vaultAmount > 0 && pointsAmount > 0;
        }

        public int getTotalAmount() {
            return vaultAmount + pointsAmount;
        }

        public SellPay add(SellPay other) {
            if (other == null) return this;
            return new SellPay(vaultAmount + other.vaultAmount, pointsAmount + other.pointsAmount);
        }

        public SellPay multiply(int amount) {
            if (amount <= 0) return new SellPay(0, 0);
            return new SellPay(vaultAmount * amount, pointsAmount * amount);
        }
    }

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
     * 按配置和插件可用性选择出售奖励落点。
     *
     * @param economyEnabled 经济总开关
     * @param vaultEnabled   Vault 开关
     * @param vaultReady     Vault 服务是否已连接
     * @param pointsReady    PlayerPoints API 是否已连接
     * @return 本次应该使用的经济类型
     */
    public static RewardType chooseRewardType(boolean economyEnabled, boolean vaultEnabled, boolean vaultReady, boolean pointsReady) {
        if (!economyEnabled) {
            return RewardType.NONE;
        }
        if (vaultEnabled && vaultReady) {
            return RewardType.VAULT;
        }
        if (pointsReady) {
            return RewardType.PLAYER_POINTS;
        }
        return RewardType.NONE;
    }

    public static RewardType chooseRewardType(boolean economyEnabled, boolean vaultEnabled, boolean pointsEnabled,
                                              boolean vaultReady, boolean pointsReady, String primary, boolean fallback) {
        if (!economyEnabled) {
            return RewardType.NONE;
        }

        boolean canVault = vaultEnabled && vaultReady;
        boolean canPoints = pointsEnabled && pointsReady;
        boolean primaryPoints = isPlayerPointsName(primary);

        if (primaryPoints) {
            if (canPoints) return RewardType.PLAYER_POINTS;
            if (fallback && canVault) return RewardType.VAULT;
            return RewardType.NONE;
        }

        if (canVault) return RewardType.VAULT;
        if (fallback && canPoints) return RewardType.PLAYER_POINTS;
        return RewardType.NONE;
    }

    public static SellPay resolveSellPay(SellValue value, String primary, boolean fallback, boolean economyEnabled,
                                         boolean vaultEnabled, boolean pointsEnabled, boolean vaultReady, boolean pointsReady) {
        if (value == null || !economyEnabled) {
            return new SellPay(0, 0);
        }

        boolean canVault = vaultEnabled && vaultReady;
        boolean canPoints = pointsEnabled && pointsReady;

        if (value.hasSplitValue()) {
            int vaultAmount = value.getVaultValue() > 0 && canVault ? value.getVaultValue() : 0;
            int pointsAmount = value.getPointsValue() > 0 && canPoints ? value.getPointsValue() : 0;
            return new SellPay(vaultAmount, pointsAmount);
        }

        int oldValue = value.getOldValue();
        if (oldValue <= 0) {
            return new SellPay(0, 0);
        }

        boolean primaryPoints = isPlayerPointsName(primary);

        if (primaryPoints) {
            if (canPoints) return new SellPay(0, oldValue);
            if (fallback && canVault) return new SellPay(oldValue, 0);
            return new SellPay(0, 0);
        }

        if (canVault) return new SellPay(oldValue, 0);
        if (fallback && canPoints) return new SellPay(0, oldValue);
        return new SellPay(0, 0);
    }

    public RewardType getRewardType() {
        return chooseRewardType(
                plugin.getCustomConfig().isEconomyEnabled(),
                plugin.getCustomConfig().isEconomySystemEnabled(),
                plugin.getCustomConfig().isPlayerPointsEconomyEnabled(),
                economy != null,
                playerPointsAPI != null,
                plugin.getCustomConfig().getPrimaryEconomy(),
                plugin.getCustomConfig().isEconomyFallbackEnabled());
    }

    /**
     * @return Vault 或 PlayerPoints 其中之一是否可用
     */
    public boolean isEconomyEnabled() {
        if (!plugin.getCustomConfig().isEconomyEnabled()) {
            return false;
        }
        return (plugin.getCustomConfig().isEconomySystemEnabled() && economy != null)
                || (plugin.getCustomConfig().isPlayerPointsEconomyEnabled() && playerPointsAPI != null);
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

    public SellPay resolveSellPay(SellValue value) {
        return resolveSellPay(
                value,
                plugin.getCustomConfig().getPrimaryEconomy(),
                plugin.getCustomConfig().isEconomyFallbackEnabled(),
                plugin.getCustomConfig().isEconomyEnabled(),
                plugin.getCustomConfig().isEconomySystemEnabled(),
                plugin.getCustomConfig().isPlayerPointsEconomyEnabled(),
                economy != null,
                playerPointsAPI != null);
    }

    public boolean depositSellPay(OfflinePlayer player, SellPay pay) {
        if (player == null || pay == null || !pay.hasAny()) return false;

        boolean success = true;
        if (pay.getVaultAmount() > 0) {
            success = economy != null
                    && economy.depositPlayer(player, pay.getVaultAmount()).transactionSuccess()
                    && success;
        }

        if (pay.getPointsAmount() > 0) {
            success = givePoints(player.getUniqueId(), pay.getPointsAmount()) && success;
        }

        return success;
    }

    /**
     * 给玩家发放经济奖励，Vault 可用时优先走 Vault，否则落到 PlayerPoints。
     *
     * @param player 目标玩家
     * @param amount 金额
     * @return 是否成功
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (player == null || amount <= 0) return false;
        RewardType rewardType = getRewardType();
        if (rewardType == RewardType.VAULT) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        if (rewardType == RewardType.PLAYER_POINTS) {
            return givePoints(player.getUniqueId(), amountToPoints(amount));
        }
        return false;
    }

    /**
     * 从玩家取款。
     *
     * @param player 目标玩家
     * @param amount 金额
     * @return 是否成功
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (player == null || amount <= 0) return false;
        RewardType rewardType = getRewardType();
        if (rewardType == RewardType.VAULT) {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        if (rewardType == RewardType.PLAYER_POINTS) {
            return takePoints(player.getUniqueId(), amountToPoints(amount));
        }
        return false;
    }

    /**
     * 查询玩家余额。
     *
     * @param player 目标玩家
     * @return 余额，经济不可用时返回 0
     */
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0;
        RewardType rewardType = getRewardType();
        if (rewardType == RewardType.VAULT) {
            return economy.getBalance(player);
        }
        if (rewardType == RewardType.PLAYER_POINTS) {
            return getPoints(player.getUniqueId());
        }
        return 0;
    }

    /**
     * 给玩家加点数。
     *
     * @param playerId 玩家 UUID
     * @param amount   点数
     * @return 是否成功
     */
    public boolean givePoints(UUID playerId, int amount) {
        if (playerPointsAPI == null) return false;
        return playerPointsAPI.give(playerId, amount);
    }

    public boolean takePoints(UUID playerId, int amount) {
        if (playerPointsAPI == null) return false;
        return playerPointsAPI.take(playerId, amount);
    }

    /**
     * 查询玩家点数。
     *
     * @param playerId 玩家 UUID
     * @return 点数，不可用时返回 0
     */
    public int getPoints(UUID playerId) {
        if (playerPointsAPI == null) return 0;
        return playerPointsAPI.look(playerId);
    }

    private int amountToPoints(double amount) {
        return (int) Math.max(1, Math.round(amount));
    }

    private static boolean isPlayerPointsName(String name) {
        return "playerpoints".equalsIgnoreCase(name)
                || "points".equalsIgnoreCase(name)
                || "pp".equalsIgnoreCase(name);
    }
}
