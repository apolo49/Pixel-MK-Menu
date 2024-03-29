package io.github.pixelmk.pixelmkmenu.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.util.concurrent.Runnables;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.pixelmk.pixelmkmenu.PixelMKMenu;
import io.github.pixelmk.pixelmkmenu.PixelMKMenuClient;
import io.github.pixelmk.pixelmkmenu.compat.PixelMKMenuCompat;
import io.github.pixelmk.pixelmkmenu.controls.ActionInstance;
import io.github.pixelmk.pixelmkmenu.controls.ButtonAction;
import io.github.pixelmk.pixelmkmenu.controls.ButtonMute;
import io.github.pixelmk.pixelmkmenu.controls.ButtonPanel;
import io.github.pixelmk.pixelmkmenu.controls.ConnectToButton;
import io.github.pixelmk.pixelmkmenu.controls.GuiButtonMainMenu;
import io.github.pixelmk.pixelmkmenu.controls.ModUpdateIcon;
import io.github.pixelmk.pixelmkmenu.controls.ScreenType;
import io.github.pixelmk.pixelmkmenu.event.AddModButtonsEvent;
import io.github.pixelmk.pixelmkmenu.fx.RenderTargetProxy;
import io.github.pixelmk.pixelmkmenu.fx.ScreenTransition;
import io.github.pixelmk.pixelmkmenu.fx.transitions.Fade;
import io.github.pixelmk.pixelmkmenu.helpers.FBO;
import io.github.pixelmk.pixelmkmenu.helpers.ForgeHelper;
import io.github.pixelmk.pixelmkmenu.helpers.PixelMKMenuConfig;
import io.github.pixelmk.pixelmkmenu.helpers.PixelMKMenuSoundEvents;
import io.github.pixelmk.pixelmkmenu.helpers.PixelMKMusicManager;
import io.github.pixelmk.pixelmkmenu.interfaces.IPanoramaRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.versions.mcp.MCPVersion;

public class PixelMKMenuScreen extends TitleScreen {

	public static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/background.png");
	private int updateCounter;
	private ServerStatusPinger serverPinger;
	private ButtonPanel buttonPanelLeft;
	private ButtonPanel buttonPanelRight;
	private GuiButtonMainMenu btnAboutForgeMods;
	private Tooltip modpackTooltip;
	private Tooltip BrandingsTooltip;
	private GuiButtonMainMenu btnConnectToServer;
	public static ButtonMute btnMute;
	private FBO[] transitionFBOs = new FBO[2];
	private ScreenTransition defaultTransition = (ScreenTransition) new Fade();
	private List<ScreenTransition> availableTransitions = new ArrayList<ScreenTransition>();
	private List<ScreenTransition> activeTransitions = new ArrayList<ScreenTransition>();
	private ScreenTransition transition = null;
	private boolean fboEnabled;
	private int transitionFrames;
	private int currentFBO;
	private Boolean previousWasScreen;
	private Screen transitionScreen;
	private float transitionPct;
	private long transitionBeginTime;
	private Float transitionRate;
	private boolean swapped;
	private Screen swappedScreen;
	private boolean drawingChildScreen;
	private boolean handleRecursion;
	private RenderTargetProxy proxy = new RenderTargetProxy();
	public static final Music MENU_MUSIC = new Music(PixelMKMenuSoundEvents.MUSIC_MENU.get(), 20, 600, true);

	/**
	 * Creates an instance of the screen, initialises the <code>ForgeHelper</code>,
	 * updates the server info before starting, changes the minecraft edition logo
	 *
	 * @param fade
	 */
	public PixelMKMenuScreen(boolean fade) {
		this.minecraft = Minecraft.getInstance();
		ForgeHelper.init();
		updateServerInfo();
		IPanoramaRenderer previousRenderer = PixelMKMenu.getPanoramaRenderer();
		if (previousRenderer != null)
			this.updateCounter = previousRenderer.getUpdateCounter();
		MINECRAFT_EDITION = new ResourceLocation("pixelmkmenu", "textures/gui/edition.png");
	}

	public PixelMKMenuScreen() {
		this(true);
	}

