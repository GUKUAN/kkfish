package me.kkfish.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyServiceTest {

    @Test
    void rewardUsesVaultWhenVaultIsEnabledAndReady() {
        assertEquals(EconomyService.RewardType.VAULT,
                EconomyService.chooseRewardType(true, true, true, true));
    }

    @Test
    void rewardFallsBackToPlayerPointsWhenVaultIsMissing() {
        assertEquals(EconomyService.RewardType.PLAYER_POINTS,
                EconomyService.chooseRewardType(true, true, false, true));
    }

    @Test
    void rewardUsesPlayerPointsWhenVaultSwitchIsOff() {
        assertEquals(EconomyService.RewardType.PLAYER_POINTS,
                EconomyService.chooseRewardType(true, false, true, true));
    }

    @Test
    void rewardStopsWhenMainEconomySwitchIsOff() {
        assertEquals(EconomyService.RewardType.NONE,
                EconomyService.chooseRewardType(false, true, true, true));
    }

    @Test
    void rewardStopsWhenNoEconomyProviderExists() {
        assertEquals(EconomyService.RewardType.NONE,
                EconomyService.chooseRewardType(true, true, false, false));
    }

    @Test
    void oldValueUsesVaultAsDefaultPrimaryEconomy() {
        SellValue value = SellValue.oldValue(60);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "vault", true, true, true, true, true, true);

        assertEquals(60, pay.getVaultAmount());
        assertEquals(0, pay.getPointsAmount());
    }

    @Test
    void oldValueCanUsePlayerPointsAsPrimaryEconomy() {
        SellValue value = SellValue.oldValue(35);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "playerpoints", true, true, true, true, true, true);

        assertEquals(0, pay.getVaultAmount());
        assertEquals(35, pay.getPointsAmount());
    }

    @Test
    void oldValueFallsBackToPlayerPointsWhenVaultPrimaryIsMissing() {
        SellValue value = SellValue.oldValue(22);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "vault", true, true, true, true, false, true);

        assertEquals(0, pay.getVaultAmount());
        assertEquals(22, pay.getPointsAmount());
    }

    @Test
    void oldValueStopsWhenFallbackIsDisabledAndPrimaryMissing() {
        SellValue value = SellValue.oldValue(22);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "vault", false, true, true, true, false, true);

        assertEquals(0, pay.getVaultAmount());
        assertEquals(0, pay.getPointsAmount());
    }

    @Test
    void splitValuePaysVaultAndPlayerPointsTogether() {
        SellValue value = SellValue.splitValue(120, 18);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "vault", true, true, true, true, true, true);

        assertEquals(120, pay.getVaultAmount());
        assertEquals(18, pay.getPointsAmount());
    }

    @Test
    void splitValueDoesNotUseFallbackForExplicitProvider() {
        SellValue value = SellValue.splitValue(120, 0);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "playerpoints", true, true, true, true, true, true);

        assertEquals(120, pay.getVaultAmount());
        assertEquals(0, pay.getPointsAmount());
    }

    @Test
    void disabledPlayerPointsSwitchPreventsPointsPayment() {
        SellValue value = SellValue.splitValue(0, 18);

        EconomyService.SellPay pay = EconomyService.resolveSellPay(
                value, "playerpoints", true, true, true, false, true, true);

        assertEquals(0, pay.getVaultAmount());
        assertEquals(0, pay.getPointsAmount());
    }
}
