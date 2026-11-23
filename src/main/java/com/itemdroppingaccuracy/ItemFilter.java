package com.itemdroppingaccuracy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Matches items against a comma separated list of names (case-insensitive, * wildcard) or item ids. */
public class ItemFilter
{
	private final List<Integer> ids = new ArrayList<>();
	private final List<Pattern> names = new ArrayList<>();

	public ItemFilter(String csv)
	{
		if (csv == null)
		{
			return;
		}
		for (String raw : csv.split(","))
		{
			String tok = raw.trim().toLowerCase();
			if (tok.isEmpty())
			{
				continue;
			}
			if (tok.matches("\\d+"))
			{
				try
				{
					ids.add(Integer.parseInt(tok));
				}
				catch (NumberFormatException ignored)
				{
					// too large, ignore
				}
				continue;
			}
			names.add(Pattern.compile(glob(tok)));
		}
	}

	private static String glob(String tok)
	{
		StringBuilder sb = new StringBuilder();
		StringBuilder lit = new StringBuilder();
		for (char c : tok.toCharArray())
		{
			if (c == '*')
			{
				if (lit.length() > 0)
				{
					sb.append(Pattern.quote(lit.toString()));
					lit.setLength(0);
				}
				sb.append(".*");
			}
			else
			{
				lit.append(c);
			}
		}
		if (lit.length() > 0)
		{
			sb.append(Pattern.quote(lit.toString()));
		}
		return sb.toString();
	}

	public boolean isEmpty()
	{
		return ids.isEmpty() && names.isEmpty();
	}

	public boolean matches(int itemId, String name)
	{
		if (ids.contains(itemId))
		{
			return true;
		}
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase();
		for (Pattern p : names)
		{
			if (p.matcher(n).matches())
			{
				return true;
			}
		}
		return false;
	}
}
