package gjum.minecraft.distantsync.mod.integration;

import gjum.minecraft.distantsync.mod.DistantSyncMod;
import gjum.minecraft.distantsync.mod.data.BlockInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    
    // Cached methods to avoid repeated reflection lookups
    private Method getLevelWrapperMethod;
    private Method getAllTerrainDataMethod;
    
    // Track processed chunks to avoid duplicates
    private final Set<ChunkPos> processedLodChunks = ConcurrentHashMap.newKeySet();
    
    // Track if we've warned about level wrapper issues
    private boolean hasWarnedAboutLevelWrapper = false;
    
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
            
            // Log available methods to debug
            LOGGER.info("DH terrainRepo class: {}", terrainRepo.getClass().getName());
            LOGGER.info("DH worldProxy class: {}", worldProxy.getClass().getName());
            
            // Create terrain data cache (use createSoftCache, not getSoftCache)
            try {
                Method createSoftCacheMethod = terrainRepo.getClass().getMethod("createSoftCache");
                terrainDataCache = createSoftCacheMethod.invoke(terrainRepo);
                LOGGER.info("Terrain data cache created successfully");
            } catch (NoSuchMethodException e) {
                LOGGER.warn("createSoftCache() not available - will proceed without cache");
                terrainDataCache = null;
            }
            
            // Cache commonly used methods
            try {
                // Use getSinglePlayerLevel() directly (no parameters)
                getLevelWrapperMethod = worldProxy.getClass().getMethod("getSinglePlayerLevel");
                LOGGER.info("Found getSinglePlayerLevel method");
                
                // getAllTerrainDataAtChunkPos always takes cache parameter (can be null)
                getAllTerrainDataMethod = terrainRepo.getClass().getMethod(
                    "getAllTerrainDataAtChunkPos",
                    dhApiLevelWrapperClass,
                    int.class,
                    int.class,
                    Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache")
                );
                LOGGER.info("Found getAllTerrainDataAtChunkPos method");
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                LOGGER.error("Failed to find required DH API methods", e);
                available = false;
                dhInitialized = false;
                return;
            }
            
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
     * Try to get all LOD positions that DH currently has in memory/cache.
     * This is more efficient than checking every chunk individually.
     */
    @Nullable
    public java.util.Set<ChunkPos> getAllLoadedLodPositions(Level level) {
        if (!isInitialized()) {
            initializeDelayedApi();
            if (!isInitialized()) return null;
        }
        
        try {
            // Get level wrapper (use cached method if available)
            Object levelWrapper;
            if (getLevelWrapperMethod != null) {
                levelWrapper = getLevelWrapperMethod.invoke(worldProxy);
            } else {
                Method method = worldProxy.getClass().getMethod("getSinglePlayerLevel");
                levelWrapper = method.invoke(worldProxy);
            }
            
            if (levelWrapper == null) {
                LOGGER.debug("No level wrapper for getAllLoadedLodPositions");
                return null;
            }
            
            // Try to get all loaded positions from terrainRepo
            // This may vary by DH version, so we try multiple approaches
            
            // Approach 1: Try to get from cache directly
            try {
                if (terrainDataCache != null) {
                    Method getCachedPositionsMethod = terrainDataCache.getClass().getMethod("getCachedPositions");
                    Object positions = getCachedPositionsMethod.invoke(terrainDataCache);
                    if (positions instanceof java.util.Collection) {
                        java.util.Set<ChunkPos> result = new java.util.HashSet<>();
                        for (Object pos : (java.util.Collection<?>) positions) {
                            // Try to extract x,z from position object
                            // This is DH-specific and may need adjustment
                            LOGGER.debug("Found cached position: {}", pos);
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                LOGGER.debug("getCachedPositions method not available");
            }
            
            // Approach 2: Query the terrainRepo for a very large area
            LOGGER.info("Attempting to scan large area for loaded LOD chunks...");
            
            return null; // For now, return null and fall back to chunk-by-chunk scanning
            
        } catch (Exception e) {
            LOGGER.error("Error getting loaded LOD positions", e);
            return null;
        }
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
        
        // Safety checks
        if (getLevelWrapperMethod == null || getAllTerrainDataMethod == null) {
            LOGGER.warn("DH API methods not cached - cannot check LOD data");
            return false;
        }
        
        try {
            // Get level wrapper from worldProxy (getSinglePlayerLevel takes no parameters)
            Object levelWrapper = getLevelWrapperMethod.invoke(worldProxy);
            
            if (levelWrapper == null) {
                // This is normal for dimensions DH doesn't track (like Nether/End in some configs)
                return false;
            }
            
            // Try to get terrain data for this chunk (cache can be null)
            Object result = getAllTerrainDataMethod.invoke(
                terrainRepo,
                levelWrapper,
                pos.x,
                pos.z,
                terrainDataCache  // Can be null, DH handles it
            );
            
            // Check if result is success and has data
            // DhApiResult uses public fields, not methods
            Field successField = result.getClass().getField("success");
            boolean success = (boolean) successField.get(result);
            
            if (!success) {
                // Log why it failed - message field contains error details
                try {
                    Field messageField = result.getClass().getField("message");
                    Object message = messageField.get(result);
                    LOGGER.debug("Failed to get LOD for chunk {}: {}", pos, message);
                } catch (Exception e) {
                    LOGGER.debug("Failed to get LOD for chunk {} (no error details)", pos);
                }
                return false;
            }
            
            // Get payload field
            Field payloadField = result.getClass().getField("payload");
            Object data = payloadField.get(result);
            boolean hasData = data != null;
            
            if (hasData) {
                LOGGER.debug("Found LOD data for chunk {}", pos);
            }
            
            return hasData;
            
        } catch (java.lang.reflect.InvocationTargetException e) {
            // This wraps the actual exception thrown by the invoked method
            Throwable cause = e.getCause();
            if (cause != null) {
                if (!hasWarnedAboutLevelWrapper) {
                    LOGGER.warn("DH API call failed for chunk {}: {} - {}", pos, cause.getClass().getSimpleName(), cause.getMessage());
                    LOGGER.warn("This usually means DH is not fully initialized yet. Will suppress further warnings.");
                    hasWarnedAboutLevelWrapper = true;
                }
            }
            return false;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            if (!hasWarnedAboutLevelWrapper) {
                LOGGER.error("DH API method invocation error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                LOGGER.error("This may indicate an incompatible DH version. Will suppress further warnings.");
                hasWarnedAboutLevelWrapper = true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error checking LOD data for chunk {}: {}", pos, e.getClass().getName(), e);
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
            // Get level wrapper (getSinglePlayerLevel takes no parameters)
            Object levelWrapper = getLevelWrapperMethod.invoke(worldProxy);
            
            if (levelWrapper == null) {
                LOGGER.debug("No level wrapper available for level");
                return null;
            }
            
            // Get all terrain data at chunk position
            Object result = getAllTerrainDataMethod.invoke(
                terrainRepo,
                levelWrapper,
                pos.x,
                pos.z,
                terrainDataCache
            );
            
            // Check result - DhApiResult uses public fields
            Field successField = result.getClass().getField("success");
            boolean success = (boolean) successField.get(result);
            
            if (!success) {
                LOGGER.debug("Failed to get terrain data for chunk {}", pos);
                return null;
            }
            
            Field payloadField = result.getClass().getField("payload");
            Object dataArray = payloadField.get(result);
            
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
     * Extract raw LOD terrain data array from Distant Horizons.
     * This can be used with LodToChunkTileConverter to create ChunkTiles
     * and leverage MapSync's minimap integration infrastructure.
     * 
     * @return Raw terrain data array [x][z][columnData], or null if unavailable
     */
    @Nullable
    public Object getRawTerrainData(ChunkPos pos, Level level) {
        if (!isInitialized()) {
            initializeDelayedApi();
            if (!isInitialized()) return null;
        }
        
        try {
            // Get level wrapper (getSinglePlayerLevel takes no parameters)
            Object levelWrapper = getLevelWrapperMethod.invoke(worldProxy);
            
            if (levelWrapper == null) {
                return null;
            }
            
            // Get all terrain data at chunk position
            Object result = getAllTerrainDataMethod.invoke(
                terrainRepo,
                levelWrapper,
                pos.x,
                pos.z,
                terrainDataCache
            );
            
            // Check result - DhApiResult uses public fields
            Field successField = result.getClass().getField("success");
            boolean success = (boolean) successField.get(result);
            
            if (!success) {
                return null;
            }
            
            Field payloadField = result.getClass().getField("payload");
            return payloadField.get(result);
            
        } catch (Exception e) {
            LOGGER.debug("Error getting raw terrain data for chunk {}: {}", pos, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a BlockState is vegetation (plants/flowers) that should be ignored
     * for surface detection. Does NOT include terrain blocks like grass_block.
     */
    private boolean isVegetation(BlockState state) {
        if (state == null) return false;
        
        // Check if it's a liquid (water, lava) - these are NOT vegetation
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        
        // Get block name
        String blockName = state.getBlock().toString().toLowerCase();
        
        // Filter ONLY plants that grow on terrain, NOT terrain blocks themselves
        // Keep: grass_block, dirt, stone, sand, etc.
        // Remove: short_grass, tall_grass, flowers, etc.
        return blockName.contains("short_grass") || 
               blockName.contains("tall_grass") ||
               blockName.contains("flower") || 
               blockName.contains("sapling") ||
               blockName.contains("fern") ||
               blockName.contains("bush") ||
               blockName.contains("vine") ||
               blockName.contains("kelp") ||
               blockName.contains("seagrass") ||
               blockName.contains("lily") ||
               blockName.contains("mushroom") ||
               blockName.contains("fungus") ||
               blockName.contains("roots") ||
               blockName.contains("dead_bush") ||
               blockName.contains("dandelion") ||
               blockName.contains("poppy") ||
               blockName.contains("allium") ||
               blockName.contains("azure_bluet") ||
               blockName.contains("tulip") ||
               blockName.contains("orchid") ||
               blockName.contains("cornflower") ||
               blockName.contains("wither_rose");
    }
    
    /**
     * Parse DH terrain data array into our LodChunkData format
     * Extracts ALL layers of BlockStates for JourneyMap to calculate colors natively
     * Ignores vegetation like grass/flowers but keeps water layers
     */
    private LodChunkData parseLodData(ChunkPos pos, Object dataArray) throws Exception {
        int[][] heightMap = new int[16][16];
        @SuppressWarnings("unchecked")
        List<BlockInfo>[][] layersMap = new List[16][16]; // All layers per position
        Biome[][] biomeMap = new Biome[16][16];        Object[][] biomeMdMap = new Object[16][16]; // JourneyMap BiomeMD objects        
        // Access the 3D array structure: [x][z][columnData]
        Object[][][] data = (Object[][][]) dataArray;
        
        for (int x = 0; x < 16 && x < data.length; x++) {
            for (int z = 0; z < 16 && z < data[x].length; z++) {
                Object[] column = data[x][z];
                
                if (column == null || column.length == 0) {
                    heightMap[x][z] = 64; // Default sea level
                    layersMap[x][z] = new ArrayList<>();
                    layersMap[x][z].add(new BlockInfo(64, Blocks.AIR.defaultBlockState()));
                    biomeMap[x][z] = null;
                    continue;
                }
                
                // Extract ALL layers from the column, not just surface
                // Skip non-solid blocks (grass, flowers, etc.) to find real surface
                List<BlockInfo> layers = new ArrayList<>();
                Object surfaceDataPoint = null;
                Object firstNonAirDataPoint = null; // For biome extraction
                int surfaceY = 64;
                
                for (int i = 0; i < column.length; i++) {
                    Object dataPoint = column[i];
                    if (dataPoint == null) continue;
                    
                    try {
                        Field blockStateField = dataPoint.getClass().getDeclaredField("blockStateWrapper");
                        blockStateField.setAccessible(true);
                        Object blockStateWrapper = blockStateField.get(dataPoint);
                        
                        if (blockStateWrapper == null) continue;
                        
                        // Check if air
                        Method isAirMethod = blockStateWrapper.getClass().getMethod("isAir");
                        boolean isAir = (Boolean) isAirMethod.invoke(blockStateWrapper);
                        if (isAir) continue;
                        
                        // Get BlockState
                        Method getWrappedMethod = blockStateWrapper.getClass().getMethod("getWrappedMcObject");
                        Object wrappedBlockState = getWrappedMethod.invoke(blockStateWrapper);
                        
                        if (!(wrappedBlockState instanceof BlockState)) continue;
                        BlockState state = (BlockState) wrappedBlockState;
                        
                        // Get Y position
                        Field topYField = dataPoint.getClass().getDeclaredField("topYBlockPos");
                        topYField.setAccessible(true);
                        int y = topYField.getInt(dataPoint);
                        
                        // Track first non-air for biome extraction
                        if (firstNonAirDataPoint == null) {
                            firstNonAirDataPoint = dataPoint;
                        }
                        
                        // Add to layers - include ALL blocks (water, stone, etc.)
                        layers.add(new BlockInfo(y, state));
                        
                        // Find surface (first non-vegetation block, including water)
                        if (surfaceDataPoint == null) {
                            // Skip only vegetation (grass, flowers, saplings, etc.) but keep water/liquids
                            if (!isVegetation(state)) {
                                surfaceDataPoint = dataPoint;
                                surfaceY = y;
                            }
                        }
                    } catch (Exception e) {
                        // Skip problematic data points
                    }
                }
                
                // If no solid surface found, use first non-air layer
                if (surfaceDataPoint == null && !layers.isEmpty()) {
                    surfaceDataPoint = firstNonAirDataPoint;
                    surfaceY = layers.get(0).y();
                }
                
                // Store layers
                if (layers.isEmpty()) {
                    layers.add(new BlockInfo(64, Blocks.AIR.defaultBlockState()));
                }
                layersMap[x][z] = layers;
                heightMap[x][z] = surfaceY;
                
                // Log first extraction to verify
                if (x == 0 && z == 0 && !layers.isEmpty()) {
                    LOGGER.info("Chunk {} pos[0,0]: extracted {} layers, surface at Y={}, top block: {}", 
                        pos, layers.size(), surfaceY, layers.get(0).state());
                }
                
                // Extract biome from the first non-air data point (surface level)
                // This ensures we get the biome at the actual surface (water or terrain)
                if (firstNonAirDataPoint != null) {
                    
                    // Extract biome - try multiple methods since getWrappedMcObject() may return null for LOD data
                    try {
                        Field biomeField = firstNonAirDataPoint.getClass().getDeclaredField("biomeWrapper");
                        biomeField.setAccessible(true);
                        Object biomeWrapper = biomeField.get(firstNonAirDataPoint);
                        
                        if (biomeWrapper != null) {
                            // Try method 1: getWrappedMcObject()
                            try {
                                Method getWrappedBiomeMethod = biomeWrapper.getClass().getMethod("getWrappedMcObject");
                                Object wrappedBiome = getWrappedBiomeMethod.invoke(biomeWrapper);
                                
                                if (wrappedBiome instanceof Biome) {
                                    biomeMap[x][z] = (Biome) wrappedBiome;
                                    if (x == 0 && z == 0) {
                                        LOGGER.debug("Chunk {} pos[0,0]: extracted Biome via getWrappedMcObject: {}", pos, biomeMap[x][z]);
                                    }
                                }
                            } catch (Exception ignored) {}
                            
                            // Try method 2: getSerialString() if method 1 failed
                            if (biomeMap[x][z] == null) {
                                try {
                                    Method getSerialStringMethod = biomeWrapper.getClass().getMethod("getSerialString");
                                    String biomeSerial = (String) getSerialStringMethod.invoke(biomeWrapper);
                                    
                                    Minecraft minecraft = Minecraft.getInstance();
                                    if (biomeSerial != null && !biomeSerial.isEmpty() && minecraft != null && minecraft.level != null) {
                                        // Parse biome name from serial string (e.g., "minecraft:ocean")
                                        var biomeRegistry = minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
                                        
                                        // Parse namespace:path format
                                        String namespace = "minecraft";
                                        String path = biomeSerial;
                                        if (biomeSerial.contains(":")) {
                                            String[] parts = biomeSerial.split(":", 2);
                                            namespace = parts[0];
                                            path = parts[1];
                                        }
                                        
                                        var biomeKey = net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BIOME,
                                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path)
                                        );
                                        var biomeHolder = biomeRegistry.getHolder(biomeKey);
                                        if (biomeHolder.isPresent()) {
                                            biomeMap[x][z] = biomeHolder.get().value();
                                            // Log biome extraction for water blocks (DEBUG level to avoid spam)
                                            if (x == 0 && z == 0) {
                                                LOGGER.debug("Chunk {} pos[0,0]: extracted Biome via getSerialString: {} -> {}", pos, biomeSerial, biomeMap[x][z]);
                                            }
                                            
                                            // Create BiomeMD for JourneyMap color calculations
                                            try {
                                                Class<?> biomeMdClass = Class.forName("journeymap.client.model.block.BiomeMD");
                                                Method getMethod = biomeMdClass.getDeclaredMethod("get", Biome.class);
                                                biomeMdMap[x][z] = getMethod.invoke(null, biomeMap[x][z]);
                                            } catch (Exception biomeMdEx) {
                                                LOGGER.debug("Failed to create BiomeMD for chunk {} pos [{}, {}]: {}", 
                                                    pos, x, z, biomeMdEx.getMessage());
                                            }
                                        }
                                    }
                                } catch (Exception e2) {
                                    LOGGER.debug("Failed to extract Biome via getSerialString for chunk {} pos [{}, {}]: {}", 
                                        pos, x, z, e2.getMessage());
                                }
                            }
                        }
                        
                        // If still null, fallback will be handled in LodChunkMD
                        if (biomeMap[x][z] == null && x == 0 && z == 0) {
                            LOGGER.warn("Chunk {} pos[0,0]: No biome extracted, will use fallback", pos);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Failed to extract Biome for chunk {} pos [{}, {}]: {}", 
                            pos, x, z, e.getMessage());
                        biomeMap[x][z] = null;
                    }
                } else {
                    heightMap[x][z] = 64;
                    layersMap[x][z] = new ArrayList<>();
                    layersMap[x][z].add(new BlockInfo(64, Blocks.AIR.defaultBlockState()));
                    biomeMap[x][z] = null;
                    biomeMdMap[x][z] = null;
                }
            }
        }
        
        return new LodChunkData(pos, heightMap, layersMap, biomeMap, biomeMdMap);
    }
    
    /**

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
     * Now contains ALL layers, not just surface
     */
    public static class LodChunkData {
        public final ChunkPos pos;
        public final int[][] heightMap;        // [16][16] - Surface height
        public final List<BlockInfo>[][] layersMap; // [16][16][] - ALL layers per position
        public final Biome[][] biomeMap;      // [16][16] - Real Biomes for proper water coloring
        public final Object[][] biomeMdMap;   // [16][16] - JourneyMap BiomeMD for colors
        public final long timestamp;
        
        @SuppressWarnings("unchecked")
        public LodChunkData(ChunkPos pos, int[][] heightMap, List<BlockInfo>[][] layersMap, Biome[][] biomeMap, Object[][] biomeMdMap) {
            this.pos = pos;
            this.heightMap = heightMap;
            this.layersMap = layersMap;
            this.biomeMap = biomeMap;
            this.biomeMdMap = biomeMdMap;
            this.timestamp = System.currentTimeMillis();
        }
        
        /**
         * Create LOD data from a regular Minecraft chunk (fallback when DH data not available)
         */
        public static LodChunkData fromMinecraftChunk(LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            int[][] heightMap = new int[16][16];
            @SuppressWarnings("unchecked")
            List<BlockInfo>[][] layersMap = new List[16][16];
            Biome[][] biomeMap = new Biome[16][16];
            Object[][] biomeMdMap = new Object[16][16];
            
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    List<BlockInfo> layers = new ArrayList<>();
                    int height = chunk.getHeight();
                    
                    // Find the top non-air block
                    for (int y = height - 1; y >= chunk.getMinBuildHeight(); y--) {
                        BlockPos testPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                        BlockState state = chunk.getBlockState(testPos);
                        if (!state.isAir()) {
                            heightMap[x][z] = y;
                            layers.add(new BlockInfo(y, state));
                            break;
                        }
                    }
                    
                    if (layers.isEmpty()) {
                        layers.add(new BlockInfo(64, Blocks.AIR.defaultBlockState()));
                    }
                    layersMap[x][z] = layers;
                    
                    // Get biome
                    try {
                        var biome = chunk.getNoiseBiome(x >> 2, 64 >> 2, z >> 2);
                        biomeMap[x][z] = biome.value();
                        
                        // Create BiomeMD
                        try {
                            Class<?> biomeMdClass = Class.forName("journeymap.client.model.block.BiomeMD");
                            Method getMethod = biomeMdClass.getDeclaredMethod("get", Biome.class);
                            biomeMdMap[x][z] = getMethod.invoke(null, biomeMap[x][z]);
                        } catch (Exception ignored) {
                        }
                    } catch (Exception e) {
                        biomeMap[x][z] = null;
                        biomeMdMap[x][z] = null;
                    }
                }
            }
            
            return new LodChunkData(pos, heightMap, layersMap, biomeMap, biomeMdMap);
        }
    }
}
