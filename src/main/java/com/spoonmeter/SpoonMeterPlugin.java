package com.spoonmeter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Spoon Meter",
	description = "Rates how spooned or cursed your account is from collection log KC and uniques",
	tags = {"collection", "log", "luck", "drop", "rate", "spoon", "dry", "rng"}
)
public class SpoonMeterPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(SpoonMeterPlugin.class);

	private static final String COMMAND = "spoon";
	private static final String PUBLIC_COMMAND = "!spoon";

	// Verified against shipped core plugins rather than guessed: KeyRemappingPlugin writes varcstr
	// 335 to clear what you have typed, and ChatHistoryPlugin runs script 222 to redraw the input.
	private static final int CHATBOX_TYPED_TEXT = 335;
	private static final int CHAT_TEXT_INPUT_REBUILD = 222;
	private static final int SAVE_INTERVAL_TICKS = 50; // ~30 seconds

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private SpoonMeterConfig config;

	private final Map<String, CollectionLogPage> pages = new LinkedHashMap<>();

	private CollectionLogParser parser;
	private DropTable dropTable;
	private SpoonMeterPanel panel;
	private NavigationButton navButton;

	private boolean dirty;
	private boolean commandRegistered;
	private int ticksSinceSave;
	private String loadedProfileKey;
	private String lastFingerprint;

	@Provides
	SpoonMeterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpoonMeterConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		parser = new CollectionLogParser(itemManager);
		dropTable = DropTable.load(gson, new File(dataDir(), "drop_rates.json"));

		panel = new SpoonMeterPanel(this);

		BufferedImage icon = ImageUtil.loadImageResource(SpoonMeterPlugin.class, "/com/spoonmeter/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Spoon Meter")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		setChatCommandRegistered(config.publicChatCommand());

		loadForCurrentProfile();
		refreshPanel();

		log.info("Spoon Meter started: sidebar button added, {} drop sources, {} saved pages",
			dropTable.getSources().size(), pages.size());
	}

	@Override
	protected void shutDown() throws Exception
	{
		flush(false);
		setChatCommandRegistered(false);
		clientToolbar.removeNavigation(navButton);
		pages.clear();
		panel = null;
		navButton = null;
		lastFingerprint = null;
		loadedProfileKey = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadForCurrentProfile();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadForCurrentProfile();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			flush();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == CollectionLogParser.COLLECTION_LOG_GROUP_ID)
		{
			lastFingerprint = null;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == CollectionLogParser.COLLECTION_LOG_GROUP_ID)
		{
			lastFingerprint = null;
			flush();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Checked every tick rather than tracked with a flag: enabling the plugin while the log is
		// already open still works, and a missed close event cannot wedge it.
		if (parser.isLogOpen(client))
		{
			readOpenPage();
		}

		if (dirty && ++ticksSinceSave >= SAVE_INTERVAL_TICKS)
		{
			flush();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SpoonMeterConfig.GROUP.equals(event.getGroup()))
		{
			setChatCommandRegistered(config.publicChatCommand());
			refreshPanel();
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!config.chatCommand() || !COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		SpoonReport report = buildReport();

		String message = report.isEmpty()
			? new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("Spoon Meter: ")
				.append(ChatColorType.NORMAL).append("no rated pages yet - open your collection log.")
				.build()
			: new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("Spoon Meter: ")
				.append(ChatColorType.NORMAL).append(SpoonSummary.oneLine(report))
				.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	/**
	 * Handles {@code !spoon} typed into public, clan or friends chat.
	 *
	 * <p>This rewrites the message where it is displayed, exactly like {@code !kc} does. Everyone
	 * else still receives the literal "!spoon" text: the client cannot alter what is sent, and
	 * nobody else's client holds this account's collection log to look the answer up with.
	 */
	private void onSpoonChatCommand(ChatMessage chatMessage, String message)
	{
		Player local = client.getLocalPlayer();

		// Someone else's !spoon must be left alone rather than answered with our numbers.
		if (local == null || local.getName() == null
			|| !local.getName().equals(Text.sanitize(chatMessage.getName())))
		{
			return;
		}

		SpoonReport report = buildReport();
		String argument = message.length() > PUBLIC_COMMAND.length()
			? message.substring(PUBLIC_COMMAND.length()) : "";

		String line = report.isEmpty()
			? "Spoon Meter: no rated pages yet"
			: SpoonSummary.chatLine(report, argument);

		MessageNode node = chatMessage.getMessageNode();
		node.setRuneLiteFormatMessage(new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(line)
			.build());

		client.refreshChat();
	}

	/**
	 * Types the rating into the game's chat input, leaving you to press Enter.
	 *
	 * <p>The client cannot send chat itself, and there is no paste into the game chatbox, so this is
	 * the only route to text your clan actually receives. Both ids are the ones core RuneLite uses:
	 * varcstr 335 is the chatbox typed text (KeyRemappingPlugin clears it), and script 222 redraws
	 * the input line (ChatHistoryPlugin runs it after filling in a reply).
	 */
	void prefillChatbox()
	{
		SpoonReport report = buildReport();

		if (report.isEmpty())
		{
			return;
		}

		String prefix = config.clanChatPrefix() ? "/" : "";
		String line = prefix + SpoonSummary.chatLine(report, "",
			SpoonSummary.MAX_CHAT_LENGTH - prefix.length());

		clientThread.invokeLater(() ->
		{
			client.setVarcStrValue(CHATBOX_TYPED_TEXT, line);
			client.runScript(CHAT_TEXT_INPUT_REBUILD, "");
		});
	}

	private void setChatCommandRegistered(boolean wanted)
	{
		if (wanted == commandRegistered)
		{
			return;
		}

		if (wanted)
		{
			chatCommandManager.registerCommand(PUBLIC_COMMAND, this::onSpoonChatCommand);
		}
		else
		{
			chatCommandManager.unregisterCommand(PUBLIC_COMMAND);
		}

		commandRegistered = wanted;
	}

	/** Called by the panel when the user picks a different sort order. */
	void setSortMode(SortMode sortMode)
	{
		if (sortMode != null && sortMode != config.sortMode())
		{
			configManager.setConfiguration(SpoonMeterConfig.GROUP, "sortMode", sortMode);
		}
	}

	private void readOpenPage()
	{
		CollectionLogPage page = parser.parseOpenPage(client);

		if (page == null)
		{
			return;
		}

		String fingerprint = page.fingerprint();

		if (fingerprint.equals(lastFingerprint))
		{
			return;
		}

		lastFingerprint = fingerprint;

		CollectionLogPage previous = pages.put(page.getName(), page);

		if (previous == null || !previous.fingerprint().equals(fingerprint))
		{
			dirty = true;
			refreshPanel();
		}
	}

	private SpoonReport buildReport()
	{
		Map<String, Double> uniqueChances = new HashMap<>();
		uniqueChances.put("cox", config.coxUniqueChance() / 100.0);
		uniqueChances.put("coxcm", config.coxCmUniqueChance() / 100.0);
		uniqueChances.put("tob", config.tobUniqueChance() / 100.0);
		uniqueChances.put("tobhm", config.tobHardUniqueChance() / 100.0);
		uniqueChances.put("toa", config.toaUniqueChance() / 100.0);
		uniqueChances.put("toaexpert", config.toaExpertUniqueChance() / 100.0);

		return SpoonReport.build(pages.values(), dropTable, uniqueChances, config.minimumKc());
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}

		SpoonReport report = buildReport();
		SortMode sortMode = config.sortMode();
		SwingUtilities.invokeLater(() -> panel.update(report, sortMode));
	}

	// --- persistence ----------------------------------------------------------------------
	// Kept in RuneLite's own directory rather than the config service: a full collection log is far
	// too big to be sensible as a synced config value, and it is per account anyway.

	private static File dataDir()
	{
		File dir = new File(RuneLite.RUNELITE_DIR, "spoon-meter");

		if (!dir.exists() && !dir.mkdirs())
		{
			log.warn("Could not create data directory {}", dir);
		}

		return dir;
	}

	private File dataFile(String profileKey)
	{
		return new File(dataDir(), profileKey.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
	}

	/**
	 * RuneLite's RS profile key identifies the logged in account without exposing anything about it,
	 * and changes when you switch accounts, which is exactly when the saved log should switch too.
	 */
	private void loadForCurrentProfile()
	{
		String profileKey = configManager.getRSProfileKey();

		if (profileKey == null || profileKey.equals(loadedProfileKey))
		{
			return;
		}

		flush();

		loadedProfileKey = profileKey;
		pages.clear();
		lastFingerprint = null;

		File file = dataFile(profileKey);

		if (file.isFile())
		{
			Type type = new TypeToken<Map<String, CollectionLogPage>>()
			{
			}.getType();

			try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
			{
				Map<String, CollectionLogPage> saved = gson.fromJson(reader, type);

				if (saved != null)
				{
					pages.putAll(saved);
					dropStalePersonalBests();
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Could not read saved collection log data from {}", file, e);
			}
		}

		refreshPanel();
	}

	/**
	 * Earlier builds read "Personal Best: 9:51" as a counter called "Personal Best: 9" worth 51,
	 * which outranks the real kill count on any page with fewer kills than that. The parser no
	 * longer produces them, but saved files still hold them until each page is opened again.
	 */
	private void dropStalePersonalBests()
	{
		int removed = 0;

		for (CollectionLogPage page : pages.values())
		{
			removed += page.getCounters().keySet()
				.removeIf(label -> label.toLowerCase(Locale.ENGLISH).startsWith("personal best")) ? 1 : 0;
		}

		if (removed > 0)
		{
			dirty = true;
			log.info("Dropped personal best times from {} saved pages", removed);
		}
	}

	/** Writes pending changes off the client thread. */
	private void flush()
	{
		flush(true);
	}

	private void flush(boolean async)
	{
		ticksSinceSave = 0;

		if (!dirty || loadedProfileKey == null || pages.isEmpty())
		{
			return;
		}

		dirty = false;

		Map<String, CollectionLogPage> snapshot = new LinkedHashMap<>(pages);
		File file = dataFile(loadedProfileKey);

		if (async)
		{
			executor.execute(() -> write(file, snapshot));
		}
		else
		{
			// On shutdown there may be no executor left to run it later.
			write(file, snapshot);
		}
	}

	private void write(File file, Map<String, CollectionLogPage> snapshot)
	{
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			gson.toJson(snapshot, writer);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not save collection log data to {}", file, e);
		}
	}
}
