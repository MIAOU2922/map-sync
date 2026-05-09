package gjum.minecraft.distantsync.mod.integrations.journeymap;

import gjum.minecraft.distantsync.mod.data.BlockColumn;
import gjum.minecraft.distantsync.mod.data.BlockInfo;
import gjum.minecraft.distantsync.mod.data.ChunkTile;
import gjum.minecraft.distantsync.mod.integration.DistantHorizonsAPI;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.chunk.ChunkMD;
import journeymap.client.model.map.MapType;
import journeymap.client.model.region.RegionCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static gjum.minecraft.distantsync.mod.Utils.getBiomeRegistry;
import static gjum.minecraft.distantsync.mod.Utils.mc;

public class JourneyMapHelperReal {
	private static final Logger logger = LoggerFactory.getLogger("DistantSync-JourneyMap");

	static boolean isMapping() {
		return JourneymapClient.getInstance().isMapping();
	}

	/**
	 * NEW: Render LOD data directly using JourneyMap's native rendering methods
	 * This bypasses our color extraction and lets JourneyMap calculate colors properly
	 */
	static boolean updateWithLodData(DistantHorizonsAPI.LodChunkData lodData, ResourceKey<Level> dimension) {
		if (!JourneymapClient.getInstance().isMapping()) return false;

		var renderController = JourneymapClient.getInstance().getChunkRenderController();
		if (renderController == null) return false;

		var chunkMd = new LodChunkMD(lodData, dimension);

		var rCoord = RegionCoord.fromChunkPos(
			FileHandler.getJMWorldDir(mc),
			MapType.day(dimension),
			chunkMd.getCoord().x,
			chunkMd.getCoord().z);

		final boolean renderedDay = renderWithDiagnostics(rCoord,
			MapType.day(dimension), chunkMd, "day", lodData.pos);
		final boolean renderedBiome = renderWithDiagnostics(rCoord,
			MapType.biome(dimension), chunkMd, "biome", lodData.pos);
		final boolean renderedTopo = renderWithDiagnostics(rCoord,
			MapType.topo(dimension), chunkMd, "topo", lodData.pos);

		if (!renderedDay || !renderedBiome || !renderedTopo) {
			logger.warn("LOD chunk render debug {} -> day={}, biome={}, topo={}",
				lodData.pos, renderedDay, renderedBiome, renderedTopo);
		}

		return renderedDay && renderedBiome && renderedTopo;
	}

	static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (!JourneymapClient.getInstance().isMapping()) return false;

		var renderController = JourneymapClient.getInstance().getChunkRenderController();
		if (renderController == null) return false;

		var chunkMd = new TileChunkMD(chunkTile);

		var rCoord = RegionCoord.fromChunkPos(
			FileHandler.getJMWorldDir(mc),
			MapType.day(chunkTile.dimension()),
			chunkMd.getCoord().x,
			chunkMd.getCoord().z);

		final boolean renderedDay = renderWithDiagnostics(rCoord,
			MapType.day(chunkTile.dimension()), chunkMd, "day", chunkTile.chunkPos());
		final boolean renderedBiome = renderWithDiagnostics(rCoord,
			MapType.biome(chunkTile.dimension()), chunkMd, "biome", chunkTile.chunkPos());
		final boolean renderedTopo = renderWithDiagnostics(rCoord,
			MapType.topo(chunkTile.dimension()), chunkMd, "topo", chunkTile.chunkPos());

		if (!renderedDay || !renderedBiome || !renderedTopo) {
			logger.warn("chunk render debug {} -> day={}, biome={}, topo={}",
				chunkTile.chunkPos(), renderedDay, renderedBiome, renderedTopo);
		}

