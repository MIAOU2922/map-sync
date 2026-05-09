package gjum.minecraft.distantsync.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for DistantSync mod
 */
public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("DistantSync-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "distantsync.json");
    
    // Default values
    public int scanRadius = 256;           // Maximum distance to scan for LOD chunks
    public int chunksPerTick = 50;         // Number of chunks to process per tick
    public int checkIntervalMs = 500;      // How often to check for new LOD chunks (milliseconds)
    
    /**
     * Load configuration from file, or create default if not exists
     */
    public static Config load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                Config config = GSON.fromJson(json, Config.class);
                LOGGER.info("Loaded DistantSync config: scanRadius={}, chunksPerTick={}, checkIntervalMs={}", 
                    config.scanRadius, config.chunksPerTick, config.checkIntervalMs);
                return config;
            } else {
                Config config = new Config();
                config.save();
                LOGGER.info("Created default DistantSync config: scanRadius={}, chunksPerTick={}, checkIntervalMs={}", 
                    config.scanRadius, config.chunksPerTick, config.checkIntervalMs);
                return config;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            return new Config();
        }
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
            LOGGER.info("Saved DistantSync config");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
