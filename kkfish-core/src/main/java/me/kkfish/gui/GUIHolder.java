package me.kkfish.gui;

import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;
import me.kkfish.managers.GUI;

public class GUIHolder implements InventoryHolder {
    private final GUI.GUIType type;
    private int page = 0;
    private Inventory inventory;
    
    public GUIHolder(GUI.GUIType type) {
        this.type = type;
    }
    
    public GUIHolder(GUI.GUIType type, int page) {
        this.type = type;
        this.page = page;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    
    public GUI.GUIType getType() {
        return type;
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
}