	/**
	 * <p>Initialses the screen.</p>
	 * <p>Clears all renderables from parent init and children,
	 * checks to see if it is a modpack, creates the brandings,
	 * And sets up the screen</p>
	 */
	@Override
	protected void init() {
		super.init();
		this.renderables.clear();
		this.children().clear();
		if (PixelMKMenuClient.BUTTON_MANAGER.getButtons().isEmpty())
			this.addDefaultButtons();
		else
			PixelMKMenuClient.BUTTON_MANAGER.getButtons().forEach(button -> {
				this.addRenderableWidget(button).setup(this);
			});
		if (PixelMKMenuClient.inModpack) {
			this.modpackTooltip = new Tooltip(PixelMKMenuClient.instance.getModpackName() + " version "
					+ PixelMKMenuClient.instance.getModpackVersion(), PixelMKMenuClient.instance.getModpackName(), 2,
					2, -7);
			this.addRenderableWidget(this.modpackTooltip);
		}

		this.BrandingsTooltip = new Tooltip(ForgeHelper.getBrandings(), "Minecraft " + MCPVersion.getMCVersion(), 2,
				this.height - 10, 200, 0, 10);
		this.addRenderableWidget(this.BrandingsTooltip);

		this.minecraft.setConnectedToRealms(false);

		int txtWidth = this.font.width(COPYRIGHT_TEXT);
		int leftPos = this.width - txtWidth - 3;
		this.addRenderableWidget(
				new PlainTextButton(leftPos, this.height - 10, txtWidth, 10, COPYRIGHT_TEXT, (p_211790_) -> {
					this.minecraft.setScreen(new WinScreen(false, Runnables.doNothing()));
				}, this.font));
		this.fboEnabled = FBO.detectFBOCapabilities();
		if (this.fboEnabled) {
			PixelMKMenu.LOGGER.info("FBO capability is supported, enabling transitions.");
			this.transitionFBOs[0] = new FBO();
			this.transitionFBOs[1] = new FBO();
		}
		registerTransition((ScreenTransition) new Fade());
		updateRegisteredTransitions();
		ObfuscationReflectionHelper.setPrivateValue(TitleScreen.class, this,
				ModUpdateIcon.init(this, this.btnAboutForgeMods, this.buttonPanelRight), "modUpdateNotification");
	}

	private void registerTransition(ScreenTransition screenTransition) {
		this.availableTransitions.forEach((transition) -> {
			if (transition != null && transition.getClass().equals(screenTransition.getClass()))
				return;
		});
		this.availableTransitions.add(screenTransition);
	}

	public List<ScreenTransition> getAvailableTransitions() {
		return this.availableTransitions;
	}

	private void addTransition(ScreenTransition transition) {
		this.activeTransitions.add(transition);
	}

	public void updateRegisteredTransitions() {
		this.activeTransitions.clear();
		for (ScreenTransition transition : this.availableTransitions) {
			addTransition(transition);
		}
	}

	private ScreenTransition createTransition() {
		return this.defaultTransition;
	}

	/**
	 * Adds the button panels, initialises them and adds the mute button.
	 */
	private void addDefaultButtons() {

		if (this.buttonPanelLeft == null) {
			this.buttonPanelLeft = new ButtonPanel(ButtonPanel.AnchorType.BottomLeft, 12,
					20, 150, 100, 16, this.width, this.height, "left", new ActionInstance(ButtonAction.NONE, null));
			this.buttonPanelRight = new ButtonPanel(ButtonPanel.AnchorType.BottomRight, 12,
					20, 150, 100, 16, this.width, this.height, "right", new ActionInstance(ButtonAction.NONE, null));
			initPanelButtons();
		} else {
			this.buttonPanelLeft.updatePosition(this.width, this.height);
			this.buttonPanelRight.updatePosition(this.width, this.height);
		}
		this.addRenderableWidget(this.buttonPanelLeft);
		this.addRenderableWidget(this.buttonPanelRight);

		btnMute = new ButtonMute(this.width - 24, 4);
		this.addRenderableWidget(btnMute);
	}

	/**
	 * Checks to see if demo world is present, if it is returns <code>true</code>
	 * @return
	 */
	private boolean checkDemoWorldPresence() {
		try {
			LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.minecraft
					.getLevelSource().createAccess("Demo_World");

			boolean flag;
			try {
				flag = levelstoragesource$levelstorageaccess.getSummary() != null;
			} catch (Throwable throwable1) {
				if (levelstoragesource$levelstorageaccess != null) {
					try {
						levelstoragesource$levelstorageaccess.close();
					} catch (Throwable throwable) {
						throwable1.addSuppressed(throwable);
					}
				}

				throw throwable1;
			}

			if (levelstoragesource$levelstorageaccess != null) {
				levelstoragesource$levelstorageaccess.close();
			}

			return flag;
		} catch (IOException ioexception) {
			SystemToast.onWorldAccessFailure(this.minecraft, "Demo_World");
			PixelMKMenu.LOGGER.warn("Failed to read demo world data", (Throwable) ioexception);
			return false;
		}
	}

