package com.spoonmeter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Runs a RuneLite client with this plugin loaded, for development. */
public class SpoonMeterPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SpoonMeterPlugin.class);
		RuneLite.main(args);
	}
}
