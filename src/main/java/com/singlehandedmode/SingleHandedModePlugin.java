package com.singlehandedmode;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.api.ChatMessageType;
import net.runelite.client.game.ItemManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Single-Handed Mode"
)
public class SingleHandedModePlugin extends Plugin
{
	private static final int PIRATE_HOOK_EQUIPMENT_ID = 5045;

	private static final Map<String, List<String>> optionToTargetSubstringList = Map.of(
			// Any rock ("iron rocks", "coal rocks" as well as "rockfall")
			// veins e.g. in MLM
			// rune or daeyalt erssence
			"mine", List.of("rock","ore vein", "essence"),
			"climb", List.of("rocks")
	);

	private static final Set<Integer> BANNED_TWO_HANDED_TOOLS = ImmutableSet.of(
			// Fletching / Crafting
			ItemID.KNIFE,
			ItemID.CHISEL,
			ItemID.NEEDLE,
			ItemID.GLASSBLOWINGPIPE,
			ItemID.BALL_OF_WOOL, // Spinning requires two hands feeding the wheel

			// Firemaking
			ItemID.TINDERBOX,

			// Farming (Heavy Tools)
			ItemID.SPADE,
			ItemID.RAKE,
			ItemID.DIBBER,
			ItemID.GARDENING_TROWEL,

			// Herblore
			ItemID.PESTLE_AND_MORTAR, // Grinding requires holding the bowl + grinding

			// Smithing / Construction
			ItemID.HAMMER, // Often requires holding the nail/object with offhand
			ItemID.POH_SAW    // Sawing is a 2H motion
	);

	@Inject
	private Client client;

	@Inject
	private SingleHandedModeConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ShieldRestrictionOverlay shieldOverlay;

	private int lastEquipmentId = 0;

	@Getter
    private boolean isPiratesHookEquipped = false;

	// Transient flags to allow 1-tick actions
	private int lastShieldRemovalTick = -1;
	private int lastHookEquipTick = -1;

