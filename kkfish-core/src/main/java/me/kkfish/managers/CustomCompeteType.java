package me.kkfish.managers;

import me.kkfish.interfaces.CustomCompetitionData;
import me.kkfish.interfaces.CustomCompetitionType;
import me.kkfish.kkfish;

import java.util.HashMap;
import java.util.Map;

public class CustomCompeteType {

    private final kkfish plugin;
    private final Map<String, CustomCompetitionType> customTypes = new HashMap<>();

    public CustomCompeteType(kkfish plugin) {
        this.plugin = plugin;
        registerDefaultTypes();
    }

    private void registerDefaultTypes() {
    }

    public void registerType(CustomCompetitionType type) {
        if (type != null && type.getTypeId() != null && !type.getTypeId().isEmpty()) {
            customTypes.put(type.getTypeId(), type);
            kkfish.log("已注册自定义比赛类型: " + type.getTypeId());
        }
    }

    public CustomCompetitionType getType(String typeId) {
        return customTypes.get(typeId);
    }

    public boolean hasType(String typeId) {
        return customTypes.containsKey(typeId);
    }

    public Map<String, CustomCompetitionType> getAllTypes() {
        return new HashMap<>(customTypes);
    }

    public void loadFromConfig(Map<String, Object> config) {
        if (config == null) return;

    }
}