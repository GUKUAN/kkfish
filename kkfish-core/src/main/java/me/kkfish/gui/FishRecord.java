package me.kkfish.gui;

public class FishRecord {
    private int totalFishCaught;
    private int rareFishCaught;
    private int legendaryFishCaught;
    
    public FishRecord() {
        this.totalFishCaught = 0;
        this.rareFishCaught = 0;
        this.legendaryFishCaught = 0;
    }
    
    public FishRecord(int total, int rare, int legendary) {
        this.totalFishCaught = total;
        this.rareFishCaught = rare;
        this.legendaryFishCaught = legendary;
    }
    
    public int getTotalFishCaught() {
        return totalFishCaught;
    }
    
    public void setTotalFishCaught(int totalFishCaught) {
        this.totalFishCaught = totalFishCaught;
    }
    
    public int getRareFishCaught() {
        return rareFishCaught;
    }
    
    public void setRareFishCaught(int rareFishCaught) {
        this.rareFishCaught = rareFishCaught;
    }
    
    public int getLegendaryFishCaught() {
        return legendaryFishCaught;
    }
    
    public void setLegendaryFishCaught(int legendaryFishCaught) {
        this.legendaryFishCaught = legendaryFishCaught;
    }
    
    public void incrementTotal() {
        totalFishCaught++;
    }
    
    public void incrementRare() {
        rareFishCaught++;
        totalFishCaught++;
    }
    
    public void incrementLegendary() {
        legendaryFishCaught++;
        rareFishCaught++;
        totalFishCaught++;
    }
}
