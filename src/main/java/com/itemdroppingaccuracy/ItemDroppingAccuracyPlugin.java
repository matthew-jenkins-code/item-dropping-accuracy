package com.itemdroppingaccuracy;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Item Dropping Accuracy",
	description = "Tracks click accuracy and speed when dropping or opening inventory items, with a click-pattern overlay",
	tags = {"click", "accuracy", "drop", "open", "pack", "trainer", "stats", "streak"}
)
/**
 * Tracks click accuracy and speed while dropping/opening inventory items.
 *
 * <p>Everything is event driven: nothing runs per frame here, and an idle game tick only checks two
 * empty collections. All state is written on the client thread; the fields read by the overlays are
 * volatile (or replaced as immutable snapshots) so the render thread never needs a lock.
 * The plugin makes no network calls and writes config only when a run finishes.
 */
public class ItemDroppingAccuracyPlugin extends Plugin
{
	private static final long RAW_MATCH_WINDOW_NS = 500_000_000L;
	private static final long RAW_MISS_DELAY_NS = 400_000_000L;
	private static final int MAX_RAW_CLICKS = 50;
	private static final int LATE_CONFIRM_TICKS = 10;
	private static final String PERFECT_KEY = "perfectRuns";
	private static final String STREAK_KEY = "perfectStreak";
	private static final String BEST_STREAK_KEY = "bestPerfectStreak";
	private static final String BEST_TIME_PREFIX = "bestTime";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemDroppingAccuracyConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PatternOverlay patternOverlay;

	@Inject
	private StatsOverlay statsOverlay;

	// ---- client-thread state ----
	private final int[] invIds = new int[ClickPattern.SIZE];
	private final int[] invQty = new int[ClickPattern.SIZE];
	private final int[] prevIds = new int[ClickPattern.SIZE];
	private final int[] prevQty = new int[ClickPattern.SIZE];
	private final Map<Integer, Attempt> pending = new HashMap<>();
	/** Clicks that timed out but whose item may still leave a little late; matched before calling a loss "automatic". */
	private final List<Attempt> expired = new ArrayList<>();
	/** Clicks on target items that were not tracked (run already full); their items leaving is expected. */
	private final Map<Integer, Integer> ignoredClicks = new HashMap<>();
	private final Map<Integer, Boolean> targetCache = new HashMap<>();
	private final Queue<RawClick> rawClicks = new ConcurrentLinkedQueue<>();
	private Set<String> actionSet = Collections.emptySet();
	// Config values are cached here (see parseConfig) because config getters are not free and some are read per frame.
	private List<Integer> patternOrder = ClickPattern.ROW_BY_ROW.order("");
	private int itemsPerRun;
	private int confirmTicks;
	private long idleNs;
	private final Map<Integer, Long> bestTimes = new HashMap<>();
	private ItemFilter includeFilter = new ItemFilter("");
	private ItemFilter excludeFilter = new ItemFilter("");
	private long lastClickNs;
	/** Pattern position of the latest click; the overlay continues from here so one slip does not derail the rest. */
	private int cursor = -1;
	/** Slots that were skipped or failed; they stay red and are queued after the rest of the pattern. */
	private final Set<Integer> missedSlots = new HashSet<>();
	private long lastHitClickNs;

	// ---- state read by overlays (render thread) ----
	@Getter
	private volatile List<Integer> upcomingOrder = Collections.emptyList();
	@Getter
	private volatile Map<Integer, Integer> upcomingRanks = Collections.emptyMap();
	@Getter
	private volatile Set<Integer> missedView = Collections.emptySet();

	@Getter
	private volatile boolean showPatternOverlay;
	@Getter
	private volatile boolean showStatsOverlay;
	@Getter
	private volatile boolean showNumbers;
	@Getter
	private volatile int showAhead;
	@Getter
	private volatile Color nextColor = Color.GREEN;
	@Getter
	private volatile Color upcomingColor = Color.YELLOW;
	@Getter
	private volatile Color missedColor = Color.RED;

