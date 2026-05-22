package me.kkfish.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;



import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityBatchProcessor {

    private static final Logger LOGGER = Logger.getLogger("KKFish-EntityBatchProcessor");
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_FLUSH_INTERVAL = 20L;
    
    private final List<ParticleBatch> particleBatches = new ArrayList<>();
    private final List<ItemBatch> itemBatches = new ArrayList<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final int batchSize;
    private final long flushInterval;
    private boolean isFlushing = false;
    
    private static class ParticleBatch {
        final Particle particle;
        final Location location;
        final int count;
        final double offsetX;
        final double offsetY;
        final double offsetZ;
        final double extra;
        final Object data;
        
        ParticleBatch(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
            this.particle = particle;
            this.location = location;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.extra = extra;
            this.data = data;
        }
    }
    
    private static class ItemBatch {
        final Location location;
        final ItemStack itemStack;
        final boolean isNaturally;
        
        ItemBatch(Location location, ItemStack itemStack, boolean isNaturally) {
            this.location = location;
            this.itemStack = itemStack;
            this.isNaturally = isNaturally;
        }
    }
    
    public EntityBatchProcessor() {
        this(DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL);
    }
    
    public EntityBatchProcessor(int batchSize, long flushInterval) {
        this.batchSize = batchSize;
        this.flushInterval = flushInterval;
    }
    
    public void addParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        particleBatches.add(new ParticleBatch(particle, location, count, offsetX, offsetY, offsetZ, extra, data));
        pendingCount.incrementAndGet();
        checkFlush();
    }
    
    public void addItem(Location location, ItemStack itemStack, boolean isNaturally) {
        itemBatches.add(new ItemBatch(location, itemStack, isNaturally));
        pendingCount.incrementAndGet();
        checkFlush();
    }
    
    private void checkFlush() {
        if (pendingCount.get() >= batchSize && !isFlushing) {
            flush();
        }
    }
    
    public void flush() {
        if (isFlushing) return;
        
        isFlushing = true;
        
        if (!particleBatches.isEmpty()) {
            processParticleBatches();
        }
        
        if (!itemBatches.isEmpty()) {
            processItemBatches();
        }
        
        pendingCount.set(0);
        isFlushing = false;
    }
    
    private void processParticleBatches() {
        List<ParticleBatch> batches = new ArrayList<>(particleBatches);
        particleBatches.clear();
        
        for (ParticleBatch batch : batches) {
            World world = batch.location.getWorld();
            if (world == null) continue;
            
            try {
                if (batch.data != null) {
                    world.spawnParticle(batch.particle, batch.location, batch.count, batch.offsetX, batch.offsetY, batch.offsetZ, batch.extra, batch.data);
                } else {
                    try {
                        if (batch.particle == Particle.REDSTONE) {
                            org.bukkit.Particle.DustOptions dustOptions = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
                            world.spawnParticle(batch.particle, batch.location, batch.count, batch.offsetX, batch.offsetY, batch.offsetZ, batch.extra, dustOptions);
                        } else {
                            world.spawnParticle(batch.particle, batch.location, batch.count, batch.offsetX, batch.offsetY, batch.offsetZ, batch.extra);
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Failed to spawn particle without data: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to spawn particle: " + e.getMessage());
            }
        }
    }
    
    private void processItemBatches() {
        List<ItemBatch> batches = new ArrayList<>(itemBatches);
        itemBatches.clear();
        
        for (ItemBatch batch : batches) {
            World world = batch.location.getWorld();
            if (world == null) continue;
            
            try {
                Item item;
                if (batch.isNaturally) {
                    item = world.dropItemNaturally(batch.location, batch.itemStack);
                } else {
                    item = world.dropItem(batch.location, batch.itemStack);
                }
                item.setPickupDelay(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public int getPendingCount() {
        return pendingCount.get();
    }
    
    public void clear() {
        particleBatches.clear();
        itemBatches.clear();
        pendingCount.set(0);
    }
}
