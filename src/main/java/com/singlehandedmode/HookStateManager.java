package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class HookStateManager
{
    public static final int PIRATE_HOOK_ID = ItemID.PIRATEHOOK; // or 5045 if using custom
    private final Client client;
    private final ItemManager itemManager;

    @Getter
    private boolean isPiratesHookEquipped = false;

    // Transient tick tracking
    private int lastShieldRemovalTick = -1;
    private int lastHookEquipTick = -1;

    @Inject
    public HookStateManager(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    public void onGameTick()
    {
        // Optional: Periodic sync if needed, but ItemContainerChanged is usually enough
    }

    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.EQUIPMENT.getId()) return;

        ItemContainer equipment = event.getItemContainer();
        if (equipment != null)
        {
            boolean newState = equipment.contains(PIRATE_HOOK_ID);
            if (newState != isPiratesHookEquipped)
            {
                isPiratesHookEquipped = newState;
                log.debug("Hook state changed: " + isPiratesHookEquipped);
            }
        }
    }

    /**
     * Call this at the start of onMenuOptionClicked to update intent timers.
     */
    public void captureClickIntent(MenuOptionClicked event)
    {
        int currentTick = client.getTickCount();

        if (isShieldRemovalInteraction(event))
        {
            lastShieldRemovalTick = currentTick;
        }

        if (isHookEquipInteraction(event))
        {
            lastHookEquipTick = currentTick;
        }
    }

    public boolean wasShieldJustRemoved()
    {
        return client.getTickCount() == lastShieldRemovalTick;
    }

    public boolean wasHookJustEquipped()
    {
        return client.getTickCount() == lastHookEquipTick;
    }

    // --- Internal Helpers ---

    private boolean isHookEquipInteraction(MenuOptionClicked event)
    {
        String option = event.getMenuOption();
        return (option.equalsIgnoreCase("Wear") || option.equalsIgnoreCase("Wield") || option.equalsIgnoreCase("Equip"))
                && event.getItemId() == PIRATE_HOOK_ID;
    }

    private boolean isShieldRemovalInteraction(MenuOptionClicked event)
    {
        String option = event.getMenuOption();

        // 1. Direct "Remove" on Shield Slot
        if (option.equalsIgnoreCase("Remove"))
        {
            String target = Text.removeTags(event.getMenuTarget());
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null)
            {
                var shield = equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx());
                var weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());

                if (shield != null)
                {
                    String shieldName = itemManager.getItemComposition(shield.getId()).getName();
                    if (target.equals(shieldName)) return true;
                }

                // Removing a 2H weapon also counts as freeing the offhand
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
        return false;
    }
}