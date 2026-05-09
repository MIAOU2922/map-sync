package gjum.minecraft.distantsync.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

public class Utils {
    public static final Minecraft mc = Minecraft.getInstance();

    public static Registry<Biome> getBiomeRegistry() {
        return Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME);
    }
}
