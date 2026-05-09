package gjum.minecraft.distantsync.mod;

import gjum.minecraft.distantsync.mod.data.BlockColumn;
import gjum.minecraft.distantsync.mod.data.BlockInfo;
import gjum.minecraft.distantsync.mod.data.ChunkTile;
import gjum.minecraft.distantsync.mod.integration.DistantHorizonsAPI;
import gjum.minecraft.distantsync.mod.integration.DistantHorizonsAPI.LodChunkData;
import gjum.minecraft.distantsync.mod.integrations.journeymap.JourneyMapHelper;
import gjum.minecraft.distantsync.mod.integrations.xaerosmap.XaerosWorldMapHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * DistantSync - Synchronizes Distant Horizons LOD chunks with minimaps
 * 
 * This mod bridges the gap between Distant Horizons (which generates terrain beyond render distance)
 * and minimap mods (which typically only show loaded chunks). It monitors Distant Horizons' LOD
 * data and pushes it to compatible minimap mods.
 * 
 * INDEPENDENT from MapSync - works standalone!
 */
@Mod(value = DistantSyncMod.MOD_ID, dist = Dist.CLIENT)
public class DistantSyncMod {
    public static final String MOD_ID = "distantsync";
    public static final Logger LOGGER = LoggerFactory.getLogger(DistantSyncMod.class);
    
    private static DistantSyncMod instance;
    private DistantHorizonsAPI dhApi;
    private Config config;
    
    private long lastCheckTime = 0;
    private long lastDebugTime = 0;
    private static final long DEBUG_INTERVAL_MS = 10000; // Debug log every 10 seconds
    
    // Circular scan state - scan ALL chunks within radius
    private int currentScanRadius = 0; // Current radius being scanned
    private int currentScanX = -1; // Current X offset in scan box
    private int currentScanZ = -1; // Current Z offset in scan box
    private ChunkPos lastPlayerChunk = null;
    
    public DistantSyncMod(IEventBus modEventBus) {
        instance = this;
        LOGGER.info("DistantSync initializing...");
        
        // Load configuration
        this.config = Config.load();
        
        // Register client setup
        modEventBus.addListener(this::onClientSetup);
        
        // Register to the Forge event bus
        NeoForge.EVENT_BUS.register(this);
        
        LOGGER.info("DistantSync initialized");
    }
    
    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("DistantSync client setup");
        
