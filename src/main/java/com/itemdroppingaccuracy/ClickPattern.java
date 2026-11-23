package com.itemdroppingaccuracy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Order in which inventory slots should be clicked. Slots are 0-27, laid out 4 columns x 7 rows
 * (slot = row * 4 + col).
 */
public enum ClickPattern
{
	ROW_BY_ROW("Row by row"),
	COLUMN_BY_COLUMN("Column by column"),
	SNAKE_ROWS("Snake rows"),
	SNAKE_COLUMNS("Snake columns"),
	/** Right two columns snaking down, then the left two columns snaking back up. */
	DU_SNAKE_ROWS("DU Snake Rows"),
	/** Left two columns snaking down, then the right two columns snaking back up. */
	DU_SNAKE_ROWS_LEFT("DU Snake Rows (left first)"),
	/** Right two columns snaking up from the bottom, then the left two columns snaking back down. */
	UD_SNAKE_ROWS("UD Snake Rows"),
	/** Left two columns snaking up from the bottom, then the right two columns snaking back down. */
	UD_SNAKE_ROWS_LEFT("UD Snake Rows (left first)"),
	CUSTOM("Custom");

	public static final int COLS = 4;
	public static final int ROWS = 7;
	public static final int SIZE = COLS * ROWS;

	private final String name;

	ClickPattern(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}

	/**
	 * @param custom 1-based slot list such as "1,2,3,4,8,7,6,5"; only used for CUSTOM. Slots not listed
	 *               are appended afterwards in row order.
	 * @return all 28 slots (0-based) in click order
	 */
	public List<Integer> order(String custom)
	{
		Set<Integer> out = new LinkedHashSet<>();
		switch (this)
		{
			case ROW_BY_ROW:
				for (int s = 0; s < SIZE; s++)
				{
					out.add(s);
				}
				break;
			case COLUMN_BY_COLUMN:
				for (int c = 0; c < COLS; c++)
				{
					for (int r = 0; r < ROWS; r++)
					{
						out.add(r * COLS + c);
					}
				}
				break;
			case SNAKE_ROWS:
				for (int r = 0; r < ROWS; r++)
				{
					for (int i = 0; i < COLS; i++)
					{
						int c = r % 2 == 0 ? i : COLS - 1 - i;
						out.add(r * COLS + c);
					}
				}
				break;
			case SNAKE_COLUMNS:
				for (int c = 0; c < COLS; c++)
				{
					for (int i = 0; i < ROWS; i++)
					{
						int r = c % 2 == 0 ? i : ROWS - 1 - i;
						out.add(r * COLS + c);
					}
				}
				break;
			case DU_SNAKE_ROWS:
				twoPassSnake(out, true, true);
				break;
			case DU_SNAKE_ROWS_LEFT:
				twoPassSnake(out, false, true);
				break;
			case UD_SNAKE_ROWS:
				twoPassSnake(out, true, false);
				break;
			case UD_SNAKE_ROWS_LEFT:
				twoPassSnake(out, false, false);
				break;
			case CUSTOM:
				if (custom != null)
				{
					for (String tok : custom.split("[,\\s]+"))
					{
						try
						{
							int n = Integer.parseInt(tok.trim());
							if (n >= 1 && n <= SIZE)
							{
								out.add(n - 1);
							}
						}
						catch (NumberFormatException ignored)
						{
							// skip junk
						}
					}
				}
				for (int s = 0; s < SIZE; s++)
				{
					out.add(s);
				}
				break;
		}
		return new ArrayList<>(out);
	}

	/**
	 * Two passes over the 7 rows, each covering a pair of columns and snaking left/right row by row. The first pass
	 * starts at the outer edge of its pair; the second pass covers the other pair going the opposite way and
	 * keeps the snake's direction so the path stays continuous (it turns around on the last row).
	 */
	private static void twoPassSnake(Set<Integer> out, boolean rightFirst, boolean downFirst)
	{
		int firstLeftCol = rightFirst ? 2 : 0;
		int secondLeftCol = rightFirst ? 0 : 2;
		boolean leftToRight = !rightFirst; // right-first starts at the right edge, heading left
		for (int pass = 0; pass < 2; pass++)
		{
			boolean down = pass == 0 ? downFirst : !downFirst;
			int leftCol = pass == 0 ? firstLeftCol : secondLeftCol;
			for (int k = 0; k < ROWS; k++)
			{
				int row = down ? k : ROWS - 1 - k;
				int a = row * COLS + leftCol;
				out.add(leftToRight ? a : a + 1);
				out.add(leftToRight ? a + 1 : a);
				leftToRight = !leftToRight;
			}
			leftToRight = !leftToRight; // the last row of a pass is followed by a turn-around, not a flip
		}
	}
}
