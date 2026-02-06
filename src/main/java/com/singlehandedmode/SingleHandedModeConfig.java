package com.singlehandedmode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SingleHandedModeConfig.GROUP)
public interface SingleHandedModeConfig extends Config
{
	String GROUP = "singlehandedmode";

	// =========================================================
	// SECTION 1: VISUALS
	// =========================================================
	@ConfigSection(
			name = "Player Model Amputation",
			description = "Settings for the visual removal of your limb.",
			position = 0
	)
	String sectionVisuals = "sectionVisuals";

	@ConfigItem(
			keyName = "shouldRemoveLimb",
			name = "Visually Remove Limb",
			description = "If enabled, the limb will be invisible when the hook is not equipped.",
			position = 1,
			section = sectionVisuals
	)
	default boolean shouldRemoveLimb() { return true; }

	@ConfigItem(
			keyName = "amputationLevel",
			name = "Amputation Level",
			description = "Choose where the limb is removed.",
			position = 2,
			section = sectionVisuals
	)
	default AmputationLevel amputationLevel() { return AmputationLevel.TRANSRADIAL; }

	enum AmputationLevel { TRANSRADIAL, SHOULDER_DISARTICULATION }

	// =========================================================
	// SECTION 2: SOCIAL
	// =========================================================
	@ConfigSection(
			name = "NPC Behavior",
			description = "Settings for NPC interactions and dialogue.",
			position = 10
	)
	String sectionAbleism = "sectionAbleism";

	@ConfigItem(
			keyName = "enableAbleism",
			name = "Enable Ableism",
			description = "Allow NPCs to make comments about your missing hand.",
			position = 11,
			section = sectionAbleism
	)
	default boolean enableAbleism() { return true; }

	@Range(min = 1, max = 100)
	@ConfigItem(
			keyName = "ableismLevel",
			name = "Ableism Level",
			description = "Controls harassment frequency. 1 = Rare (~5 mins). 100 = Every Tick.",
			position = 12,
			section = sectionAbleism
	)
	default int ableismLevel() { return 10; }

	// =========================================================
	// SECTION 3: RESTRICTIONS WITH NO HOOK
	// =========================================================
	@ConfigSection(
			name = "Disabled without Pirate's Hook",
			description = "Rules applied when you have NO pirate's hook equipped.",
			position = 20
	)
	String sectionNoHook = "sectionNoHook";

	@ConfigItem(
			keyName = "disableShieldsNoHook",
			name = "Shield Slot",
			description = "Prevent wearing anything in the shield slot.",
			position = 21,
			section = sectionNoHook
	)
	default boolean disableShieldsNoHook() { return true; }

	@ConfigItem(
			keyName = "disable2HWeaponsNoHook",
			name = "Two-handed Weapons",
			description = "Prevent wearing two-handed weapons.",
			position = 22,
			section = sectionNoHook
	)
	default boolean disable2HWeaponsNoHook() { return true; }

	@ConfigItem(
			keyName = "disableMining",
			name = "Mining",
			description = "Prevent mining rocks. Though pickaxe is a single-handed weapon for combat, the mining animation uses both hands",
			position = 41,
			section = sectionNoHook
	)
	default boolean disableMining() { return true; }

	@ConfigItem(
			keyName = "disableAnvilSmithing",
			name = "Anvil Smithing",
			description = "Prevent smithing at anvils. Smelting still allowed",
			position = 42,
			section = sectionNoHook
	)
	default boolean disableSmithing() { return true; }

	@ConfigItem(
			keyName = "disableFletching",
			name = "Two-handed Knife Usage",
			description = "Prevent two-handed usage of knives, (when another hand needs to hold something, e.g. a log for fletching).",
			position = 43,
			section = sectionNoHook
	)
	default boolean disableFletching() { return true; }

	@ConfigItem(
			keyName = "disableCrafting",
			name = "Crafting Tools",
			description = "Prevent two-handed usage of crafting tools (chisel, needle, glassblowing Pipe).",
			position = 48,
			section = sectionNoHook
	)
	default boolean disableCrafting() { return true; }

	@ConfigItem(
			keyName = "disableFishing",
			name = "Two-handed Fishing Methods",
			description = "Prevent two-handed fishing methods (Net, Rod). Harpoon and bare-handed still allowed.",
			position = 44,
			section = sectionNoHook
	)
	default boolean disableFishing() { return true; }

	@ConfigItem(
			keyName = "disableFarming",
			name = "Two-handed Farming Tools",
			description = "Prevent usage of two-handed farming tools (Spade, Rake).",
			position = 45,
			section = sectionNoHook
	)
	default boolean disableFarming() { return true; }

