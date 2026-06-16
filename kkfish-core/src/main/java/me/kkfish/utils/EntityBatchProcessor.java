package me.kkfish.utils;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量粒子/物品生成处理器。
 *
 * <p>遵循 XSeries 规则：所有粒子生成通过 {@link XSeriesUtil} 进行，
 * 禁止直接使用 {@link org.bukkit.Particle} 枚举或 {@link World#spawnParticle}。</p>
 */
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

    /**
     * 粒子批次记录。使用字符串粒子名而非 {@link org.bukkit.Particle} 枚举，
     * 由 {@link XSeriesUtil} 在刷新时解析，保证多版本兼容。
     */
    private static class ParticleBatch {
        final String particleName;
        final Location location;
        final int count;
        final double offsetX;
        final double offsetY;
        final double offsetZ;
        final double extra;
        final Object data;

        ParticleBatch(String particleName, Location location, int count,
                      double offsetX, double offsetY, double offsetZ, double extra, Object data) {
            this.particleName = particleName;
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

    /**
     * 添加粒子批次。使用粒子名称字符串，由 XSeries 解析。
     *
     * @param particleName XSeries 粒子名（如 "FLAME"、"REDSTONE"）
     */
    public void addParticle(String particleName, Location location, int count,
                            double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        particleBatches.add(new ParticleBatch(particleName, location, count, offsetX, offsetY, offsetZ, extra, data));
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

    /**
     * 处理粒子批次。统一通过 {@link XSeriesUtil} 生成粒子，
     * 不再直接使用 {@link org.bukkit.Particle} 枚举或 {@link World#spawnParticle}。
     */
    private void processParticleBatches() {
        List<ParticleBatch> batches = new ArrayList<>(particleBatches);
        particleBatches.clear();

        for (ParticleBatch batch : batches) {
            if (batch.location == null || batch.location.getWorld() == null) continue;

            try {
                if (batch.data != null) {
                    XSeriesUtil.spawnParticleWithData(
                            batch.location, batch.particleName, batch.count,
                            batch.offsetX, batch.offsetY, batch.offsetZ, batch.extra, batch.data);
                } else {
                    XSeriesUtil.spawnParticle(
                            batch.location, batch.particleName, batch.count,
                            batch.offsetX, batch.offsetY, batch.offsetZ, batch.extra);
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to spawn particle '" + batch.particleName + "': " + e.getMessage());
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
