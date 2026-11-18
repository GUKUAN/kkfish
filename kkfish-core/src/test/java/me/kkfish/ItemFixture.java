package me.kkfish;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ItemFixture extends ItemStack {
    private ItemMeta meta;
    private final Map<NamespacedKey, Object> values = new HashMap<>();
    private final Map<NamespacedKey, PersistentDataType<?, ?>> types = new HashMap<>();

    public ItemFixture(Material material, String name) {
        super(material);
        PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "set": values.put((NamespacedKey) args[0], args[2]); types.put((NamespacedKey) args[0], (PersistentDataType<?, ?>) args[1]); return null;
                        case "has": return types.get(args[0]) == args[1];
                        case "get": return values.get(args[0]);
                        case "getKeys": return values.keySet();
                        case "isEmpty": return values.isEmpty();
                        default: return null;
                    }
                });
        meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class[]{ItemMeta.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getDisplayName": return name;
                        case "hasDisplayName": return name != null;
                        case "getPersistentDataContainer": return container;
                        default: return method.getReturnType() == boolean.class ? false : null;
                    }
                });
    }
    @Override public ItemMeta getItemMeta() { return meta; }
    @Override public boolean hasItemMeta() { return true; }
    @Override public boolean setItemMeta(ItemMeta meta) { this.meta = meta; return true; }
}