    @Override
	protected void startUp() throws Exception
	{
		overlayManager.add(shieldOverlay);
		// Initial check in case we are already logged in wearing the hook
		// Schedule this to run on the next game tick (Client Thread)
		// since we can't make this check on the startup thread.
//		clientThread.invokeLater(() ->
//		{
//			ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
//			if (equipment != null) {
//				this.isPiratesHookEquipped = equipment.contains(ItemID.PIRATEHOOK);
//				if (this.isPiratesHookEquipped) {
//					log.debug("Logging in with pirate's hook equipped!");
//				}
//			}
//		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Example stopped!");
		enableHands();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
		}
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		disableHands();
	}

	private void disableHands() {
		toggleHands(true);
	}

	private void enableHands() {
		toggleHands(false);
	}

	private void toggleHands(boolean disable) {
		Player player = client.getLocalPlayer();
		if (player == null) return;

		// Get the player's "Paper Doll" definition
		PlayerComposition playerComposition = player.getPlayerComposition();
		if (playerComposition == null) return;

		int[] equipmentIds = playerComposition.getEquipmentIds();

		if (disable && !this.isPiratesHookEquipped) {
			int armIds = equipmentIds[KitType.HANDS.getIndex()];
			if (armIds != 0) {
				lastEquipmentId = equipmentIds[KitType.HANDS.getIndex()];
				log.debug("last equipment ID is " + lastEquipmentId);
				equipmentIds[KitType.HANDS.getIndex()] = 0;
			}
		} else if (this.isPiratesHookEquipped) {
			equipmentIds[KitType.HANDS.getIndex()] = PIRATE_HOOK_EQUIPMENT_ID;
		} else {
			equipmentIds[KitType.HANDS.getIndex()] = lastEquipmentId;
		}

		playerComposition.setHash();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// 1. Filter: We only care about Equipment changes
		if (event.getContainerId() != InventoryID.EQUIPMENT.getId())
		{
			return;
		}

		// 2. Check for the Hook
		ItemContainer equipment = event.getItemContainer();

		// Safety check: Container might be null on login
		if (equipment != null)
		{
			// Check if the equipment container has the Pirate's Hook
			// (Item ID 2997 is the standard Brimhaven Agility Hook)
			this.isPiratesHookEquipped = equipment.contains(ItemID.PIRATEHOOK);
			if (this.isPiratesHookEquipped) {
				log.debug("Pirate's hook equipped!");
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{

		log.debug("Menu option clicked!");
		// --- PHASE 1: PRE-CALCULATE INTENT ---
		// Before blocking anything, let's see if this click is a "Good Action"
		// that should enable future clicks in this same tick.
		int currentTick = client.getTickCount();

		// Check if this click is REMOVING a Shield/2H
		if (isShieldRemovalInteraction(event))
		{
			log.debug("Pending shield removal");
			lastShieldRemovalTick = currentTick;
			// We don't return; we still let the event process naturally
		}

		// Check if this click is EQUIPPING the Hook
		if (isHookEquipInteraction(event))
		{
			log.debug("Pending hook equip");
			lastHookEquipTick = currentTick;
		}

		// 1. Check for Hook REMOVAL (The "Safety Lock")
		// If we are trying to take the hook off, we must ensure hands are empty.
		if (isHookRemovalInteraction(event))
		{
			boolean recentlyRemovedShield = currentTick == lastShieldRemovalTick;
			if (isHoldingIllegalItem() && !recentlyRemovedShield)
			{
				event.consume();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"<col=ff0000>You cannot remove your hook while you're using it.", null);
			}
			return; // We handled the removal logic, no need to check equipping logic.
		}

		// 1. Filter: We only care about "Equip" or "Wield" or "Wear" options.
		String option = Text.removeTags(event.getMenuOption()).toLowerCase();
		String target = Text.removeTags(event.getMenuTarget()).toLowerCase();

		// 2. Permission Check: If the Hook is equipped, you are allowed to use offhands!
		boolean recentlyEquippedHook = currentTick == lastHookEquipTick;
		if (this.isPiratesHookEquipped || recentlyEquippedHook)
		{
			return;
		}

		if (isTwoHandedAction(event) || isTwoHandedUsage(event)) {
			event.consume();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=ff0000>You need two hands to do that properly!", null);
			return;
		}

		// Using a set of standard equip strings to be robust
		if (!option.equalsIgnoreCase("Wield")
				&& !option.equalsIgnoreCase("Wear")
				&& !option.equalsIgnoreCase("Equip")
				&& !option.equalsIgnoreCase("Hold"))
		{
			return;
		}

		// 3. Get the Item ID being clicked
		// The itemId in the event might be -1 for some widgets, so we handle that.
		int itemId = event.getItemId();

		// Sometimes the ID in the event is just the Widget ID.
		// If itemId is -1, we might need to look up the widget, but for Inventory clicks
		// the itemId is usually populated correctly.
		if (itemId == -1) return;

		// 4. Determine where the item goes
		ItemComposition itemDef = itemManager.getItemComposition(itemId);
		if (itemDef == null) return;

		// In RuneLite API, getting the specific "Equipment Slot" index can be tricky
		// because it's sometimes stored in 'ItemStats' rather than 'ItemComposition'.
		// However, we can use a helper or check the cache values if available.

		// RELIABLE METHOD: Use ItemManager's ItemStats (requires runelite-client access)
		// If that's unavailable, we have to check the integer 'slot' params.
		// For now, let's assume standard ItemStats usage:

		// Note: The slot ID for Shield is 5.
		// However, ItemComposition often doesn't expose ".getSlot()" directly in the public interface.
		// We check via ItemEquipmentStats if available, OR we rely on a simplified check.

		// Let's use the robust 'ItemStats' lookup:
		int equipmentSlot = -1;
		try
		{
			// We verify if the item fits in the Shield Slot (Index 5)
			// Note: Some 2H weapons (Slot 3) also 'Block' Slot 5,
			// but strictly 'Shield' items go into Slot 5.

			// Since the API for getting slots changes often, here is the
			// robust fallback using the internal integer parameter 452 (Equip Slot).
			// 5 = Shield.
			// 3 = Weapon.

			// Using 'getItemStats' is preferred if available:
			// equipmentSlot = itemManager.getItemStats(itemId, false).getEquipment().getSlot();

			// BUT, since we can't guarantee you have the ItemStats library linked:
			// We will check standard Equipment Slot logic or name-matching if needed.
			// Actually, let's trust that you are restricting *Shields*.
			// Most Shields have a "Wield" option.
			// Let's try to get the stats.

			equipmentSlot = getEquipmentSlot(itemId);
		}
		catch (Exception e)
		{
			// If we can't determine the slot, we let it through to avoid bugs.
			return;
		}

		// 5. The Ban Hammer
		// EquipmentInventorySlot.SHIELD is index 5.
		if (equipmentSlot == EquipmentInventorySlot.SHIELD.getSlotIdx())
		{
			// BLOCK IT!
			event.consume(); // Cancel the click

			// Tell the user why
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=ff0000>You would need some sort of prosthetic hook to hold that.", null);
		}

		// Check for 2H Weapon
		// Slot 3 is Weapon.
		if (equipmentSlot == EquipmentInventorySlot.WEAPON.getSlotIdx())
		{
			// Check if it's 2-Handed
			var stats = itemManager.getItemStats(itemId, false);
			if (stats != null && stats.getEquipment() != null && stats.getEquipment().isTwoHanded())
			{
				event.consume();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						"<col=ff0000>You can't wield a two-handed weapon with one hand!", null);
			}
		}
	}

	/**
	 * Determines if an interaction requires two hands based on the verb or target.
	 */
	private boolean isTwoHandedAction(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).toLowerCase();
		String target = Text.removeTags(event.getMenuTarget()).toLowerCase();
		log.debug("isTwoHandedAction. Option: " + option + ", target: " + target);

		// --- MINING ---
		// Block "Mine" on anything (Rocks, Veins, Essence)
        switch (option) {
            case "mine":
                return true;
            // --- SMITHING ---
            // Block "Smith" (Anvils)
            case "smith":
                return true;
            // --- FISHING ---
            // Block "Lure", "Bait", "Net".
            // Allow "Harpoon", "Cage" (maybe?), "Small Net" (up to you)
            case "lure":
            case "bait":
			case "net":
                return true;
            // --- FARMING ---
            // Block "Rake" and "Harvest" (if digging is involved)
            case "rake":
                return true;
        }

        // --- AGILITY (Specific Obstacles) ---
		// This is tricky because "Climb" is used for both 1H ladders and 2H walls.
		// We target specific object names or specific verbs.

		// Block Monkey Bars
		if (option.contains("swing-across") && target.contains("monkey bars"))
		{
			return true;
		}

		// Block Ropes (Swing)
		if (option.contains("swing") && target.contains("rope"))
		{
			return true;
		}
		// Rope swing or ropeswing
		if (option.contains("swing-on") && target.contains("rope")) {
			return true;
		}
		// Ladders are allowed
		if (option.contains("climb") && !target.contains("ladder") && !target.contains("crumbling wall")) {
			return true;
		}

		if (option.contains("climb-across") && target.contains("hand holds")) {
			return true;
		}

		// Waterfall quest
		if (option.contains("use") && target.contains("rope -> rock")) {
			log.debug("Doing waterfall!");
			return true;
		}

		return false;
	}

	private boolean isTwoHandedUsage(MenuOptionClicked event)
	{
		log.debug("isTwoHandedUsage");
		MenuAction action = event.getMenuAction();

		// Case 1: Using an Item on another Item (Inventory -> Inventory)
		// Case 2: Using an Item on a Game Object (Inventory -> World)
		// Case 3: Using an Item on a Ground Item (Inventory -> Floor)
		if (action == MenuAction.WIDGET_TARGET_ON_WIDGET ||
				action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
				action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
		{
			// 1. Identify the item we "Selected" first (The cursor is holding it)
			int selectedItemId = -1;
			if (client.getSelectedWidget() != null)
			{
				selectedItemId = client.getSelectedWidget().getItemId();
			}

			// 2. Identify the item we are clicking "Target" on right now
			// For WIDGET_TARGET_ON_WIDGET, event.getItemId() is the target.
			// For Objects, we don't care about the object ID, only the tool we are holding.
			int targetItemId = event.getItemId();

			log.debug("selected item id: " + selectedItemId + " target item id: " + targetItemId);

			// 3. CHECK: Is the "Active Tool" (Selected Item) banned?
			if (BANNED_TWO_HANDED_TOOLS.contains(selectedItemId))
			{
				return true;
			}

			// 4. CHECK REVERSE: Did we click the Tool *onto* the Material?
			// (Only matters for Item-on-Item. e.g. clicking Log then Knife)
			if (action == MenuAction.WIDGET_TARGET_ON_WIDGET && BANNED_TWO_HANDED_TOOLS.contains(targetItemId))
			{
				return true;
			}
		}

		// Special Case: "Use" - Selecting the tool initially
		// Optional: You can block the initial "Use" click on the tool itself if you want strictness.
		// However, allowing the selection but blocking the *result* is usually better UX.
		// If you want to block even selecting the tool:
    /*
    if (action == MenuAction.CC_OP && event.getMenuOption().equalsIgnoreCase("Use"))
    {
        if (BANNED_TWO_HANDED_TOOLS.contains(event.getItemId())) return true;
    }
    */

		return false;
	}

	// Helper method to find slot
	private int getEquipmentSlot(int itemId)
	{
		// Try to get stats from ItemManager
		// This depends on if 'ItemStats' are available in your build.
		// If this errors, we might need to rely on 'ItemComposition.isStackable' etc
		// or internal params.

		// Assuming you have access to ItemStats (standard in example-plugin):
		var stats = itemManager.getItemStats(itemId, false);
		if (stats != null && stats.getEquipment() != null)
		{
			return stats.getEquipment().getSlot();
		}
		return -1;
	}

	private boolean isHookRemovalInteraction(MenuOptionClicked event)
	{
		// 1. The Source of Truth
		// If we aren't wearing the hook, we logically cannot be removing it.
		if (!this.isPiratesHookEquipped) return false;

		String option = event.getMenuOption();

		// Case A: The "Remove" Button (Equipment Tab)
		// We strictly check the Name to distinguish "Removing Hook" from "Removing Boots"
		if (option.equalsIgnoreCase("Remove"))
		{
			String target = Text.removeTags(event.getMenuTarget());
			return target.equals("Pirate's hook");
		}

		// Case B: The "Swap" Action (Inventory)
		// Here we DO verify the slot directly, because we have the Item ID!
		if (option.equalsIgnoreCase("Wear")
				|| option.equalsIgnoreCase("Wield")
				|| option.equalsIgnoreCase("Equip"))
		{
			int newItemId = event.getItemId();
			if (newItemId > -1)
			{
				// If I am wearing the hook, and I try to put on something else
				// that goes in the glove slot, I am implicitly removing the hook.
				return getEquipmentSlot(newItemId) == EquipmentInventorySlot.GLOVES.getSlotIdx();
			}
		}

		return false;
	}

	// Helper: Is this click going to remove the shield?
	private boolean isShieldRemovalInteraction(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		int itemId = event.getItemId();

		// 1. Direct "Remove" on Shield Slot
		if (option.equalsIgnoreCase("Remove"))
		{
			// Using the Text Check method since Widget IDs are flaky
			String target = Text.removeTags(event.getMenuTarget());
			// We need to know if the target name corresponds to the item in the shield slot.
			// This is tricky. A simpler way is checking the slot index if possible,
			// OR checking if the item in Slot 5 matches the text.

			// Let's use the Widget Check again, but safer:
			Widget widget = client.getWidget(event.getParam1());
			if (widget != null)
			{
				// If we clicked the Shield Slot (Child 38 in Group 387 usually, or index 5)
				// We can check if the widget IS the shield slot.
				// But simpler: Check if the text matches the currently equipped shield.
				ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
				if (equipment != null)
				{
					var shield = equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx());
					var weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());

					// Check Shield Slot
					if (shield != null)
					{
						String shieldName = itemManager.getItemComposition(shield.getId()).getName();
						if (target.equals(shieldName)) return true;
					}

					// Check 2H Weapon (Unequipping a 2H counts as freeing the offhand)
					if (weapon != null)
					{
						var stats = itemManager.getItemStats(weapon.getId(), false);
						if (stats != null && stats.getEquipment().isTwoHanded())
						{
							String weaponName = itemManager.getItemComposition(weapon.getId()).getName();
							if (target.equals(weaponName)) return true;
						}
					}
				}
			}
		}

		// 2. Unequipping by Swapping (Equipping a 1-handed weapon to replace a 2H)
		// This frees up the "Offhand" slot technically?
		// No, usually equipping a main-hand keeps the offhand empty.
		// But equipping a SHIELD removes a 2H.
		// This logic gets complex. For now, let's stick to EXPLICIT removal.

		return false;
	}

	// Helper: Is this click equipping the hook?
	private boolean isHookEquipInteraction(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option.equalsIgnoreCase("Wear") || option.equalsIgnoreCase("Wield") || option.equalsIgnoreCase("Equip"))
		{
			return event.getItemId() == ItemID.PIRATEHOOK;
		}
		return false;
	}

	// Helper: Check if we are holding a Shield or 2H Weapon
	private boolean isHoldingIllegalItem()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT); // ID 94
		if (equipment == null) return false;

		// Check Slot 5 (Shield)
		if (equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx()) != null)
		{
			return true;
		}

		// Check Slot 3 (Weapon) for 2H
		// We need to check if the CURRENT weapon is 2-Handed
		// (You can omit this if you trust yourself, but for completeness:)
		var weaponItem = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weaponItem != null)
		{
			var stats = itemManager.getItemStats(weaponItem.getId(), false);
			if (stats != null && stats.getEquipment().isTwoHanded())
			{
				return true;
			}
		}

		return false;
	}

	@Provides
	SingleHandedModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SingleHandedModeConfig.class);
	}
}