		return renderedDay && renderedBiome && renderedTopo;
	}

	private static boolean renderWithDiagnostics(
		RegionCoord rCoord,
		MapType mapType,
		ChunkMD chunkMd,
		String mapName,
		ChunkPos chunkPos
	) {
		try {
			final boolean rendered = JourneymapClient.getInstance().getChunkRenderController().renderChunk(rCoord, mapType, chunkMd);
			return rendered;
		} catch (ChunkMD.ChunkMissingException e) {
			logger.error("Chunk missing for rendering {} at {}", mapName, chunkPos);
			return false;
		} catch (Exception t) {
			logger.error("Exception rendering {} at {}", mapName, chunkPos, t);
			return false;
		}
	}

	/**
	 * Custom LevelChunk that returns LOD biomes for correct color calculation
	 */
	private static class LodLevelChunk extends LevelChunk {
		private final DistantHorizonsAPI.LodChunkData lodData;

		public LodLevelChunk(Level level, DistantHorizonsAPI.LodChunkData lodData) {
			super(level, lodData.pos);
			this.lodData = lodData;
		}

		@Override
		public Holder<Biome> getNoiseBiome(int x, int y, int z) {
			// x, y, z are in 4x4x4 biome coordinates (>> 2)
			// Convert back to block coordinates
			int blockX = (x << 2) & 0xf;
			int blockZ = (z << 2) & 0xf;
			
			Biome biome = lodData.biomeMap[blockX][blockZ];
			if (biome != null && mc.level != null) {
				var biomeKey = getBiomeRegistry().getResourceKey(biome).orElse(null);
				if (biomeKey != null) {
					logger.debug("LodLevelChunk.getNoiseBiome({},{},{}) -> blockX={}, blockZ={}, biome={}", 
						x, y, z, blockX, blockZ, biomeKey.location());
					return mc.level.registryAccess()
						.lookupOrThrow(Registries.BIOME)
						.getOrThrow(biomeKey);
				}
			}
			
			// Fallback to plains
			logger.warn("LodLevelChunk.getNoiseBiome({},{},{}) -> PLAINS (no biome data)", x, y, z);
			return mc.level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.getOrThrow(Biomes.PLAINS);
		}
	}

	/**
	 * NEW: ChunkMD implementation for Distant Horizons LOD data
	 * Provides BlockState, biome, and height data directly from DH to JourneyMap
	 * This allows JourneyMap to use its native color calculation methods
	 */
	private static class LodChunkMD extends ChunkMD {
		private final DistantHorizonsAPI.LodChunkData lodData;
		private final ResourceKey<Level> dimension;

		public LodChunkMD(DistantHorizonsAPI.LodChunkData lodData, ResourceKey<Level> dimension) {
			super(new LodLevelChunk(mc.level, lodData));
			this.lodData = lodData;
			this.dimension = dimension;
		}

		@Override
		public boolean hasChunk() {
			return true;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			int x = pos.getX() & 0xf;
			int z = pos.getZ() & 0xf;
			int y = pos.getY();
			
			// Get all layers for this position
			var layers = lodData.layersMap[x][z];
			if (layers == null || layers.isEmpty()) {
				return Blocks.AIR.defaultBlockState();
			}
			
			// Find the correct layer for this Y coordinate
			// Layers are sorted by Y (topmost first)
			for (var layer : layers) {
				if (y >= layer.y()) {
					return layer.state();
				}
			}
			
			// Below all layers, return air
			return Blocks.AIR.defaultBlockState();
		}

		@Override
		public BlockState getChunkBlockState(BlockPos pos) {
			return getBlockState(pos);
		}

		@Override
		public boolean canBlockSeeTheSky(int localX, int y, int localZ) {
			// For LOD data, assume blocks at or above heightMap can see sky
			return y >= lodData.heightMap[localX][localZ];
		}

		@Override
		public int getSavedLightValue(int localX, int y, int localZ) {
			// For LOD data at surface level, assume full daylight
			// JourneyMap will adjust this based on its own lighting calculations
			int surfaceY = lodData.heightMap[localX][localZ];
			if (y >= surfaceY) {
				return 15; // Full daylight
			}
			return 0; // Dark below surface
		}

		@Override
		public int getPrecipitationHeight(BlockPos pos) {
			return getPrecipitationHeight(pos.getX(), pos.getZ());
		}

		@Override
		public int getPrecipitationHeight(int localX, int localZ) {
			int x = localX & 0xf;
			int z = localZ & 0xf;
			return lodData.heightMap[x][z];
		}

		@Override
		public int getHeight(BlockPos pos) {
			return getPrecipitationHeight(pos);
		}

		@Override
		public Holder<Biome> getBiomeHolder(BlockPos pos) {
			var biome = getBiome(pos);
			if (biome == null || mc.level == null) return null;
			var biomeKey = getBiomeRegistry().getResourceKey(biome).orElse(null);
			if (biomeKey == null) return null;
			return mc.level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.getOrThrow(biomeKey);
		}

		@Override
		public Biome getBiome(BlockPos pos) {
			int x = pos.getX() & 0xf;
			int z = pos.getZ() & 0xf;
			
			// Get the real biome from LOD data
			Biome biome = lodData.biomeMap[x][z];
			if (biome != null) {
				return biome;
			}
			
			// Fallback to plains if no biome data
			if (mc.level == null) return null;
			return mc.level.registryAccess()
				.registryOrThrow(Registries.BIOME)
				.getOrThrow(Biomes.PLAINS);
		}

		@Override
		public ResourceKey<Level> getDimension() {
			return dimension;
		}
	}

	private static class TileChunkMD extends ChunkMD {
		private final ChunkTile chunkTile;

		public TileChunkMD(ChunkTile chunkTile) {
			super(new LevelChunk(mc.level, chunkTile.chunkPos()));
			this.chunkTile = chunkTile;
		}

		@Override
		public boolean hasChunk() {
			return true;
		}

		private BlockColumn getCol(int x, int z) {
			int xic = x & 0xf;
			int zic = z & 0xf;
			return chunkTile.columns()[xic + zic * 16];
		}

		private BlockColumn getCol(BlockPos pos) {
			return getCol(pos.getX(), pos.getZ());
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			var column = getCol(pos.getX(), pos.getZ());
			if (column == null) return Blocks.AIR.defaultBlockState();
			var layers = column.layers();
			BlockInfo prevLayer = null;
			for (BlockInfo layer : layers) {
				if (layer.y() == pos.getY()) {
					return layer.state();
				}
				if (layer.y() < pos.getY()) {
					if (prevLayer == null) return Blocks.AIR.defaultBlockState();
					return prevLayer.state();
				}
				prevLayer = layer;
			}
			if (layers.isEmpty()) return Blocks.AIR.defaultBlockState();
			return layers.get(layers.size() - 1).state();
		}

		@Override
		public BlockState getChunkBlockState(BlockPos pos) {
			return getBlockState(pos);
		}

		@Override
		public boolean canBlockSeeTheSky(int localX, int y, int localZ) {
			return getSavedLightValue(localX, y, localZ) > 0;
		}

		@Override
		public int getSavedLightValue(int localX, int y, int localZ) {
			var column = getCol(localX, localZ);
			if (column == null) return 0;
			int light = column.light();
			if (light < 0 || light > 15) return 0;
			return light;
		}

		@Override
		public int getPrecipitationHeight(BlockPos pos) {
			return this.getPrecipitationHeight(pos.getX(), pos.getZ());
		}

		@Override
		public ResourceKey<Level> getDimension() {
			return chunkTile.dimension();
		}

		@Override
		public int getPrecipitationHeight(int localX, int localZ) {
			var column = getCol(localX, localZ);
			if (column == null || column.layers().isEmpty()) {
				return mc.level != null ? this.getMinY() : 0;
			}
			return column.layers().get(0).y();
		}

		@Override
		public int getHeight(BlockPos pos) {
			return this.getPrecipitationHeight(pos);
		}

		@Override
		public Holder<Biome> getBiomeHolder(BlockPos pos) {
			var biome = getBiome(pos);
			if (biome == null || mc.level == null) return null;
			var biomeKey = getBiomeRegistry().getResourceKey(biome).orElse(null);
			if (biomeKey == null) return null;
			return mc.level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.getOrThrow(biomeKey);
		}

		@Override
		public Biome getBiome(BlockPos pos) {
			var column = getCol(pos);
			if (column != null) return column.biome();
			return null;
		}
	}
}
