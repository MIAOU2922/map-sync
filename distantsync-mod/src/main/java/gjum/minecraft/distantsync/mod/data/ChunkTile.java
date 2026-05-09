package gjum.minecraft.distantsync.mod.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Represents terrain data for a 16x16 chunk
 */
public record ChunkTile(
    int x,
    int z,
    ResourceKey<Level> dimension,
    BlockColumn[] columns, // 256 columns (16x16)
    long timestamp
) {
    public ChunkPos chunkPos() {
        return new ChunkPos(x, z);
    }
}
