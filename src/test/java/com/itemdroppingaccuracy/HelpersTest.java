package com.itemdroppingaccuracy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class HelpersTest
{
	@Test
	public void rowByRow()
	{
		List<Integer> o = ClickPattern.ROW_BY_ROW.order("");
		Assert.assertEquals(28, o.size());
		Assert.assertEquals(Arrays.asList(0, 1, 2, 3, 4), o.subList(0, 5));
	}

	@Test
	public void columnByColumn()
	{
		List<Integer> o = ClickPattern.COLUMN_BY_COLUMN.order("");
		Assert.assertEquals(Arrays.asList(0, 4, 8, 12, 16, 20, 24, 1), o.subList(0, 8));
	}

	@Test
	public void snakeRows()
	{
		List<Integer> o = ClickPattern.SNAKE_ROWS.order("");
		Assert.assertEquals(Arrays.asList(0, 1, 2, 3, 7, 6, 5, 4, 8), o.subList(0, 9));
	}

	@Test
	public void snakeColumns()
	{
		List<Integer> o = ClickPattern.SNAKE_COLUMNS.order("");
		Assert.assertEquals(Arrays.asList(0, 4, 8, 12, 16, 20, 24, 25, 21), o.subList(0, 9));
	}

	@Test
	public void duSnakeRowsMatchesTheHandMadePattern()
	{
		// 1-based: 4,3,7,8,12,11,15,16,20,19,23,24,28,27 down the right pair, 26,25,21,22,18,17,13,14,10,9,5,6,2,1 back up the left
		List<Integer> expected = Arrays.asList(3, 2, 6, 7, 11, 10, 14, 15, 19, 18, 22, 23, 27, 26,
			25, 24, 20, 21, 17, 16, 12, 13, 9, 8, 4, 5, 1, 0);
		Assert.assertEquals(expected, ClickPattern.DU_SNAKE_ROWS.order(""));
	}

	@Test
	public void duSnakeRowsLeftIsTheMirrorImage()
	{
		List<Integer> right = ClickPattern.DU_SNAKE_ROWS.order("");
		List<Integer> left = ClickPattern.DU_SNAKE_ROWS_LEFT.order("");
		for (int i = 0; i < right.size(); i++)
		{
			int s = right.get(i);
			Assert.assertEquals((int) left.get(i), s / 4 * 4 + (3 - s % 4));
		}
	}

	@Test
	public void udSnakeRowsStartsAtTheBottom()
	{
		List<Integer> o = ClickPattern.UD_SNAKE_ROWS.order("");
		Assert.assertEquals(Arrays.asList(27, 26, 22, 23, 19, 18), o.subList(0, 6));
		// the right pair ends at the top (row 1), and the left pair then comes back down from the top
		Assert.assertEquals(Arrays.asList(1, 0), o.subList(14, 16));
	}

	@Test
	public void customAppendsRemainingSlots()
	{
		List<Integer> o = ClickPattern.CUSTOM.order("5, 1, junk, 99, 5");
		Assert.assertEquals(28, o.size());
		Assert.assertEquals(Arrays.asList(4, 0, 1, 2, 3), o.subList(0, 5));
		Assert.assertEquals(28, new HashSet<>(o).size());
	}

	@Test
	public void allPatternsCoverEverySlotOnce()
	{
		for (ClickPattern p : ClickPattern.values())
		{
			Assert.assertEquals(p.name(), 28, new HashSet<>(p.order("1,2")).size());
		}
	}

	@Test
	public void itemFilter()
	{
		ItemFilter f = new ItemFilter("Barbarian rod, feather, *pack, 995");
		Assert.assertTrue(f.matches(11323, "Barbarian rod"));
		Assert.assertTrue(f.matches(314, "Feather"));
		Assert.assertTrue(f.matches(1, "Empty bucket pack"));
		Assert.assertTrue(f.matches(995, "Coins"));
		Assert.assertFalse(f.matches(11328, "Leaping trout"));
		Assert.assertFalse(new ItemFilter("").matches(1, "Anything"));
		Assert.assertTrue(new ItemFilter("").isEmpty());
	}

	@Test
	public void statsFormatting()
	{
		Assert.assertEquals("12.3s", StatsOverlay.seconds(12345));
		Assert.assertEquals("0.0s", StatsOverlay.seconds(0));
		Assert.assertEquals("-", StatsOverlay.percent(0, 0));
		Assert.assertEquals("96.6%", StatsOverlay.percent(28, 1));
		Assert.assertEquals("100.0%", StatsOverlay.percent(28, 0));
		Assert.assertEquals("96.6% (28/29)", StatsOverlay.accuracy(28, 1));
	}
}
