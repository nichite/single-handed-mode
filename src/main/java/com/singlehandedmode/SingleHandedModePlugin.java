package com.singlehandedmode;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.api.ChatMessageType;
import net.runelite.client.game.ItemManager;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "SingleHandedMode"
)
public class SingleHandedModePlugin extends Plugin
{
	private static final int PIRATE_HOOK_EQUIPMENT_ID = 5045;

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

    @Override
	protected void startUp() throws Exception
	{
		overlayManager.add(shieldOverlay);
		// Initial check in case we are already logged in wearing the hook
		// Schedule this to run on the next game tick (Client Thread)
		// since we can't make this check on the startup thread.
		clientThread.invokeLater(() ->
		{
			ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
			if (equipment != null) {
				this.isPiratesHookEquipped = equipment.contains(ItemID.PIRATEHOOK);
				if (this.isPiratesHookEquipped) {
					log.debug("Logging in with pirate's hook equipped!");
				}
			}
		});
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
		// 1. Filter: We only care about "Equip" or "Wield" or "Wear" options.
		String option = event.getMenuOption();
		// Using a set of standard equip strings to be robust
		if (!option.equalsIgnoreCase("Wield")
				&& !option.equalsIgnoreCase("Wear")
				&& !option.equalsIgnoreCase("Equip")
				&& !option.equalsIgnoreCase("Hold"))
		{
			return;
		}

		// 2. Permission Check: If the Hook is equipped, you are allowed to use offhands!
		if (this.isPiratesHookEquipped)
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
					"<col=ff0000>You need a prosthetic hook to hold that!", null);
		}
		// Inside onMenuOptionClicked...

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
						"<col=ff0000>You can't wield a two-handed weapon with one arm!", null);
			}
		}
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

	@Provides
	SingleHandedModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SingleHandedModeConfig.class);
	}
}