	@Getter
	private volatile boolean active;
	@Getter
	private volatile boolean finished;
	@Getter
	private volatile boolean patternBroken;
	/** A target item left the inventory without one of our clicks (e.g. it opened on its own): the run failed. */
	@Getter
	private volatile boolean autoFailed;
	@Getter
	private volatile int targetTotal;
	@Getter
	private volatile int hits;
	@Getter
	private volatile int misses;
	@Getter
	private volatile long startNs;
	@Getter
	private volatile long finalElapsedMs;
	@Getter
	private volatile int readyTarget;
	@Getter
	private volatile long bestTimeMs;
	@Getter
	private volatile int sessionRuns;
	@Getter
	private volatile int sessionHits;
	@Getter
	private volatile int sessionMisses;
	@Getter
	private volatile long sessionBestRunMs;
	@Getter
	private volatile int sessionPerfectRuns;
	@Getter
	private volatile int totalPerfectRuns;
	/** Consecutive perfect runs; any imperfect finished run resets it. Saved between sessions. */
	@Getter
	private volatile int perfectStreak;
	@Getter
	private volatile int bestPerfectStreak;

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (SwingUtilities.isLeftMouseButton(e))
			{
				rawClicks.add(new RawClick(System.nanoTime(), e.getPoint()));
				while (rawClicks.size() > MAX_RAW_CLICKS)
				{
					rawClicks.poll();
				}
			}
			return e;
		}
	};

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.resetKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(ItemDroppingAccuracyPlugin.this::resetAll);
		}
	};

	@Provides
	ItemDroppingAccuracyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemDroppingAccuracyConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(patternOverlay);
		overlayManager.add(statsOverlay);
		mouseManager.registerMouseListener(mouseListener);
		keyManager.registerKeyListener(hotkeyListener);
		totalPerfectRuns = loadInt(PERFECT_KEY);
		perfectStreak = loadInt(STREAK_KEY);
		bestPerfectStreak = loadInt(BEST_STREAK_KEY);
		clientThread.invoke(() ->
		{
			parseConfig();
			refreshInventory();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(patternOverlay);
		overlayManager.remove(statsOverlay);
		mouseManager.unregisterMouseListener(mouseListener);
		keyManager.unregisterKeyListener(hotkeyListener);
		pending.clear();
		rawClicks.clear();
		targetCache.clear();
		bestTimes.clear();
		upcomingOrder = Collections.emptyList();
		upcomingRanks = Collections.emptyMap();
		resetCounters();
		sessionRuns = 0;
		sessionHits = 0;
		sessionMisses = 0;
		sessionBestRunMs = 0;
		sessionPerfectRuns = 0;
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!ItemDroppingAccuracyConfig.GROUP.equals(e.getGroup()) || isStateKey(e.getKey()))
		{
			return; // not ours, or just the stats this plugin saved itself
		}
		clientThread.invoke(() ->
		{
			parseConfig();
			recompute();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			abandonRun();
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked e)
	{
		if (e.getEntry().getMenuAction() == MenuAction.RUNELITE_OVERLAY
			&& e.getOverlay() == statsOverlay
			&& StatsOverlay.RESET_OPTION.equals(e.getEntry().getOption()))
		{
			resetAll();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.INV)
		{
			return;
		}
		System.arraycopy(invIds, 0, prevIds, 0, ClickPattern.SIZE);
		System.arraycopy(invQty, 0, prevQty, 0, ClickPattern.SIZE);
		readInventory(e.getItemContainer());

		// An attempt is confirmed once its slot changed AND the inventory really lost one of that item.
		// Checking the slot alone would count an accidental drag/swap of two items as a successful drop.
		Map<Integer, Integer> lost = new HashMap<>();
		Iterator<Map.Entry<Integer, Attempt>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			Attempt a = it.next().getValue();
			if (invIds[a.slot] == a.itemId && invQty[a.slot] >= a.quantity)
			{
				continue; // still there
			}
			int left = lost.computeIfAbsent(a.itemId, id -> Math.max(0, total(id, prevIds, prevQty) - total(id, invIds, invQty)));
			if (left > 0)
			{
				lost.put(a.itemId, left - 1);
				it.remove();
				registerHit(a);
			}
		}
		if (active)
		{
			checkUnclickedLosses(lost);
		}
		recompute();
		checkCompletion();
	}

	/**
	 * Any target item that left the inventory without a click of ours (for example an item that opens itself
	 * when left idle) fails the run. Late confirmations of clicks that already timed out are not counted.
	 */
	private void checkUnclickedLosses(Map<Integer, Integer> lost)
	{
		Set<Integer> seen = new HashSet<>();
		for (int id : prevIds)
		{
			if (id <= 0 || !seen.add(id) || !isTarget(id))
			{
				continue;
			}
			int unexplained = lost.containsKey(id) ? lost.get(id) : Math.max(0, total(id, prevIds, prevQty) - total(id, invIds, invQty));
			Iterator<Attempt> late = expired.iterator();
			while (unexplained > 0 && late.hasNext())
			{
				Attempt a = late.next();
				if (a.itemId == id && (invIds[a.slot] != id || invQty[a.slot] < a.quantity))
				{
					late.remove();
					registerHit(a);
					unexplained--;
				}
			}
			int ignored = ignoredClicks.getOrDefault(id, 0);
			if (unexplained > 0 && ignored > 0)
			{
				int used = Math.min(unexplained, ignored);
				ignoredClicks.put(id, ignored - used);
				unexplained -= used;
			}
			if (unexplained > 0 && !autoFailed)
			{
				autoFailed = true;
				registerMiss(); // one penalty, like a wrong-order run
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (e.getParam1() != InterfaceID.Inventory.ITEMS)
		{
			return;
		}
		String option = e.getMenuOption() == null ? "" : Text.removeTags(e.getMenuOption()).toLowerCase();
		int slot = e.getParam0();
		int itemId = e.getItemId();
		if (!actionSet.contains(option) || itemId <= 0 || slot < 0 || slot >= ClickPattern.SIZE || !isTarget(itemId))
		{
			// Not a click on a target; if a run is active the raw click is reported as a miss on the next tick.
			return;
		}

		if (!active && countAllTargets() < itemsPerRun)
		{
			return; // fewer items than a full run (leftovers): not tracked
		}

		consumeRawClick();
		long now = System.nanoTime();

		if (!active)
		{
			startRun(now);
		}
		else if (hits + pending.size() >= targetTotal)
		{
			ignoredClicks.merge(itemId, 1, Integer::sum);
			return;
		}
		lastClickNs = now;

		if (pending.containsKey(slot))
		{
			registerMiss();
			return;
		}

		List<Integer> order = upcomingOrder;
		boolean offPattern = !order.isEmpty() && order.get(0) != slot;
		if (offPattern && !patternBroken)
		{
			// Wrong order: one penalty for the whole run, which then counts as failed accuracy-wise.
			patternBroken = true;
			registerMiss();
		}
		if (!missedSlots.contains(slot) && patternIndex(slot) > cursor)
		{
			// Anything the player jumped over is flagged red and gets queued after the rest of the pattern.
			for (int due : order)
			{
				if (due == slot)
				{
					break;
				}
				if (!missedSlots.contains(due))
				{
					missedSlots.add(due);
				}
			}
			cursor = patternIndex(slot);
		}
		pending.put(slot, new Attempt(slot, itemId, invQty[slot], client.getTickCount(), now, offPattern));
		recompute();
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		long now = System.nanoTime();
		int tick = client.getTickCount();

		boolean changed = false;
		Iterator<Map.Entry<Integer, Attempt>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			Attempt a = it.next().getValue();
			if (tick - a.tick >= confirmTicks)
			{
				it.remove();
				a.expiredAt = tick;
				expired.add(a);
				missedSlots.add(a.slot); // the click never registered: red, and retried after the pattern
				registerMiss();
				changed = true;
			}
		}

		if (!expired.isEmpty())
		{
			expired.removeIf(a -> tick - a.expiredAt > LATE_CONFIRM_TICKS);
		}

		RawClick rc;
		while ((rc = rawClicks.peek()) != null && now - rc.ns > RAW_MISS_DELAY_NS)
		{
			rawClicks.poll();
			if (active && inInventory(rc.point))
			{
				registerMiss();
			}
		}

		if (active && now - lastClickNs > idleNs)
		{
			abandonRun();
			return;
		}

		// Nothing else changes between ticks, so an idle tick does no more than the checks above.
		if (changed)
		{
			recompute();
			checkCompletion();
		}
	}

	// ------------------------------------------------------------------ run logic

	private void startRun(long now)
	{
		resetCounters();
		active = true;
		finished = false;
		startNs = now;
		lastClickNs = now;
		lastHitClickNs = now;
		targetTotal = countTargets();
		bestTimeMs = loadBest(targetTotal);
	}

	private static int total(int itemId, int[] ids, int[] qty)
	{
		int sum = 0;
		for (int i = 0; i < ids.length; i++)
		{
			if (ids[i] == itemId)
			{
				sum += qty[i];
			}
		}
		return sum;
	}

	private void registerHit(Attempt a)
	{
		hits++;
		sessionHits++;
		missedSlots.remove(a.slot);
		lastHitClickNs = Math.max(lastHitClickNs, a.clickNs);
	}

	private void registerMiss()
	{
		if (!active)
		{
			return;
		}
		misses++;
		sessionMisses++;
	}

	private void checkCompletion()
	{
		if (!active)
		{
			return;
		}
		boolean allHit = hits >= targetTotal;
		boolean outOfTargets = pending.isEmpty() && upcomingOrder.isEmpty();
		if (allHit || outOfTargets)
		{
			if (hits > 0)
			{
				finishRun();
			}
			else
			{
				abandonRun();
			}
		}
	}

	private int patternIndex(int slot)
	{
		return patternOrder.indexOf(slot);
	}

	private void finishRun()
	{
		finalElapsedMs = Math.max(0, (lastHitClickNs - startNs) / 1_000_000L);
		active = false;
		finished = true;
		sessionRuns++;
		if (misses == 0 && !patternBroken && !autoFailed && hits >= targetTotal)
		{
			sessionPerfectRuns++;
			totalPerfectRuns++;
			configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, PERFECT_KEY, totalPerfectRuns);
			perfectStreak++;
			if (perfectStreak > bestPerfectStreak)
			{
				bestPerfectStreak = perfectStreak;
				configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, BEST_STREAK_KEY, bestPerfectStreak);
			}
			configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, STREAK_KEY, perfectStreak);
		}
		else if (perfectStreak != 0)
		{
			perfectStreak = 0;
			configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, STREAK_KEY, 0);
		}
		cursor = -1;
		missedSlots.clear();
		if (misses == 0 && (sessionBestRunMs == 0 || finalElapsedMs < sessionBestRunMs))
		{
			sessionBestRunMs = finalElapsedMs;
		}
		if (hits == targetTotal && (bestTimeMs == 0 || finalElapsedMs < bestTimeMs))
		{
			bestTimeMs = finalElapsedMs;
			saveBest(targetTotal, finalElapsedMs);
		}
		pending.clear();
		recompute();
	}

	private void abandonRun()
	{
		active = false;
		finished = false;
		pending.clear();
		resetCounters();
		recompute();
	}

	private void resetCounters()
	{
		active = false;
		finished = false;
		patternBroken = false;
		autoFailed = false;
		expired.clear();
		ignoredClicks.clear();
		cursor = -1;
		missedSlots.clear();
		targetTotal = 0;
		hits = 0;
		misses = 0;
		startNs = 0;
		finalElapsedMs = 0;
	}

	private void resetAll()
	{
		pending.clear();
		rawClicks.clear();
		resetCounters();
		sessionRuns = 0;
		sessionHits = 0;
		sessionMisses = 0;
		sessionBestRunMs = 0;
		sessionPerfectRuns = 0;
		perfectStreak = 0;
		configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, STREAK_KEY, 0);
		recompute();
	}

	// ------------------------------------------------------------------ inventory / targets

	private void refreshInventory()
	{
		readInventory(client.getItemContainer(InventoryID.INV));
		recompute();
	}

	private void readInventory(ItemContainer container)
	{
		Arrays.fill(invIds, -1);
		Arrays.fill(invQty, 0);
		if (container == null)
		{
			return;
		}
		Item[] items = container.getItems();
		for (int i = 0; i < items.length && i < ClickPattern.SIZE; i++)
		{
			if (items[i].getId() > 0)
			{
				invIds[i] = items[i].getId();
				invQty[i] = items[i].getQuantity();
			}
		}
	}

	private void parseConfig()
	{
		includeFilter = new ItemFilter(config.includeItems());
		excludeFilter = new ItemFilter(config.excludeItems());
		Set<String> set = new HashSet<>();
		for (String s : config.actions().split(","))
		{
			String t = s.trim().toLowerCase();
			if (!t.isEmpty())
			{
				set.add(t);
			}
		}
		actionSet = set;
		targetCache.clear();

		patternOrder = config.pattern().order(config.customPattern());
		itemsPerRun = config.itemsPerRun();
		confirmTicks = config.confirmTicks();
		idleNs = config.idleSeconds() * 1_000_000_000L;
		showPatternOverlay = config.showPatternOverlay();
		showStatsOverlay = config.showStatsOverlay();
		showNumbers = config.showNumbers();
		showAhead = config.showAhead();
		nextColor = config.nextColor();
		upcomingColor = config.upcomingColor();
		missedColor = config.missedColor();
	}

	private boolean isTarget(int itemId)
	{
		Boolean cached = targetCache.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		ItemComposition comp = itemManager.getItemComposition(itemId);
		String name = comp.getName();
		boolean result = true;
		if (config.excludeNoted() && comp.getNote() != -1)
		{
			result = false;
		}
		else if (excludeFilter.matches(itemId, name))
		{
			result = false;
		}
		else if (!includeFilter.isEmpty() && !includeFilter.matches(itemId, name))
		{
			result = false;
		}
		targetCache.put(itemId, result);
		return result;
	}

	private int countAllTargets()
	{
		int count = 0;
		for (int s = 0; s < ClickPattern.SIZE; s++)
		{
			if (invIds[s] > 0 && isTarget(invIds[s]))
			{
				count++;
			}
		}
		return count;
	}

	private int countTargets()
	{
		int count = countAllTargets();
		return itemsPerRun > 0 ? Math.min(itemsPerRun, count) : count;
	}

	/** Rebuilds the list of slots still to click, in pattern order. */
	private void recompute()
	{
		List<Integer> order = patternOrder;

		missedSlots.removeIf(slot -> !pending.containsKey(slot) && (invIds[slot] <= 0 || !isTarget(invIds[slot])));

		// Main pass: not-yet-reached slots in pattern order. Retry pass: missed slots (and anything behind the cursor).
		int start = active ? cursor + 1 : 0;
		List<Integer> main = new ArrayList<>();
		List<Integer> retry = new ArrayList<>();
		for (int idx = 0; idx < order.size(); idx++)
		{
			int slot = order.get(idx);
			if (invIds[slot] <= 0 || pending.containsKey(slot) || !isTarget(invIds[slot]))
			{
				continue;
			}
			if (idx >= start && !missedSlots.contains(slot))
			{
				main.add(slot);
			}
			else
			{
				retry.add(slot);
			}
		}
		List<Integer> up = new ArrayList<>(main);
		up.addAll(retry);

		int limit;
		if (active && itemsPerRun == 0)
		{
			// "Every item" run: anything still in the inventory belongs to this run, so it only ends when all are done.
			targetTotal = Math.max(targetTotal, hits + pending.size() + up.size());
			limit = up.size();
		}
		else if (active)
		{
			limit = Math.max(0, targetTotal - hits - pending.size());
		}
		else
		{
			limit = itemsPerRun > 0 ? itemsPerRun : ClickPattern.SIZE;
		}
		if (up.size() > limit)
		{
			up = new ArrayList<>(up.subList(0, limit));
		}
		missedView = Collections.unmodifiableSet(new HashSet<>(missedSlots));

		Map<Integer, Integer> ranks = new HashMap<>();
		for (int i = 0; i < up.size(); i++)
		{
			ranks.put(up.get(i), i);
		}
		upcomingOrder = Collections.unmodifiableList(up);
		upcomingRanks = Collections.unmodifiableMap(ranks);

		if (!active)
		{
			readyTarget = countTargets();
			if (!finished)
			{
				bestTimeMs = loadBest(readyTarget);
			}
		}
	}

	// ------------------------------------------------------------------ raw clicks

	private void consumeRawClick()
	{
		long now = System.nanoTime();
		RawClick match = null;
		for (RawClick rc : rawClicks)
		{
			if (now - rc.ns <= RAW_MATCH_WINDOW_NS)
			{
				match = rc;
			}
		}
		if (match != null)
		{
			rawClicks.remove(match);
		}
	}

	private boolean inInventory(Point p)
	{
		Widget inv = client.getWidget(InterfaceID.Inventory.ITEMS);
		return inv != null && !inv.isHidden() && inv.getBounds().contains(p);
	}

	// ------------------------------------------------------------------ best time persistence

	private int loadInt(String key)
	{
		String v = configManager.getConfiguration(ItemDroppingAccuracyConfig.GROUP, key);
		try
		{
			return v == null ? 0 : Integer.parseInt(v);
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	private long loadBest(int count)
	{
		if (count <= 0)
		{
			return 0;
		}
		// Read from the config store once per item count, then serve from memory.
		return bestTimes.computeIfAbsent(count, c ->
		{
			String v = configManager.getConfiguration(ItemDroppingAccuracyConfig.GROUP, BEST_TIME_PREFIX + c);
			try
			{
				return v == null ? 0L : Long.parseLong(v);
			}
			catch (NumberFormatException ex)
			{
				return 0L;
			}
		});
	}

	private void saveBest(int count, long ms)
	{
		bestTimes.put(count, ms);
		configManager.setConfiguration(ItemDroppingAccuracyConfig.GROUP, BEST_TIME_PREFIX + count, ms);
	}

	private static boolean isStateKey(String key)
	{
		return PERFECT_KEY.equals(key) || STREAK_KEY.equals(key) || BEST_STREAK_KEY.equals(key)
			|| (key != null && key.startsWith(BEST_TIME_PREFIX));
	}

	// ------------------------------------------------------------------ helpers

	/** Elapsed run time for display. */
	public long elapsedMs()
	{
		if (finished)
		{
			return finalElapsedMs;
		}
		if (!active)
		{
			return 0;
		}
		return (System.nanoTime() - startNs) / 1_000_000L;
	}

	private static final class Attempt
	{
		final int slot;
		final int itemId;
		final int quantity;
		final int tick;
		final long clickNs;
		final boolean offPattern;
		int expiredAt;

		Attempt(int slot, int itemId, int quantity, int tick, long clickNs, boolean offPattern)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
			this.clickNs = clickNs;
			this.offPattern = offPattern;
		}
	}

	private static final class RawClick
	{
		final long ns;
		final Point point;

		RawClick(long ns, Point point)
		{
			this.ns = ns;
			this.point = point;
		}
	}
}
