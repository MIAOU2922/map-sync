package gjum.minecraft.distantsync.mod.integrations.xaerosmap;

import gjum.minecraft.distantsync.mod.data.ChunkTile;

public class XaerosWorldMapHelper {
	public static boolean isXaerosWorldMapNotAvailable;

	static {
		try {
			Class.forName("xaero.map.WorldMap");
			isXaerosWorldMapNotAvailable = false;
		} catch (NoClassDefFoundError | ClassNotFoundException ignored) {
			isXaerosWorldMapNotAvailable = true;
		}
	}

	public static boolean isMapping() {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.isMapping();
	}

	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isXaerosWorldMapNotAvailable) return false;
		return XaerosWorldMapHelperReal.updateWithChunkTile(chunkTile);
	}
}
