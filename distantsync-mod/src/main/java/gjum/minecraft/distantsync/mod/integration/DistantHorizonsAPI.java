package gjum.minecraft.distantsync.mod.integration;

import gjum.minecraft.distantsync.mod.DistantSyncMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration with Distant Horizons LOD system.
 * Uses reflection to avoid hard dependency on DH.
 */
public class DistantHorizonsAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistantHorizonsAPI.class);
    
    private static DistantHorizonsAPI instance;
    private boolean available = false;
    private boolean dhInitialized = false;
    
    // Reflected DH API classes and fields
    private Class<?> dhApiClass;
    private Class<?> dhApiDelayedClass;
    private Object terrainRepo;
    private Object worldProxy;
    private Object terrainDataCache;
    private Class<?> dhApiResultClass;
    private Class<?> dhApiTerrainDataPointClass;
    private Class<?> dhApiLevelWrapperClass;
    private Class<?> dhApiWrapperFactoryClass;
    
    // Track processed chunks to avoid duplicates
    private final Set<ChunkPos> processedLodChunks = ConcurrentHashMap.newKeySet();
    
    private DistantHorizonsAPI() {
        detectDistantHorizons();
    }
    
    public static DistantHorizonsAPI getInstance() {
        if (instance == null) {
            instance = new DistantHorizonsAPI();
        }
        return instance;
    }
    
    private void detectDistantHorizons() {
        try {
            // Try to detect DH by looking for its main API class
            dhApiClass = Class.forName("com.seibel.distanthorizons.api.DhApi");
            LOGGER.info("Distant Horizons API detected!");
            
            // Get the DhApi.Delayed class which contains terrainRepo and worldProxy
            dhApiDelayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            
            // Get related classes
            dhApiResultClass = Class.forName("com.seibel.distanthorizons.api.objects.DhApiResult");
            dhApiTerrainDataPointClass = Class.forName("com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint");
            dhApiLevelWrapperClass = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper");
            dhApiWrapperFactoryClass = Class.forName("com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory");
            
            LOGGER.info("All DH API classes loaded successfully");
            
            available = true;
            LOGGER.info("Distant Horizons integration initialized - waiting for DH to initialize...");
            
        } catch (ClassNotFoundException e) {
            LOGGER.info("Distant Horizons not detected - integration will be inactive");
            available = false;
        } catch (Exception e) {
            LOGGER.error("Error initializing Distant Horizons integration", e);
            available = false;
        }
    }
    
    /**
     * Initialize DH API delayed fields (must be called after DH has initialized)
     */
    public void initializeDelayedApi() {
        if (!available || dhInitialized) return;
        
        try {
            // Access DhApi.Delayed static fields
            Field terrainRepoField = dhApiDelayedClass.getDeclaredField("terrainRepo");
            terrainRepoField.setAccessible(true);
            terrainRepo = terrainRepoField.get(null);
            
            Field worldProxyField = dhApiDelayedClass.getDeclaredField("worldProxy");
            worldProxyField.setAccessible(true);
            worldProxy = worldProxyField.get(null);
            
            if (terrainRepo == null || worldProxy == null) {
                LOGGER.warn("DH API delayed fields are still null - DH may not be initialized yet");
                return;
            }
            
            // Create a terrain data cache for better performance
            Method createCacheMethod = terrainRepo.getClass().getMethod("createSoftCache");
            terrainDataCache = createCacheMethod.invoke(terrainRepo);
            
            dhInitialized = true;
            LOGGER.info("Distant Horizons API fully initialized with terrainRepo and worldProxy");
            
        } catch (Exception e) {
            LOGGER.error("Error initializing DH delayed API", e);
        }
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public boolean isInitialized() {
        return available && dhInitialized;
    }
    
    /**
     * Check if a chunk position has LOD data available in Distant Horizons
     */
    public boolean hasLodData(ChunkPos pos, Level level) {
        if (!isInitialized()) {
            // Try to initialize if not done yet
            initializeDelayedApi();
            if (!isInitialized()) return false;
        }
        
        try {
            // Get level wrapper from worldProxy
            Method getLevelWrapperMethod = worldProxy.getClass().getMethod("getClientLevelWrapper", Object.class);
            Object levelWrapper = getLevelWrapperMethod.invoke(worldProxy, level);
            
            if (levelWrapper == null) {
                return false;
            }
            
            // Try to get terrain data for this chunk (just check first column)
            Method getAllTerrainDataMethod = terrainRepo.getClass().getMethod(
                "getAllTerrainDataAtChunkPos",
                dhApiLevelWrapperClass,
                int.class,
                int.class,
                terrainDataCache.getClass().getInterfaces()[0] // IDhApiTerrainDataCache
            );
            
            Object result = getAllTerrainDataMethod.invoke(
                terrainRepo,
                levelWrapper,
                pos.x,
                pos.z,
                terrainDataCache
            );
            
            // Check if result is success and has data
            Method isSuccessMethod = result.getClass().getMethod("isSuccess");
            boolean success = (boolean) isSuccessMethod.invoke(result);
            
            if (success) {
                Method getValueMethod = result.getClass().getMethod("getValue");
                Object data = getValueMethod.invoke(result);
                return data != null;
            }
            
            return false;
            
        } catch (Exception e) {
            LOGGER.debug("Error checking LOD data for chunk {}: {}", pos, e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract LOD chunk data from Distant Horizons for a given chunk position
     */
    @Nullable
    public LodChunkData extractLodData(ChunkPos pos, Level level) {
        if (!isInitialized()) {
            initializeDelayedApi();
            if (!isInitialized()) return null;
        }
        
        try {
            // Get level wrapper
            Method getLevelWrapperMethod = worldProxy.getClass().getMethod("getClientLevelWrapper", Object.class);
            Object levelWrapper = getLevelWrapperMethod.invoke(worldProxy, level);
            
            if (levelWrapper == null) {
                LOGGER.debug("No level wrapper available for level");
                return null;
            }
            
            // Get all terrain data at chunk position
            Method getAllTerrainDataMethod = terrainRepo.getClass().getMethod(
                "getAllTerrainDataAtChunkPos",
                dhApiLevelWrapperClass,
                int.class,
                int.class,
                terrainDataCache.getClass().getInterfaces()[0]
            );
            
            Object result = getAllTerrainDataMethod.invoke(
                terrainRepo,
                levelWrapper,
                pos.x,
                pos.z,
                terrainDataCache
            );
            
            // Check result
            Method isSuccessMethod = result.getClass().getMethod("isSuccess");
            boolean success = (boolean) isSuccessMethod.invoke(result);
            
            if (!success) {
                LOGGER.debug("Failed to get terrain data for chunk {}", pos);
                return null;
            }
            
            Method getValueMethod = result.getClass().getMethod("getValue");
            Object dataArray = getValueMethod.invoke(result);
            
            if (dataArray == null) {
                return null;
            }
            
            // Parse the 3D array: [x][z][columnData]
            return parseLodData(pos, dataArray);
            
        } catch (Exception e) {
            LOGGER.error("Error extracting LOD data for chunk {}", pos, e);
            return null;
        }
    }
    
    /**
     * Parse DH terrain data array into our LodChunkData format
     */
    private LodChunkData parseLodData(ChunkPos pos, Object dataArray) throws Exception {
        int[][] heightMap = new int[16][16];
        int[][] colorMap = new int[16][16];
        byte[][] biomeIds = new byte[16][16];
        
        // Access the 3D array structure: [x][z][columnData]
        Object[][][] data = (Object[][][]) dataArray;
        
        for (int x = 0; x < 16 && x < data.length; x++) {
            for (int z = 0; z < 16 && z < data[x].length; z++) {
                Object[] column = data[x][z];
                
                if (column == null || column.length == 0) {
                    heightMap[x][z] = 64; // Default sea level
                    colorMap[x][z] = 0x7F7F7F; // Gray
                    biomeIds[x][z] = 0;
                    continue;
                }
                
                // Get the top data point (first element in column)
                Object topDataPoint = column[0];
                
                if (topDataPoint != null) {
                    // Extract height
                    Field topYField = topDataPoint.getClass().getDeclaredField("topYBlockPos");
                    topYField.setAccessible(true);
                    heightMap[x][z] = topYField.getInt(topDataPoint);
                    
                    // Extract color from blockStateWrapper
                    try {
                        Field blockStateField = topDataPoint.getClass().getDeclaredField("blockStateWrapper");
                        blockStateField.setAccessible(true);
                        Object blockStateWrapper = blockStateField.get(topDataPoint);
                        
                        if (blockStateWrapper != null) {
                            // Get the actual BlockState from wrapper
                            Method getBlockStateMethod = blockStateWrapper.getClass().getMethod("getBlockState");
                            Object blockState = getBlockStateMethod.invoke(blockStateWrapper);
                            colorMap[x][z] = LodChunkData.getBlockColor((BlockState) blockState);
                        } else {
                            colorMap[x][z] = 0x7F7F7F;
                        }
                    } catch (Exception e) {
                        colorMap[x][z] = 0x7F7F7F;
                    }
                    
                    // Extract biome
                    try {
                        Field biomeField = topDataPoint.getClass().getDeclaredField("biomeWrapper");
                        biomeField.setAccessible(true);
                        Object biomeWrapper = biomeField.get(topDataPoint);
                        
                        if (biomeWrapper != null) {
                            // For now, use placeholder biome ID
                            biomeIds[x][z] = 0;
                        } else {
                            biomeIds[x][z] = 0;
                        }
                    } catch (Exception e) {
                        biomeIds[x][z] = 0;
                    }
                } else {
                    heightMap[x][z] = 64;
                    colorMap[x][z] = 0x7F7F7F;
                    biomeIds[x][z] = 0;
                }
            }
        }
        
        return new LodChunkData(pos, heightMap, colorMap, biomeIds);
    }
    
    /**
     * Register a chunk as processed to avoid duplicate processing
     */
    public void markChunkProcessed(ChunkPos pos) {
        processedLodChunks.add(pos);
    }
    
    /**
     * Check if a chunk has already been processed
     */
    public boolean isChunkProcessed(ChunkPos pos) {
        return processedLodChunks.contains(pos);
    }
    
    /**
     * Clear processed chunks (e.g., when changing dimensions)
     */
    public void clearProcessedChunks() {
        processedLodChunks.clear();
    }
    
    /**
     * LOD chunk data extracted from Distant Horizons
     */
    public static class LodChunkData {
        public final ChunkPos pos;
        public final int[][] heightMap; // [16][16]
        public final int[][] colorMap;  // [16][16] RGB colors
        public final byte[][] biomeIds; // [16][16]
        public final long timestamp;
        
        public LodChunkData(ChunkPos pos, int[][] heightMap, int[][] colorMap, byte[][] biomeIds) {
            this.pos = pos;
            this.heightMap = heightMap;
            this.colorMap = colorMap;
            this.biomeIds = biomeIds;
            this.timestamp = System.currentTimeMillis();
        }
        
        /**
         * Create LOD data from a regular Minecraft chunk (fallback when DH data not available)
         */
        public static LodChunkData fromMinecraftChunk(LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            int[][] heightMap = new int[16][16];
            int[][] colorMap = new int[16][16];
            byte[][] biomeIds = new byte[16][16];
            
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, 0, pos.getMinBlockZ() + z);
                    int height = chunk.getHeight();
                    
                    // Find the top non-air block
                    for (int y = height - 1; y >= chunk.getMinBuildHeight(); y--) {
                        BlockPos testPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        BlockState state = chunk.getBlockState(testPos);
                        if (!state.isAir()) {
                            heightMap[x][z] = y;
                            // Get approximate color from block
                            colorMap[x][z] = getBlockColor(state);
                            break;
                        }
                    }
                    
                    // Get biome
                    try {
                        var biome = chunk.getNoiseBiome(x >> 2, 64 >> 2, z >> 2);
                        biomeIds[x][z] = (byte) 0; // TODO: Get actual biome ID
                    } catch (Exception e) {
                        biomeIds[x][z] = 0;
                    }
                }
            }
            
            return new LodChunkData(pos, heightMap, colorMap, biomeIds);
        }
        
        public static int getBlockColor(BlockState state) {
            // Simplified color extraction - would need proper block color mapping
            return 0x7F7F7F; // Default gray
        }
    }
}
