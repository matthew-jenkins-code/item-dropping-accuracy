package com.itemdroppingaccuracy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class StatsOverlay extends OverlayPanel
{
	static final String RESET_OPTION = "Reset";
	private static final int PANEL_WIDTH = 190;

	private final ItemDroppingAccuracyPlugin plugin;

	@Inject
	private StatsOverlay(ItemDroppingAccuracyPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, RESET_OPTION, "Item Dropping Accuracy"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isShowStatsOverlay())
		{
			return null;
		}

		boolean active = plugin.isActive();
		boolean finished = plugin.isFinished();
		int hits = plugin.getHits();
		int misses = plugin.getMisses();
		int total = plugin.getTargetTotal();

		String status;
		Color statusColor;
		boolean auto = plugin.isAutoFailed();
		boolean broken = plugin.isPatternBroken() || auto;
		if (active)
		{
			status = auto ? "Auto-opened" : broken ? "Wrong order" : "Running";
			statusColor = broken ? Color.ORANGE : Color.GREEN;
		}
		else if (finished)
		{
			status = auto ? "Failed (auto)" : broken ? "Failed (order)" : misses == 0 ? "Perfect!" : "Complete";
			statusColor = broken ? Color.ORANGE : misses == 0 ? Color.CYAN : Color.YELLOW;
		}
		else
		{
			status = "Ready";
			statusColor = Color.LIGHT_GRAY;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("Item Dropping Accuracy").build());
		line("Status", status, statusColor);
		if (!active && !finished)
		{
			line("Items", String.valueOf(plugin.getReadyTarget()), Color.WHITE);
		}

		if (active || finished)
		{
			line("Progress", hits + " / " + total, Color.WHITE);
			line("Accuracy", accuracy(hits, misses), accuracyColor(hits, misses));
			line("Time", seconds(plugin.elapsedMs()), Color.WHITE);
		}

		if (plugin.getBestTimeMs() > 0)
		{
			int count = active || finished ? total : plugin.getReadyTarget();
			line("Best (" + count + ")", seconds(plugin.getBestTimeMs()), Color.LIGHT_GRAY);
		}

		if (plugin.getTotalPerfectRuns() > 0 || plugin.getSessionPerfectRuns() > 0)
		{
			line("Perfect", plugin.getSessionPerfectRuns() + " (" + plugin.getTotalPerfectRuns() + " total)", Color.CYAN);
			line("Streak", plugin.getPerfectStreak() + " (best " + plugin.getBestPerfectStreak() + ")", Color.CYAN);
		}

		if (plugin.getSessionRuns() > 0)
		{
			line("Session", plugin.getSessionRuns() + " runs, " + percent(plugin.getSessionHits(), plugin.getSessionMisses()), Color.LIGHT_GRAY);
			if (plugin.getSessionBestRunMs() > 0)
			{
				line("Best clean", seconds(plugin.getSessionBestRunMs()), Color.LIGHT_GRAY);
			}
		}

		return super.render(graphics);
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build());
	}

	static String seconds(long ms)
	{
		return (ms / 1000) + "." + (ms / 100 % 10) + "s";
	}

	/** hits/clicks as a percentage with one decimal, rounded, without going through String.format. */
	private static String tenths(int hits, int clicks)
	{
		int t = (int) ((hits * 1000L + clicks / 2) / clicks);
		return (t / 10) + "." + (t % 10);
	}

	static String percent(int hits, int misses)
	{
		int clicks = hits + misses;
		return clicks == 0 ? "-" : tenths(hits, clicks) + "%";
	}

	static String accuracy(int hits, int misses)
	{
		int clicks = hits + misses;
		if (clicks == 0)
		{
			return "-";
		}
		return tenths(hits, clicks) + "% (" + hits + "/" + clicks + ")";
	}

	private static Color accuracyColor(int hits, int misses)
	{
		int clicks = hits + misses;
		if (clicks == 0)
		{
			return Color.WHITE;
		}
		double pct = 100.0 * hits / clicks;
		return pct >= 95 ? Color.GREEN : pct >= 85 ? Color.YELLOW : Color.ORANGE;
	}
}
