package gjum.minecraft.mapsync.forge;

import gjum.minecraft.mapsync.common.MapSyncMod;
import gjum.minecraft.mapsync.common.ModGui;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod("mapsync")
public class ForgeMapSyncMod extends MapSyncMod {
	public ForgeMapSyncMod(
		final FMLJavaModLoadingContext context
	) {
		context.getModEventBus().addListener((event) -> {
			context.registerExtensionPoint(
				ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(
					(minecraft, previousScreen) -> new ModGui(previousScreen)
				)
			);

			init();
		});
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public String getVersion() {
		return VERSION + "+forge";
	}

	@Override
	public boolean isDevMode() {
		return !FMLLoader.isProduction();
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		try {
			if (event.phase == TickEvent.Phase.START) {
				handleTick();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private final Collection<KeyMapping> keyMappings = new ArrayList<>();
	@Override
	public void registerKeyBinding(KeyMapping mapping) {
		this.keyMappings.add(mapping);
	}
	@SubscribeEvent
	public void registerBindings(RegisterKeyMappingsEvent event) {
		this.keyMappings.forEach(event::register);
	}
}
