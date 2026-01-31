package com.singlehandedmode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("singlehandedmode")
public interface SingleHandedModeConfig extends Config
{
	@ConfigItem(
			keyName = "shouldRemoveLimb",
			name = "Visually Remove Limb",
			description = "If enabled, the limb will be invisible when the hook is not equipped. If disabled, the limb looks normal but you are still restricted.",
			position = 1
	)
	default boolean shouldRemoveLimb()
	{
		return true;
	}

	@ConfigItem(
			keyName = "amputationLevel",
			name = "Amputation Level",
			description = "Choose where the limb is removed, either at the forearm (transradial) or at the shoulder (shoulder disarticulation).",
			position = 2
	)
	default AmputationLevel amputationLevel()
	{
		return AmputationLevel.TRANSRADIAL;
	}

	enum AmputationLevel
	{
		TRANSRADIAL,
		SHOULDER_DISARTICULATION
	}

	@ConfigItem(
			keyName = "enableAbleism",
			name = "Enable Ableism",
			description = "Allow NPCs to make comments about your missing hand.",
			position = 3
	)
	default boolean enableAbleism()
	{
		return true;
	}

	@Range(
			min = 1,
			max = 100
	)
	@ConfigItem(
			keyName = "ableismLevel",
			name = "Ableism Level",
			description = "Controls harassment frequency. 1 = Rare (~5 mins). 80+ = Overlap Mode. 100 = Every Tick.",
			position = 4
	)
	default int ableismLevel()
	{
		return 10;
	}
}