package gjum.minecraft.mapsync.common;

import gjum.minecraft.mapsync.common.config.ServerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gjum.minecraft.mapsync.common.MapSyncMod.getMod;

public class ModGui extends Screen {
	final Screen parentScreen;

	ServerConfig serverConfig = getMod().getServerConfig();

	int innerWidth = 300;
	int left;
	int right;
	int top;

	int centerX = width / 2;
	int centerY = width / 2;

	EditBox syncServerAddressField;
	Button syncServerConnectBtn;

	public ModGui(Screen parentScreen) {
		super(Component.literal("Map-Sync"));
		this.parentScreen = parentScreen;
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		init();
	}

	@Override
	protected void init() {
		try {
			MapSyncMod.logger.info("ModGui.init() called; width={}, height={}", width, height);

			left = width / 2 - innerWidth / 2;
			right = width / 2 + innerWidth / 2;
			top = height / 3;

            centerX = width / 2;
            centerY = width / 2;

            int buttonWidth = 100;
            int buttonHeight = 20;

			clearWidgets();

			addRenderableWidget(
				Button.builder(Component.literal("Close"), (button) -> minecraft.setScreen(parentScreen))
					.bounds(centerX - (buttonWidth / 2), centerY, buttonWidth, buttonHeight)
					.build()
			);

			if (serverConfig != null) {
				addWidget(syncServerAddressField = new EditBox(font,
						left,
						top + 40,
						innerWidth - 110, 20,
						Component.literal("Sync Server Address")));
				syncServerAddressField.setMaxLength(256);
				syncServerAddressField.setValue(String.join(" ",
						serverConfig.getSyncServerAddresses()));

				addRenderableWidget(
					syncServerConnectBtn = Button.builder(Component.literal("Connect"), this::connectClicked)
						.bounds(right - 100, top + 40, 100, 20)
						.build()
				);

				
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void connectClicked(Button btn) {
		try {
			if (syncServerAddressField == null) return;
			var addresses = List.of(syncServerAddressField.getValue().split("[^-_.:A-Za-z0-9]+"));
			serverConfig.setSyncServerAddresses(addresses);
			getMod().shutDownSyncClients();
			getMod().getSyncClients();
			btn.active = false;
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	@Override
	public void render(@NotNull GuiGraphics guiGraphics, int i, int j, float f) {
		
		try {
			// wait for init() to finish
			if (syncServerAddressField == null) return;
			if (syncServerConnectBtn == null) return;
			super.render(guiGraphics, i, j, f);

			guiGraphics.drawCenteredString(font, title, centerX, top, 0xFFFFFFFF);
			syncServerAddressField.render(guiGraphics, i, j, f);
			syncServerConnectBtn.render(guiGraphics, j, j, i);

			var dimensionState = getMod().getDimensionState();
			if (dimensionState != null) {
				String counterText = String.format(
						"In dimension %s, received %d chunks, rendered %d, rendering %d",
						dimensionState.dimension.registry(),
						dimensionState.getNumChunksReceived(),
						dimensionState.getNumChunksRendered(),
						dimensionState.getRenderQueueSize()
				);
				guiGraphics.drawCenteredString(font, counterText, centerX, syncServerAddressField.getY() - 20, 0xFF888888);
			}

			int numConnected = 0;
			int msgY = syncServerAddressField.getY() - 40;
			var syncClients = getMod().getSyncClients();
			for (var client : syncClients) {
				int statusColor;
				String statusText;
				if (client.isEncrypted()) {
					numConnected++;
					statusColor = 0xFF008800;
					statusText = "Connected";
				} else if (client.getError() != null) {
					statusColor = 0xFFff8888;
					statusText = client.getError();
				} else {
					statusColor = 0xFFffffff;
					statusText = "Connecting...";
				}
				statusText = client.address + "  " + statusText;
				guiGraphics.drawString(font, statusText, left, msgY, statusColor);
				msgY += 10;
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