        // Initialize integrations
        event.enqueueWork(() -> {
            dhApi = DistantHorizonsAPI.getInstance();
            
            if (!dhApi.isAvailable()) {
                LOGGER.warn("Distant Horizons not found - mod will be inactive");
            }
            
            boolean jmReady = !JourneyMapHelper.isJourneyMapNotAvailable;
            boolean xmReady = !XaerosWorldMapHelper.isXaerosWorldMapNotAvailable;
            
            LOGGER.info("DistantSync ready! Minimap integration: JourneyMap={}, Xaero's={}", jmReady, xmReady);
        });
    }
    
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Try to initialize DH delayed API if not done yet
        if (dhApi.isAvailable() && !dhApi.isInitialized()) {
            dhApi.initializeDelayedApi();
            if (dhApi.isInitialized()) {
                LOGGER.info("Distant Horizons API fully initialized!");
            }
        }
        
        // Debug logging every 10 seconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDebugTime >= DEBUG_INTERVAL_MS) {
            lastDebugTime = currentTime;
            boolean minimapsReady = areMinimapsReady();
            LOGGER.debug("Status check: DH initialized={}, minimaps ready={}", dhApi.isInitialized(), minimapsReady);
            if (!minimapsReady) {
                boolean jmAvailable = !JourneyMapHelper.isJourneyMapNotAvailable;
                boolean jmMapping = jmAvailable && JourneyMapHelper.isMapping();
                boolean xmAvailable = !XaerosWorldMapHelper.isXaerosWorldMapNotAvailable;
                boolean xmMapping = xmAvailable && XaerosWorldMapHelper.isMapping();
                LOGGER.debug("Minimap details: JourneyMap available={}, mapping={} | Xaero's available={}, mapping={}", 
                    jmAvailable, jmMapping, xmAvailable, xmMapping);
            }
        }
        
        // Don't do anything if we don't have both DH and at least one minimap ready
        if (!dhApi.isInitialized() || !areMinimapsReady()) {
            return;
        }
        
        // Periodic check for LOD updates
        if (currentTime - lastCheckTime >= config.checkIntervalMs) {
            lastCheckTime = currentTime;
            checkForLodUpdates();
        }
    }
    
    private boolean areMinimapsReady() {
        boolean jmReady = !JourneyMapHelper.isJourneyMapNotAvailable && JourneyMapHelper.isMapping();
        boolean xmReady = !XaerosWorldMapHelper.isXaerosWorldMapNotAvailable && XaerosWorldMapHelper.isMapping();
        return jmReady || xmReady;
    }
    
    /**
     * Called when a regular Minecraft chunk is loaded.
     * We don't need to do anything here - real chunks are handled by minimap mods.
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        // Real chunks are already handled by minimap mods themselves.
        // We only handle LOD chunks that are NOT loaded in the world.
        // No need to mark anything here - we check mc.level.hasChunk() instead
    }
    
    /**
     * Periodically check for new LOD chunks from Distant Horizons.
     * Uses a spiral scan pattern to progressively cover all chunks up to configured scanRadius.
     * The scan continues across ticks to cover the entire area without lag.
     */
    private void checkForLodUpdates() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        
        // Get player position
        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        
        // Reset circular scan if player moved to a different chunk
        if (lastPlayerChunk == null || !lastPlayerChunk.equals(playerChunk)) {
            lastPlayerChunk = playerChunk;
            currentScanRadius = 0;
            currentScanX = 0;
            currentScanZ = 0;
            LOGGER.debug("Player moved to chunk {}, resetting circular scan", playerChunk);
        }
        
        int chunksProcessed = 0;
        int chunksScanned = 0;
        int chunksWithLod = 0;
        int chunksCheckedForLod = 0;
        int chunksSkippedAlreadyProcessed = 0;
        
        // Circular scan algorithm - scan ALL chunks within current radius
        // This ensures no gaps in coverage
        while (chunksProcessed < config.chunksPerTick) {
            // Check if we've completed all radii
            if (currentScanRadius > config.scanRadius) {
                // Reset to center to scan again (for new LODs)
                currentScanRadius = 0;
                currentScanX = 0;
                currentScanZ = 0;
                LOGGER.debug("Completed full circular scan up to {} chunks, restarting from center", config.scanRadius);
                break;
            }
            
            // Calculate chunk position
            int offsetX = currentScanX - currentScanRadius;
            int offsetZ = currentScanZ - currentScanRadius;
            
            // Check if this chunk is within the circular radius
            double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            
            if (distance <= currentScanRadius) {
                ChunkPos checkPos = new ChunkPos(playerChunk.x + offsetX, playerChunk.z + offsetZ);
                chunksScanned++;
                
                // Check if we already processed this chunk
                if (!dhApi.isChunkProcessed(checkPos)) {
                    chunksCheckedForLod++;
                    
                    // Check if DH has LOD data for this chunk
                    if (dhApi.hasLodData(checkPos, mc.level)) {
                        chunksWithLod++;
                        // Extract LOD data with ALL layers
                        LodChunkData lodData = dhApi.extractLodData(checkPos, mc.level);
                        if (lodData != null) {
                            // Render to minimaps
                            boolean rendered = false;
                            
                            // JourneyMap: Use direct LOD rendering with native color calculation
                            if (!JourneyMapHelper.isJourneyMapNotAvailable) {
                                boolean jmRendered = JourneyMapHelper.updateWithLodData(lodData, mc.level.dimension());
                                rendered |= jmRendered;
                            }
                            
                            // Xaero's: Still needs ChunkTile conversion
                            if (!XaerosWorldMapHelper.isXaerosWorldMapNotAvailable) {
                                ChunkTile chunkTile = convertLodToChunkTile(checkPos, mc.level, lodData);
                                if (chunkTile != null) {
                                    boolean xmRendered = XaerosWorldMapHelper.updateWithChunkTile(chunkTile);
                                    rendered |= xmRendered;
                                }
                            }
                            
                            if (rendered) {
                                // Mark as processed to avoid re-rendering every tick
                                dhApi.markChunkProcessed(checkPos);
                                chunksProcessed++;
                            }
                        }
                    }
                } else {
                    chunksSkippedAlreadyProcessed++;
                }
            }
            
            // Advance to next position
            advanceCircularPosition();
        }
        
        if (chunksScanned > 0 && (chunksWithLod > 0 || currentScanRadius % 50 == 0)) {
            LOGGER.debug("Circular scan at radius {}: {} chunks scanned, {} skipped, {} checked for LOD, {} with LOD data, {} rendered", 
                currentScanRadius, chunksScanned, chunksSkippedAlreadyProcessed, chunksCheckedForLod, chunksWithLod, chunksProcessed);
        }
        if (chunksProcessed > 0) {
            LOGGER.debug("Processed {} LOD chunks from Distant Horizons this tick (radius {})", chunksProcessed, currentScanRadius);
        }
    }
    
    /**
     * Advances the circular scan position by one step.
     * Scans all chunks in square boxes, filtering by circular distance.
     * This ensures complete coverage with no gaps.
     */
    private void advanceCircularPosition() {
        int boxSize = currentScanRadius * 2 + 1;
        
        // Move to next position in current box
        currentScanZ++;
        if (currentScanZ >= boxSize) {
            currentScanZ = 0;
            currentScanX++;
        }
        
        // Check if we've completed this box
        if (currentScanX >= boxSize) {
            // Move to next radius
            currentScanRadius++;
            currentScanX = 0;
            currentScanZ = 0;
        }
    }
    
    /**
     * Convert LOD data to ChunkTile format for minimap rendering (Xaero's)
     */
    private ChunkTile convertLodToChunkTile(ChunkPos pos, Level level, LodChunkData lodData) {
        if (lodData == null || lodData.heightMap == null || lodData.layersMap == null) {
            return null;
        }
        
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome defaultBiome = biomeRegistry.getOrThrow(Biomes.PLAINS);
        
        BlockColumn[] columns = new BlockColumn[256]; // 16x16
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = (z << 4) | x;
                
                // Get all layers from LOD data
                List<BlockInfo> layers = lodData.layersMap[x][z];
                
                if (layers == null || layers.isEmpty()) {
                    // Create empty column
                    columns[idx] = new BlockColumn(
                        List.of(new BlockInfo(level.getMinBuildHeight(), Blocks.AIR.defaultBlockState())),
                        defaultBiome,
                        15
                    );
                    continue;
                }
                
                // Use the layers directly - they contain all vertical data
                Biome biome = lodData.biomeMap[x][z];
                if (biome == null) {
                    biome = defaultBiome;
                }
                int light = 15; // Default to full brightness
                
                columns[idx] = new BlockColumn(layers, biome, light);
            }
        }
        
        return new ChunkTile(
            pos.x,
            pos.z,
            level.dimension(),
            columns,
            System.currentTimeMillis()
        );
    }

    
    public static DistantSyncMod getInstance() {
        return instance;
    }
    
    public Config getConfig() {
        return config;
    }
    
    public void reloadConfig() {
        this.config = Config.load();
        LOGGER.info("Config reloaded: scanRadius={}, chunksPerTick={}, checkIntervalMs={}", 
            config.scanRadius, config.chunksPerTick, config.checkIntervalMs);
    }
    
    public DistantHorizonsAPI getDhApi() {
        return dhApi;
    }
}
