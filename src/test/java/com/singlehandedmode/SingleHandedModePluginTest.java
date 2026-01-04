package com.singlehandedmode;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SingleHandedModePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SingleHandedModePlugin.class);
		RuneLite.main(args);
	}
}