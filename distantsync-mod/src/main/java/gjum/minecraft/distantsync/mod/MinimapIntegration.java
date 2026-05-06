package gjum.minecraft.distantsync.mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles integration with minimap mods (VoxelMap, JourneyMap, Xaero's)
 * 
 * This class is responsible for:
 * - Detecting which minimap mods are loaded
 * - Converting LOD data to minimap-specific formats
 * - Pushing terrain updates to the active minimap
 */
public class MinimapIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinimapIntegration.class);
    
    private MinimapType activeMinimapType = MinimapType.NONE;
    
    public MinimapIntegration() {
        detectMinimapMods();
    }
    
    /**
     * Detect which minimap mods are loaded
     */
    private void detectMinimapMods() {
        // Check for VoxelMap
        try {
            Class.forName("com.mamiyaotaru.voxelmap.VoxelMap");
            activeMinimapType = MinimapType.VOXELMAP;
            LOGGER.info("VoxelMap detected");
            return;
        } catch (ClassNotFoundException ignored) {}
        
        // Check for JourneyMap
        try {
            Class.forName("journeymap.client.JourneymapClient");
            activeMinimapType = MinimapType.JOURNEYMAP;
            LOGGER.info("JourneyMap detected");
            return;
        } catch (ClassNotFoundException ignored) {}
        
        // Check for Xaero's World Map
        try {
            Class.forName("xaero.map.WorldMap");
            activeMinimapType = MinimapType.XAEROS;
            LOGGER.info("Xaero's World Map detected");
            return;
        } catch (ClassNotFoundException ignored) {}
        
        LOGGER.warn("No supported minimap mod detected - DistantSync will be inactive");
    }
    
    /**
     * Update a chunk on the minimap with LOD data
     */
    public void updateChunk(DistantHorizonsIntegration.LodChunkUpdate update) {
        if (activeMinimapType == MinimapType.NONE) {
            return;
        }
        
        switch (activeMinimapType) {
            case VOXELMAP:
                updateVoxelMap(update);
                break;
            case JOURNEYMAP:
                updateJourneyMap(update);
                break;
            case XAEROS:
                updateXaeros(update);
                break;
        }
    }
    
    /**
     * Update VoxelMap with LOD chunk data
     */
    private void updateVoxelMap(DistantHorizonsIntegration.LodChunkUpdate update) {
        // TODO: Implement VoxelMap API integration
        LOGGER.debug("Updating VoxelMap chunk at {}, {}", update.chunkX, update.chunkZ);
    }
    
    /**
     * Update JourneyMap with LOD chunk data
     */
    private void updateJourneyMap(DistantHorizonsIntegration.LodChunkUpdate update) {
        // TODO: Implement JourneyMap API integration
        LOGGER.debug("Updating JourneyMap chunk at {}, {}", update.chunkX, update.chunkZ);
    }
    
    /**
     * Update Xaero's World Map with LOD chunk data
     */
    private void updateXaeros(DistantHorizonsIntegration.LodChunkUpdate update) {
        // TODO: Implement Xaero's API integration
        LOGGER.debug("Updating Xaero's chunk at {}, {}", update.chunkX, update.chunkZ);
    }
    
    public MinimapType getActiveMinimapType() {
        return activeMinimapType;
    }
    
    public boolean isMinimapAvailable() {
        return activeMinimapType != MinimapType.NONE;
    }
    
    public boolean hasActiveMinimaps() {
        return isMinimapAvailable();
    }
    
    /**
     * Supported minimap types
     */
    public enum MinimapType {
        NONE,
        VOXELMAP,
        JOURNEYMAP,
        XAEROS
    }
}
