package gjum.minecraft.distantsync.mod.data;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a block at a specific Y coordinate
 */
public record BlockInfo(int y, BlockState state) {
}
