package com.itemdroppingaccuracy;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(ItemDroppingAccuracyConfig.GROUP)
public interface ItemDroppingAccuracyConfig extends Config
{
	String GROUP = "itemdroppingaccuracy";

	@ConfigSection(
		name = "Targets",
		description = "Which items and actions are part of a run",
		position = 0
	)
	String targetsSection = "targets";

	@ConfigSection(
		name = "Pattern",
		description = "The order you should click in",
		position = 1
	)
	String patternSection = "pattern";

	@ConfigSection(
		name = "Display",
		description = "Overlays and colours",
		position = 2
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Advanced",
		description = "Timing and hotkeys",
		position = 4,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@Range(min = 0, max = 28)
	@ConfigItem(
		keyName = "itemsPerRun",
		name = "Items per run",
		description = "How many items make up one run (26 = a full inventory minus two tools or stacks). A run only starts when at least this many target items are in the inventory, so a few leftovers are not tracked as a tiny run. 0 = every target item, however few.",
		section = targetsSection,
		position = 0
	)
	default int itemsPerRun()
	{
		return 26;
	}

	@ConfigItem(
		keyName = "includeItems",
		name = "Only these items",
		description = "Comma separated item names (* wildcard allowed) or ids. Leave blank to target everything that is not excluded.<br>Example: Empty bucket pack",
		section = targetsSection,
		position = 1
	)
	default String includeItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "excludeItems",
		name = "Excluded items",
		description = "Comma separated item names (* wildcard allowed) or ids that are never part of a run.<br>Example: Barbarian rod, Feather, Coins",
		section = targetsSection,
		position = 2
	)
	default String excludeItems()
	{
		return "Barbarian rod, Feather, Coins";
	}

	@ConfigItem(
		keyName = "excludeNoted",
		name = "Exclude noted items",
		description = "Never treat noted items (e.g. noted buckets) as targets.",
		section = targetsSection,
		position = 3
	)
	default boolean excludeNoted()
	{
		return true;
	}

	@ConfigItem(
		keyName = "actions",
		name = "Click actions",
		description = "Comma separated menu options that count as a click on a target (the left-click option of the item).",
		section = targetsSection,
		position = 4
	)
	default String actions()
	{
		return "Drop, Open";
	}

	@ConfigItem(
		keyName = "pattern",
		name = "Pattern",
		description = "The order the overlay guides you through.",
		section = patternSection,
		position = 0
	)
	default ClickPattern pattern()
	{
		return ClickPattern.DU_SNAKE_ROWS;
	}

	@ConfigItem(
		keyName = "customPattern",
		name = "Custom pattern",
		description = "Used when Pattern is Custom. Inventory slots 1-28 (left to right, top to bottom) in click order, e.g. 1,5,9,13,2,6,10,14.<br>Slots you leave out follow in row order.",
		section = patternSection,
		position = 1
	)
	default String customPattern()
	{
		return "";
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "showAhead",
		name = "Items to show ahead",
		description = "How many upcoming items are highlighted at once.",
		section = patternSection,
		position = 2
	)
	default int showAhead()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "showNumbers",
		name = "Show step numbers",
		description = "Number the highlighted items in click order.",
		section = patternSection,
		position = 3
	)
	default boolean showNumbers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPatternOverlay",
		name = "Show pattern overlay",
		description = "Highlight the items to click next in the inventory.",
		section = displaySection,
		position = 0
	)
	default boolean showPatternOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStatsOverlay",
		name = "Show stats overlay",
		description = "Show accuracy, time and perfect-run counters.",
		section = displaySection,
		position = 1
	)
	default boolean showStatsOverlay()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "nextColor",
		name = "Next item colour",
		description = "Colour of the item to click now.",
		section = displaySection,
		position = 2
	)
	default Color nextColor()
	{
		return new Color(0, 255, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "upcomingColor",
		name = "Upcoming item colour",
		description = "Colour of the items after the next one.",
		section = displaySection,
		position = 3
	)
	default Color upcomingColor()
	{
		return new Color(255, 200, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "missedColor",
		name = "Missed item colour",
		description = "Colour of items that were skipped or whose click failed. They are clicked after the rest of the pattern.",
		section = displaySection,
		position = 4
	)
	default Color missedColor()
	{
		return new Color(255, 50, 50);
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "confirmTicks",
		name = "Confirm window (ticks)",
		description = "How many game ticks to wait for a clicked item to leave its slot before the click counts as a miss.",
		section = advancedSection,
		position = 0
	)
	default int confirmTicks()
	{
		return 3;
	}

	@Range(min = 3, max = 120)
	@ConfigItem(
		keyName = "idleSeconds",
		name = "Idle timeout (seconds)",
		description = "An unfinished run is abandoned after this long without a click.",
		section = advancedSection,
		position = 1
	)
	default int idleSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "resetKey",
		name = "Reset hotkey",
		description = "Resets the current run and session stats.",
		section = advancedSection,
		position = 2
	)
	default Keybind resetKey()
	{
		return Keybind.NOT_SET;
	}
}
