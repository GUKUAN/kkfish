package me.kkfish.interfaces;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public interface CustomCompetitionType {

    String getTypeId();

    CustomCompetitionData createData();

    void recordCatch(CustomCompetitionData data, String fishName, double fishValue, ItemStack fishItem, Player player);

    int compareData(CustomCompetitionData data1, CustomCompetitionData data2);

    String formatScore(CustomCompetitionData data);

    String getResultDescription(CustomCompetitionData data);

    void loadConfig(Map<String, Object> config);
}