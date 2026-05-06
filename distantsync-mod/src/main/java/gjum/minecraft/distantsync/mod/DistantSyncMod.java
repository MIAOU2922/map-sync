package gjum.minecraft.distantsync.mod;

import gjum.minecraft.distantsync.mod.integration.DistantHorizonsAPI;
import gjum.minecraft.distantsync.mod.integration.MinimapAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
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

/**
 * DistantSync - Synchronizes Distant Horizons LOD chunks with minimaps
 * 
 * This mod bridges the gap between Distant Horizons (which generates terrain beyond render distance)
 * and minimap mods (which typically only show loaded chunks). It monitors Distant Horizons' LOD
 * data and pushes it to compatible minimap mods.
 */
@Mod(value = DistantSyncMod.MOD_ID, dist = Dist.CLIENT)
public class DistantSyncMod {
    public static final String MOD_ID = "distantsync";
    public static final Logger LOGGER = LoggerFactory.getLogger(DistantSyncMod.class);
    
    private static DistantSyncMod instance;
    private DistantHorizonsAPI dhApi;
    private MinimapAPI minimapApi;
    
    private long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 1000; // Check every second
    
    public DistantSyncMod(IEventBus modEventBus) {
        instance = this;
        LOGGER.info("DistantSync initializing...");
        
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
            minimapApi = MinimapAPI.getInstance();
            
            if (!dhApi.isAvailable()) {
                LOGGER.warn("Distant Horizons not found - mod will be inactive");
            }
            
            if (!minimapApi.hasMinimaps()) {
                LOGGER.warn("No supported minimap mods found - mod will be inactive");
            }
            
            if (dhApi.isAvailable() && minimapApi.hasMinimaps()) {
                LOGGER.info("DistantSync ready! Detected minimaps: {}", 
                    String.join(", ", minimapApi.getDetectedMinimaps()));
            }
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
        
        // Don't do anything if we don't have both DH and minimaps
        if (!dhApi.isInitialized() || !minimapApi.hasMinimaps()) {
            return;
        }
        
        // Periodic check for LOD updates
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime >= CHECK_INTERVAL_MS) {
            lastCheckTime = currentTime;
            checkForLodUpdates();
        }
    }
    
    /**
     * Called when a regular Minecraft chunk is loaded.
     * Mark it as processed so we don't override it with LOD data.
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getLevel() != mc.level) {
            return;
        }
        
        // Mark this chunk as already loaded, so we don't override it with LOD data
        ChunkPos pos = chunk.getPos();
        dhApi.markChunkProcessed(pos);
        
        // Update minimap with the actual chunk data
        if (minimapApi.hasMinimaps()) {
            DistantHorizonsAPI.LodChunkData lodData = 
                DistantHorizonsAPI.LodChunkData.fromMinecraftChunk(chunk);
            minimapApi.updateChunk(lodData);
        }
    }
    
    /**
     * Periodically check for new LOD chunks from Distant Horizons.
     * Only processes chunks that are NOT already loaded in the world.
     */
    private void checkForLodUpdates() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        
        // Get player position
        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        
        // Scan a radius around the player for LOD chunks
        // Start with a modest radius (e.g., 8 chunks = 128 blocks)
        int scanRadius = 8;
        int chunksProcessed = 0;
        int maxChunksPerTick = 5; // Limit to avoid lag
        
        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                if (chunksProcessed >= maxChunksPerTick) {
                    return; // Process more next tick
                }
                
                ChunkPos checkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                
                // Skip if this chunk has already been processed
                if (dhApi.isChunkProcessed(checkPos)) {
                    continue;
                }
                
                // Skip if this chunk is actually loaded (we handle those in onChunkLoad)
                if (mc.level.hasChunk(checkPos.x, checkPos.z)) {
                    dhApi.markChunkProcessed(checkPos);
                    continue;
                }
                
                // Check if DH has LOD data for this unloaded chunk
                if (dhApi.hasLodData(checkPos, mc.level)) {
                    // Extract and send to minimaps
                    DistantHorizonsAPI.LodChunkData lodData = dhApi.extractLodData(checkPos, mc.level);
                    if (lodData != null) {
                        minimapApi.updateChunk(lodData);
                        dhApi.markChunkProcessed(checkPos);
                        chunksProcessed++;
                        
                        if (chunksProcessed == 1) {
                            LOGGER.debug("Processing LOD chunk {} from Distant Horizons", checkPos);
                        }
                    }
                }
            }
        }
        
        if (chunksProcessed > 0) {
            LOGGER.info("Processed {} LOD chunks from Distant Horizons this tick", chunksProcessed);
        }
    }
    
    public static DistantSyncMod getInstance() {
        return instance;
    }
    
    public DistantHorizonsAPI getDhApi() {
        return dhApi;
    }
    
    public MinimapAPI getMinimapApi() {
        return minimapApi;
    }
}