	@ConfigItem(
			keyName = "disableConstruction",
			name = "Saw",
			description = "Prevent usage of saws, needed for most construction and various other uses.",
			position = 49,
			section = sectionNoHook
	)
	default boolean disableConstruction() { return true; }

	@ConfigItem(
			keyName = "disableFiremaking",
			name = "Tinderbox",
			description = "Prevent usage of a tinderbox, needed to start fires.",
			position = 50,
			section = sectionNoHook
	)
	default boolean disableFiremaking() { return true; }

	@ConfigItem(
			keyName = "disablePestleAndMortar",
			name = "Pestle and Mortar",
			description = "Prevent usage of a pestle and mortar.",
			position = 51,
			section = sectionNoHook
	)
	default boolean disablePestleAndMortar() { return true; }

	@ConfigItem(
			keyName = "disableAgilityObstacles",
			name = "Two-Handed Agility Obstacles",
			description = "Prevent two-handed obstacles (rope swings, Monkey Bars, Hand Holds).",
			position = 46,
			section = sectionNoHook
	)
	default boolean disableAgilityObstacles() { return true; }

	@ConfigItem(
			keyName = "disableClimbingUpRopes",
			name = "Climbing up Ropes",
			description = "Prevents climbing up any rope (climbing down is still allowed).",
			position = 47,
			section = sectionNoHook
	)
	default boolean disableClimbingUpRopes() { return true; }

	@ConfigItem(
			keyName = "disableLadders",
			name = "Ladders",
			description = "Prevent climbing standard ladders.",
			position = 47,
			section = sectionNoHook
	)
	default boolean disableLadders() { return false; } // Default off

	// =========================================================
	// SECTION 4: RESTRICTIONS WITH HOOK
	// =========================================================
	@ConfigSection(
			name = "Disabled Even With Hook",
			description = "Rules applied even when you HAVE the Pirate's Hook equipped.",
			position = 30
	)
	String sectionWithHook = "sectionWithHook";

	@ConfigItem(
			keyName = "disableGrippedOffhands",
			name = "Gripped Offhands",
			description = "Prevent holding items that require fingers (Defenders, Imcando Hammer, etc).",
			position = 31,
			section = sectionWithHook
	)
	default boolean disableGrippedOffhands() { return true; }

	@ConfigItem(
			keyName = "disableDualWielding",
			name = "Dual-Wielded Weapons",
			description = "Prevent weapons that visually fill both hands (Claws, Torag's Hammers).",
			position = 32,
			section = sectionWithHook
	)
	default boolean disableDualWielding() { return true; }

	@ConfigItem(
			keyName = "disableBows",
			name = "Bows",
			description = "Prevent using Bows (fingers needed to draw string). Crossbows are allowed.",
			position = 33,
			section = sectionWithHook
	)
	default boolean disableBows() { return true; }
	// =========================================================
	// SECTION 5: ECONOMY (INSURANCE)
	// =========================================================
	@ConfigSection(
			name = "Health Insurance",
			description = "Settings for hook durability and repair costs.",
			position = 50
	)
	String sectionEconomy = "sectionEconomy";

	@ConfigItem(
			keyName = "hookDurabilityTicks",
			name = "Durability (Ticks)",
			description = "How many Ticks the hook lasts before breaking.",
			position = 51,
			section = sectionEconomy
	)
	default int hookDurabilityTicks() { return 60000; }

	@ConfigItem(
			keyName = "repairCost",
			name = "Deductible (GP)",
			description = "Amount of GP you must DROP to repair the hook.",
			position = 52,
			section = sectionEconomy
	)
	default int repairCost() { return 1000000; }
	@ConfigItem(
			keyName = "penaltyPerSecond",
			name = "Late Fee (GP/Sec)",
			description = "Additional GP fine per second if you keep wearing a broken hook.",
			position = 53,
			section = sectionEconomy
	)
	default int penaltyPerSecond() { return 100; } // 100 gp/sec = 6k gp/min

	// --- HIDDEN PERSISTENCE KEYS ---
	// We do not add these to a 'section' or give them names,
	// but we use the ConfigManager to read/write them programmatically.
	@ConfigItem(
			keyName = "currentWearTicks",
			name = "current wear ticks",
			description = "",
//			hidden = true
	position = 54,
	section = sectionEconomy
	)
	default int currentWearTicks() { return 0; }
	@ConfigItem(
			keyName = "accumulatedDebt",
			name = "accumulated debt",
			description = "",
//			hidden = true
	position = 55,
	section = sectionEconomy
	)
	default int accumulatedDebt() { return 0; }
}