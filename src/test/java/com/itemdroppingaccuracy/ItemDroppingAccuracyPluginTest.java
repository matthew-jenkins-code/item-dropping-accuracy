package com.itemdroppingaccuracy;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ItemDroppingAccuracyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ItemDroppingAccuracyPlugin.class);
		RuneLite.main(args);
	}
}
