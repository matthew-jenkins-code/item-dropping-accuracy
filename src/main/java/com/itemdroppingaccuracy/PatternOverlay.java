package com.itemdroppingaccuracy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Highlights the next inventory items to click, numbered. */
public class PatternOverlay extends WidgetItemOverlay
{
	private static final BasicStroke STROKE_NEXT = new BasicStroke(2.5f);
	private static final BasicStroke STROKE_UPCOMING = new BasicStroke(1.5f);

	private final ItemDroppingAccuracyPlugin plugin;

	@Inject
	private PatternOverlay(ItemDroppingAccuracyPlugin plugin)
	{
		this.plugin = plugin;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!plugin.isShowPatternOverlay())
		{
			return;
		}
		if (item.getWidget() == null)
		{
			return;
		}
		int slot = item.getWidget().getIndex();
		Integer rank = plugin.getUpcomingRanks().get(slot);
		boolean missed = plugin.getMissedView().contains(slot);
		// Missed items always stay visible; everything else only shows within the look-ahead.
		if (rank == null || (rank >= plugin.getShowAhead() && !missed))
		{
			return;
		}

		Rectangle b = item.getCanvasBounds();
		boolean next = rank == 0;
		Color base = missed ? plugin.getMissedColor() : next ? plugin.getNextColor() : plugin.getUpcomingColor();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int fillAlpha = next ? 70 + (int) (40 * (1 + Math.sin(System.currentTimeMillis() / 150.0)) / 2) : 35;
		g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(fillAlpha, base.getAlpha())));
		g.fill(b);
		g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), base.getAlpha()));
		g.setStroke(next ? STROKE_NEXT : STROKE_UPCOMING);
		g.draw(b);

		if (plugin.isShowNumbers() && rank < plugin.getShowAhead())
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			String text = String.valueOf(rank + 1);
			g.setColor(Color.BLACK);
			g.drawString(text, b.x + 3, b.y + 12);
			g.setColor(Color.WHITE);
			g.drawString(text, b.x + 2, b.y + 11);
		}
	}
}