	/**
	 * <p>Create the panel buttons and assign to correct panels.</p>
	 * <p><code>AddModButtonsEvent</code> is posted from here.</p>
	 */
	protected void initPanelButtons() {
		if (this.minecraft.isDemo()) {
			this.buttonPanelLeft.addButton("menu.playdemo",
					new ActionInstance(ButtonAction.DEMO_WORLD, this.checkDemoWorldPresence()));
		} else {
			this.buttonPanelLeft.addButton("menu.singleplayer",
					new ActionInstance(ButtonAction.OPEN_GUI, ScreenType.SINGLEPLAYER));
			this.buttonPanelRight.addButton("menu.multiplayer",
					new ActionInstance(ButtonAction.OPEN_GUI, ScreenType.MULTIPLAYER));
			String buttonText = "Connect to "
					+ ((PixelMKMenuConfig.CLIENT.customServerName.get() != null
							&& PixelMKMenuConfig.CLIENT.customServerName.get().length() > 0)
									? PixelMKMenuConfig.CLIENT.customServerName.get()
									: "...");
			this.buttonPanelLeft.addButton(new ConnectToButton(buttonText, this));
		}
		this.buttonPanelLeft.addButton("menu.options",
				new ActionInstance(ButtonAction.OPEN_GUI, ScreenType.OPTIONS));
		this.buttonPanelLeft.addButton("menu.quit", new ActionInstance(ButtonAction.QUIT, null));
		if (PixelMKMenuCompat.isAnyModLoaded()) {
			PixelMKMenuCompat.addButtons(this.buttonPanelRight);
		}
		MinecraftForge.EVENT_BUS.post(new AddModButtonsEvent(this.buttonPanelRight));
		this.btnAboutForgeMods = this.buttonPanelRight.addButton("fml.menu.mods",
				new ActionInstance(ButtonAction.OPEN_GUI, ScreenType.MODS));
		this.buttonPanelRight.addButton("options.language",
				new ActionInstance(ButtonAction.OPEN_GUI, ScreenType.LANGUAGE));
	}

	/**
	 * On every tick update the counter, update server info and tick the music manager.
	 */
	@Override
	public void tick() {
		super.tick();
		++this.updateCounter;
		if (this.minecraft.screen != this)
			return;
		this.updateServerInfo();
		if (this.serverPinger != null)
			this.serverPinger.tick();
	}

	/**
	 * Update server info and server button info.
	 */
	private void updateServerInfo() {
		if (PixelMKMenuConfig.CLIENT.customServerIP.get() != null &&
				!PixelMKMenuConfig.CLIENT.customServerIP.get().isEmpty() && this.btnConnectToServer != null) {
			String buttonText = "Connect to "
					+ ((PixelMKMenuConfig.CLIENT.customServerName.get() != null
							&& PixelMKMenuConfig.CLIENT.customServerName.get().length() > 0)
									? PixelMKMenuConfig.CLIENT.customServerName.get()
									: "...");
			this.btnConnectToServer.setMessage(new TranslatableComponent(buttonText));
		}
	}

	/**
	 * Render the screen by first updating buttons in the button panel and then calling the supermethod.
	 */
	@Override
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		this.buttonPanelLeft.updateButtons(this.updateCounter, partialTicks, mouseX, mouseY);
		this.buttonPanelRight.updateButtons(this.updateCounter, partialTicks, mouseX, mouseY);
		super.render(pose, mouseX, mouseY, partialTicks);
	}

	@SubscribeEvent
	public void onRenderGui(ScreenEvent event) {
		Screen current = event.getScreen();
		if (current == null && this.minecraft.options.keyPlayerList.isDown())
			return;
		if (!this.fboEnabled)
			return;
		if (this.minecraft.level != null) {
			this.transitionFBOs[0].dispose();
			this.transitionFBOs[1].dispose();
			return;
		}
		if (checkBeginTransition(current)) {
			if (this.transitionFrames > 1) {
				this.currentFBO = 1 - this.currentFBO;
				this.previousWasScreen = (this.transitionScreen != null);
				this.transitionPct = 0.0f;
				this.transitionBeginTime = Util.getMillis();
				this.transitionRate = PixelMKMenuConfig.CLIENT.transitionRate.get();
			}
			this.transitionScreen = current;
			this.transitionFrames = 0;
			this.transition = createTransition();
			PixelMKMenu.LOGGER.info("Transition created");
		} else {
			this.transitionFrames++;
			if (this.transition != null) {
				long deltaTime = Util.getMillis() - this.transitionBeginTime;
				float pct = (float) deltaTime / this.transition.getTransitionTime() * 2.0f * 1.0f / this.transitionRate;
				this.transitionPct = this.transition.getTransitionType().interpolate(pct);
				if (this.transitionPct >= 1.0f)
					this.transition = null;
			}
		}
		if (!this.swapped && current != this) {
			this.swapped = true;
			this.swappedScreen = current;
		}
		this.minecraft.screen = this;
	}

	private boolean checkBeginTransition(Screen current) {
		if (this.minecraft.level != null)
			return false;
		if (current == this.transitionScreen && this.handleRecursion) {
			this.handleRecursion = false;
			this.minecraft.setScreen(null);
		}
		if (current != this.transitionScreen) {
			if (current != null) {
				if (current.getClass().getName().contains("DialogBox"))
					return false;
			}
			return true;
		}
		return true;
	}
}
