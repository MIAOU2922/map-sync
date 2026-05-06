package gjum.minecraft.distantsync.mod;

/**
 * Represents LOD chunk data from Distant Horizons that needs to be
 * synchronized to minimap mods.
 */
public class LodChunkData {
    public final int chunkX;
    public final int chunkZ;
    public final int dimensionId;
    
    // Terrain data
    public final byte[][] heightMap;  // [x][z] height values
    public final int[][] colorMap;    // [x][z] color values (RGB)
    public final byte[][] biomeMap;   // [x][z] biome IDs (optional)
    
    // Metadata
    public final long timestamp;
    public final int lodLevel;        // LOD detail level (0 = highest detail)
    
    public LodChunkData(int chunkX, int chunkZ, int dimensionId, 
                        byte[][] heightMap, int[][] colorMap, byte[][] biomeMap,
                        int lodLevel) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimensionId = dimensionId;
        this.heightMap = heightMap;
        this.colorMap = colorMap;
        this.biomeMap = biomeMap;
        this.timestamp = System.currentTimeMillis();
        this.lodLevel = lodLevel;
    }
    
    /**
     * Get the height at a specific position within the chunk
     */
    public int getHeight(int x, int z) {
        if (heightMap == null || x < 0 || z < 0 || x >= heightMap.length || z >= heightMap[0].length) {
            return 0;
        }
        return heightMap[x][z] & 0xFF; // Convert to unsigned
    }
    
    /**
     * Get the color at a specific position within the chunk
     */
    public int getColor(int x, int z) {
        if (colorMap == null || x < 0 || z < 0 || x >= colorMap.length || z >= colorMap[0].length) {
            return 0xFFFFFF; // Default white
        }
        return colorMap[x][z];
    }
    
    /**
     * Get the biome ID at a specific position within the chunk
     */
    public byte getBiome(int x, int z) {
        if (biomeMap == null || x < 0 || z < 0 || x >= biomeMap.length || z >= biomeMap[0].length) {
            return 0;
        }
        return biomeMap[x][z];
    }
    
    @Override
    public String toString() {
        return String.format("LodChunkData[%d, %d, dim=%d, lod=%d]", 
            chunkX, chunkZ, dimensionId, lodLevel);
    }
}
