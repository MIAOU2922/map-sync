package gjum.minecraft.mapsync.common.integration;

import gjum.minecraft.mapsync.common.data.BlockColumn;
import gjum.minecraft.mapsync.common.data.ChunkTile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
import xaero.map.core.XaeroWorldMapCore;
import xaero.map.region.MapBlock;
import xaero.map.region.MapRegion;
import xaero.map.region.MapTile;
import xaero.map.region.MapTileChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static gjum.minecraft.mapsync.common.MapSyncMod.debugLog;
import static gjum.minecraft.mapsync.common.Utils.mc;
import static journeymap.client.model.map.MapType.Name.biome;

public class XaerosWorldMapHelperReal {
    private static Field worldClass;
    private static Method bottomY;

    static {
        try {

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

	static boolean isMapping() {
        try {
            boolean res = WorldMapSession.getCurrentSession().isUsable();
            debugLog("WorldMap session usable (?mapping): " + (res ? "true" : "false"));
            return res;
        } catch (Exception e) {
            debugLog("CurrentSession is null probably");
            return false;
        }
    }

	static boolean updateWithChunkTile(ChunkTile chunkTile) {

        if (isMapping()) {
            MapProcessor mapProcessor = WorldMapSession.getCurrentSession().getMapProcessor();

            int chunkX = chunkTile.x();
            int chunkZ = chunkTile.z();

            int tileChunkX = chunkX >> 2;
            int tileChunkZ = chunkZ >> 2;

            int rx = tileChunkX >> 3;
            int rz = tileChunkZ >> 3; // = 8 -> 8x8 chunk = 1 region
            int caveLayer = mapProcessor.getCurrentCaveLayer();

            MapRegion region = mapProcessor.getLeafMapRegion(caveLayer, rx, rz, false);
            if (region == null) { region = mapProcessor.getLeafMapRegion(caveLayer, rx, rz, true); }
            if (region == null) { return false; }

            int localTileChunkX = tileChunkX & 7;
            int localTileChunkZ = tileChunkZ & 7;
            MapTileChunk tileChunk = region.getChunk(localTileChunkX, localTileChunkZ); //local
            if (tileChunk == null) {
                tileChunk = new MapTileChunk(region, tileChunkX, tileChunkZ); //non-local
                region.setChunk(localTileChunkX, localTileChunkZ, tileChunk); //local
            }

            int tileX = chunkX & 3;
            int tileZ = chunkZ & 3;

            MapTile mapTile = mapProcessor.getTilePool().get(mapProcessor.getCurrentDimension(), tileX, tileZ);

            BlockColumn[] columns = chunkTile.columns();
            int worldBottomY = -64; //TODO: retrieve w reflection: mapProcessor.getWorld().method_31607()

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int idx = (z << 4) | x;           // column index
                    BlockColumn col = columns[idx];  // your data

                    BlockState topState   = col.layers().getLast().state();
                    int height                         = col.layers().getLast().y();
                    int topHeight                      = col.layers().size() - 1;
                    //var biomeRegistry = mc.level.registryAccess().get(Registries.BIOME).orElseThrow().value();
                    //ResourceKey<Biome> biomeKey = biomeRegistry.getResourceKey(col.biome()).orElseThrow();
                    byte light = (byte)col.light();
                    boolean glowing = (byte)col.light() > 0; // or some condition from your data
                    boolean cave = mapProcessor.getCurrentCaveLayer() == height;

                    // ——— write into MapBlock ———
                    MapBlock mb = mapTile.getBlock(x, z);
                    if (mb == null) { mb = new MapBlock(); }

                    mb.prepareForWriting(worldBottomY);
                    mb.write(topState, height, topHeight, null, light, glowing, cave);
                    mapTile.setBlock(x, z, mb);
                }
            }
            mapTile.setLoaded(true);
            mapTile.setWorldInterpretationVersion(MapTile.CURRENT_WORLD_INTERPRETATION_VERSION);
            // If cave info available
            // mapTile.setWrittenCave(caveStart, caveDepth);

            // Attach & queue render
            tileChunk.setTile(tileX, tileZ, mapTile, mapProcessor.getBlockStateShortShapeCache());
            tileChunk.setHasHadTerrain();
            tileChunk.setChanged(true);
            tileChunk.setLoadState((byte)2);
            tileChunk.setToUpdateBuffers(true); // will cause updateBuffers() + upload

            return true;
        } else {
            return false;
        }
	}
}