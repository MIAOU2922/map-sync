package gjum.minecraft.distantsync.mod.integrations.journeymap;

import gjum.minecraft.distantsync.mod.data.ChunkTile;
import gjum.minecraft.distantsync.mod.integration.DistantHorizonsAPI;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.regex.Pattern;

public class JourneyMapHelper {
	public static boolean isJourneyMapNotAvailable;

	static {
		try {
			Class<?> jmClient = Class.forName("journeymap.client.JourneymapClient");
			String version = null;
			try {
				// Try to get a version field or method
				try {
					version = (String) jmClient.getDeclaredField("FULL_VERSION").get(null);
				} catch (NoSuchFieldException e) { }
			} catch (Exception ignored) {}

			if (version != null) {
				// Compare version strings as needed, e.g., "6.0.0"
				if (!Pattern.compile("6\\.\\d+\\.\\d+").matcher(version).find()) {
					isJourneyMapNotAvailable = true;
					System.err.println("Please update JourneyMap to at least 6.0.0 (found " + version + ")");
				} else {
					isJourneyMapNotAvailable = false;
				}
			}
		} catch (NoClassDefFoundError | ClassNotFoundException ignored) {
			isJourneyMapNotAvailable = true;
		}
	}

	public static boolean isMapping() {
		if (isJourneyMapNotAvailable) return false;
		return JourneyMapHelperReal.isMapping();
	}

	/**
	 * NEW: Render LOD data directly using JourneyMap's native methods
	 * This bypasses color extraction and lets JourneyMap calculate everything properly
	 */
	public static boolean updateWithLodData(DistantHorizonsAPI.LodChunkData lodData, ResourceKey<Level> dimension) {
		if (isJourneyMapNotAvailable) return false;
		return JourneyMapHelperReal.updateWithLodData(lodData, dimension);
	}

	public static boolean updateWithChunkTile(ChunkTile chunkTile) {
		if (isJourneyMapNotAvailable) return false;
		return JourneyMapHelperReal.updateWithChunkTile(chunkTile);
	}
}
