package com.personal.timetravel;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Makes plants keep growing while the clock is spun forward.
 *
 * Skipping the clock doesn't run any ticks, and plants only grow from random ticks. So for the time that was
 * skipped we hand every growing block the random ticks it would have had: each block normally gets one with a
 * chance of randomTickSpeed / 4096 per game tick, so the number of ticks it missed is drawn from that.
 *
 *  - Chunks that are loaded while the clock spins are caught up as it spins (in rounds, so day and night light
 *    conditions roughly follow the sky).
 *  - Chunks that are NOT loaded (for example the world around a TARDIS that is in the vortex) are caught up when
 *    they next load. Each saved chunk is stamped with the running total of skipped ticks; on load, the
 *    difference is what it missed.
 *
 * The work is queued and done a little per server tick so a long trip doesn't freeze the game.
 */
@Mod.EventBusSubscriber(modid = TimeTravelMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlantGrowth {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String STAMP_KEY = "timetravel_skipped";
    /** Loaded chunks are caught up every time this many ticks have been skipped. */
    private static final long ROUND_TICKS = 1200;
    /** Max time spent per server tick on catching up, in nanoseconds. */
    private static final long BUDGET_NANOS = 4_000_000L;
    /** A chunk waits this long for its neighbours to load before it is done anyway. */
    private static final int NEIGHBOUR_WAIT_TICKS = 100;
    /** A chunk that never finishes loading is dropped after this long. */
    private static final int MAX_AGE_TICKS = 6000;
    /** Upper limit on missed random ticks per block (a long enough skip would otherwise make this huge). */
    private static final double MAX_TICKS_PER_BLOCK = 400;

    private record Key(ResourceKey<Level> dim, long chunk) {}

    private record Stamp(ResourceKey<Level> dim, long chunk, long stamp) {}

    private static final class Job {
        long live;
        long loaded;
        int age;

        long total() {
            return live + loaded;
        }
    }

    /** The running total of skipped ticks, saved with the world. */
    private static final class Ledger extends SavedData {
        long total;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("total", total);
            return tag;
        }

        static Ledger load(CompoundTag tag) {
            Ledger l = new Ledger();
            l.total = tag.getLong("total");
            return l;
        }
    }

    private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new ConcurrentHashMap<>();
    /** Chunk loads report from worker threads; they are picked up on the main thread. */
    private static final ConcurrentLinkedQueue<Stamp> INCOMING = new ConcurrentLinkedQueue<>();
    private static final Map<Key, Job> JOBS = new LinkedHashMap<>();

    private static Ledger ledger;
    private static volatile long total;
    private static volatile boolean ready;
    private static long pending;
    private static long lastSkipTick;

    private PlantGrowth() {}

    /** Call this each time the day clock has been moved forward by `ticks`. */
    public static void skipped(MinecraftServer server, long ticks) {
        if (ticks <= 0 || !Settings.get().growPlants) return;
        ensureLedger(server);
        ledger.total += ticks;
        total = ledger.total;
        ledger.setDirty();
        pending += ticks;
        lastSkipTick = server.getTickCount();
        if (pending >= ROUND_TICKS) flush();
    }

    private static void ensureLedger(MinecraftServer server) {
        if (ledger != null) return;
        ledger = server.overworld().getDataStorage().computeIfAbsent(Ledger::load, Ledger::new, "timetravel_skipped");
        total = ledger.total;
        ready = true;
    }

    /** Queues a catch-up of `pending` ticks for every chunk that is loaded right now. */
    private static void flush() {
        long ticks = pending;
        pending = 0;
        if (ticks <= 0) return;
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : LOADED.entrySet()) {
            for (long chunk : e.getValue()) {
                JOBS.computeIfAbsent(new Key(e.getKey(), chunk), k -> new Job()).live += ticks;
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.computeIfAbsent(level.dimension(), d -> ConcurrentHashMap.newKeySet()).add(chunk.getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Set<Long> set = LOADED.get(level.dimension());
            if (set != null) set.remove(chunk.getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkDataSave(ChunkDataEvent.Save event) {
        if (ready) event.getData().putLong(STAMP_KEY, total);
    }

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        CompoundTag data = event.getData();
        if (data == null || !data.contains(STAMP_KEY, Tag.TAG_LONG)) return;
        if (event.getLevel() instanceof ServerLevel level) {
            INCOMING.add(new Stamp(level.dimension(), event.getChunk().getPos().toLong(), data.getLong(STAMP_KEY)));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
        INCOMING.clear();
        JOBS.clear();
        ledger = null;
        total = 0;
        ready = false;
        pending = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!ready) ensureLedger(server);

        // The clock stopped moving: catch up whatever is left over.
        if (pending > 0 && server.getTickCount() - lastSkipTick >= 10) flush();

        Settings settings = Settings.get();
        Stamp stamp;
        while ((stamp = INCOMING.poll()) != null) {
            long missed = total - stamp.stamp();
            if (missed > 0 && settings.growPlants && settings.catchUpUnloadedChunks) {
                Job job = JOBS.computeIfAbsent(new Key(stamp.dim(), stamp.chunk()), k -> new Job());
                job.loaded = Math.max(job.loaded, missed);
            }
        }
        if (JOBS.isEmpty()) return;

        long deadline = System.nanoTime() + BUDGET_NANOS;
        Iterator<Map.Entry<Key, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext() && System.nanoTime() < deadline) {
            Map.Entry<Key, Job> entry = it.next();
            Key key = entry.getKey();
            Job job = entry.getValue();
            ServerLevel level = server.getLevel(key.dim());
            if (level == null) {
                it.remove();
                continue;
            }
            int cx = ChunkPos.getX(key.chunk());
            int cz = ChunkPos.getZ(key.chunk());
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            job.age++;
            if (chunk == null || (job.age < NEIGHBOUR_WAIT_TICKS && !neighboursLoaded(level, cx, cz))) {
                if (job.age > MAX_AGE_TICKS) it.remove();
                continue;
            }
            it.remove();
            try {
                growChunk(level, chunk, job.total());
            } catch (Throwable t) {
                LOGGER.error("Plant catch-up failed for chunk {}, {} in {}", cx, cz, key.dim().location(), t);
            }
        }
    }

    private static boolean neighboursLoaded(ServerLevel level, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
            }
        }
        return true;
    }

    /** Gives every growing block in the chunk the random ticks it missed over `ticks` game ticks. */
    private static void growChunk(ServerLevel level, LevelChunk chunk, long ticks) {
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (speed <= 0 || ticks <= 0) return;
        double lambda = Math.min(ticks * (double) speed / 4096.0, MAX_TICKS_PER_BLOCK);
        RandomSource random = level.getRandom();

        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        java.util.List<BlockPos> plants = new java.util.ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(PlantGrowth::isGrowing)) continue;
            int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isGrowing(section.getBlockState(x, y, z))) {
                            plants.add(new BlockPos(chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }

        for (BlockPos pos : plants) {
            int n = poisson(random, lambda);
            for (int i = 0; i < n; i++) {
                BlockState state = level.getBlockState(pos);
                if (!isGrowing(state)) break; // grown up, eaten, or turned into something else
                state.randomTick(level, pos, random);
            }
        }
        // Make sure the new stamp gets saved even if nothing visibly changed.
        chunk.setUnsaved(true);
    }

    /** A random-ticking block that is a plant (crops, saplings, cane, cactus, kelp, vines, bamboo, ...). */
    private static boolean isGrowing(BlockState state) {
        return state.isRandomlyTicking() && isPlant(state.getBlock());
    }

    private static boolean isPlant(Block block) {
        return block instanceof BushBlock // crops, stems, saplings, berries, nether wart, mushrooms, ...
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof GrowingPlantBlock // kelp, weeping/twisting vines, cave vines
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof CocoaBlock
                || block instanceof VineBlock
                || block instanceof ChorusFlowerBlock;
    }

    /** Number of events in a period where `lambda` are expected. */
    private static int poisson(RandomSource random, double lambda) {
        if (lambda <= 0) return 0;
        if (lambda > 30) {
            return Math.max(0, (int) Math.round(lambda + Math.sqrt(lambda) * random.nextGaussian()));
        }
        double limit = Math.exp(-lambda);
        double p = 1;
        int k = 0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > limit);
        return k - 1;
    }
}
