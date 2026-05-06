package gjum.minecraft.mapsync.mod.sync;

import gjum.minecraft.mapsync.mod.MapSyncMod;
import gjum.minecraft.mapsync.mod.config.ServerConfig;
import gjum.minecraft.mapsync.mod.data.GameAddress;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(GameContext.class);

	private final GameAddress gameAddress;
	private final ServerConfig gameConfig;
	private final SyncConnections syncConnections;

	private GameContext(
		final @NotNull GameAddress gameAddress,
		final @NotNull ServerConfig gameConfig
	) {
		this.gameAddress = Objects.requireNonNull(gameAddress);
		this.gameConfig = Objects.requireNonNull(gameConfig);
		this.syncConnections = new SyncConnections(this);
	}

	public void shutdown() {
		this.syncConnections.closeAll(true);
		if (DIMENSION_STATE.getAndSet(this, null) instanceof final DimensionState dimensionState) {
			dimensionState.shutDown();
		}
	}

	public @NotNull GameAddress getGameAddress() {
		return this.gameAddress;
	}

	public @NotNull ServerConfig getGameConfig() {
		return this.gameConfig;
	}

	public @NotNull SyncConnections getSyncConnections() {
		return this.syncConnections;
	}

	// ============================================================
	// Dimension State
	// ============================================================

	private volatile DimensionState dimensionState = null;
	private static final VarHandle DIMENSION_STATE; static {
		try {
			DIMENSION_STATE = MethodHandles.lookup().findVarHandle(GameContext.class, "dimensionState", DimensionState.class);
		}
		catch (final ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	public Optional<DimensionState> getDimensionState() {
		return Optional.ofNullable(this.dimensionState);
	}

	// ============================================================
	// Event Hooks
	// ============================================================

	private static volatile GameContext instance = null;
	private static final VarHandle INSTANCE; static {
		try {
			INSTANCE = MethodHandles.lookup().findStaticVarHandle(GameContext.class, "instance", GameContext.class);
		}
		catch (final ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	public static Optional<GameContext> get() {
		return Optional.ofNullable(instance);
	}

	public static void initEvents() {
		NeoForge.EVENT_BUS.register(new GameContextEventHandler());
	}

	private static class GameContextEventHandler {
		@SubscribeEvent
		public void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
			GameContext gameContext = null;
			try {
				if (!(Minecraft.getInstance().getCurrentServer() instanceof final ServerData serverData)) {
					LOGGER.error("Connection doesn't have server data yet... backing out");
					return;
				}
				final GameAddress gameAddress; {
					final String ip = serverData.ip;
					try {
						gameAddress = new GameAddress(ip);
					}
					catch (final Exception e) {
						LOGGER.error("Weirdly could not parse {} as a valid game address... backing out", ip, e);
						return;
					}
				}
				final ServerConfig gameConfig;
				try {
					gameConfig = ServerConfig.load(gameAddress);
				}
				catch (final Exception e) {
					LOGGER.error("Could not load game config for {}... backing out", gameAddress, e);
					return;
				}
				gameContext = new GameContext(
					gameAddress,
					gameConfig
				);
			}
			finally {
				if (INSTANCE.getAndSet(gameContext) instanceof final GameContext previous) {
					previous.shutdown();
				}
			}

			if (instance instanceof final GameContext context) {
				MapSyncMod.handleGameConnection(Minecraft.getInstance(), context);
			}
		}

		@SubscribeEvent
		public void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
			if (INSTANCE.getAndSet((Object) null) instanceof final GameContext context) {
				context.shutdown();
			}
		}

		@SubscribeEvent
		public void onLevelLoad(LevelEvent.Load event) {
			if (!(instance instanceof final GameContext gameContext)) {
				return;
			}
			if (event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
				final var dimensionState = new DimensionState(
					gameContext.getGameAddress(),
					level.dimension()
				);
				if (DIMENSION_STATE.getAndSet(gameContext, dimensionState) instanceof final DimensionState previous) {
					previous.shutDown();
				}
				MapSyncMod.handleDimensionChange(net.minecraft.client.Minecraft.getInstance(), level, gameContext);
			}
		}
	}
}

