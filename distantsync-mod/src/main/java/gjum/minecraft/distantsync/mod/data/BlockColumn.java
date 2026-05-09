package gjum.minecraft.distantsync.mod.data;

import net.minecraft.world.level.biome.Biome;
import java.util.List;

/**
 * Represents a vertical column of blocks at a specific X,Z position
 */
public record BlockColumn(List<BlockInfo> layers, Biome biome, int light) {
}
