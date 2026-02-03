package com.singlehandedmode;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

@Singleton
public class EquipmentRestrictionManager
{
    private final Client client;
    private final ItemManager itemManager;
    private final HookStateManager hookState;
    private final SingleHandedModeConfig config;

    private static final Set<String> BANNED_OFFHAND_KEYWORDS = ImmutableSet.of(
            "defender", "book", "torch", "lantern", "hammer", "orb", "chalice", "skull", "head", "tankard", "cane"
    );

    private static final Set<String> DUAL_WIELD_KEYWORDS = ImmutableSet.of(
            "torag's hammers", "macuahuitl", "claws", "boxing", "knuckles"
    );

    @Inject
    public EquipmentRestrictionManager(Client client, ItemManager itemManager, HookStateManager hookState, SingleHandedModeConfig config)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.hookState = hookState;
        this.config = config;
    }

    public void checkRestrictions(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        if (!option.equals("wield") && !option.equals("wear") && !option.equals("equip") && !option.equals("hold"))
        {
            return;
        }

        int itemId = event.getItemId();
        if (itemId == -1) return;

        int equipmentSlot = getEquipmentSlot(itemId);
        if (equipmentSlot == -1) return;

        boolean isHookEquipped = hookState.isPiratesHookEquipped() || hookState.wasHookJustEquipped();

        if (isHookEquipped)
        {
            checkHookEquippedRestrictions(event, itemId, equipmentSlot);
        }
        else
        {
            checkNoHookRestrictions(event, itemId, equipmentSlot);
        }
    }

    private void checkNoHookRestrictions(MenuOptionClicked event, int itemId, int slot)
    {
        // 1. Disable Shield Slot
        if (config.disableShieldsNoHook() && slot == EquipmentInventorySlot.SHIELD.getSlotIdx())
        {
            blockEvent(event, "You need a prosthetic to hold an offhand item.");
            return;
        }

        // 2. Disable 2H Weapons
        if (config.disable2HWeaponsNoHook() && slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            if (isTwoHanded(itemId))
            {
                blockEvent(event, "You can't wield a two-handed weapon with one hand!");
            }
        }
    }

    private void checkHookEquippedRestrictions(MenuOptionClicked event, int itemId, int slot)
    {
        // 1. Disable Gripped Offhands
        if (config.disableGrippedOffhands() && slot == EquipmentInventorySlot.SHIELD.getSlotIdx())
        {
            if (nameContainsKeyword(itemId, BANNED_OFFHAND_KEYWORDS))
            {
                blockEvent(event, "You cannot grip that item with a hook (no fingers!).");
            }
        }

        // 2. Disable Dual Wielding
        if (config.disableDualWielding() && slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            if (nameContainsKeyword(itemId, DUAL_WIELD_KEYWORDS))
            {
                blockEvent(event, "You can't dual-wield weapons with a hook!");
            }
        }

        // 3. Disable Bows (String drawing requires fingers)
        if (config.disableBows() && slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            String name = itemManager.getItemComposition(itemId).getName().toLowerCase();
            // Allow Crossbows, Ballistas, etc. Ban standard bows.
            boolean isBow = name.contains("bow") && !name.contains("crossbow") && !name.contains("ballista") && !name.contains("crystal bow");
            // Crystal bow is technically magic? Up to you.
            // Standard "Shortbow", "Longbow", "Composite Bow", "Seercull", "Twisted Bow".

            if (isBow)
            {
                blockEvent(event, "You cannot draw a bowstring with a hook.");
            }
        }
    }

    // ... (Helpers: checkHookRemoval, isHookRemovalInteraction, isHoldingIllegalItem, blockEvent, getEquipmentSlot, nameContainsKeyword remain same) ...
    // Note: I omitted repeating them to save space, but ensure 'isTwoHanded' helper is present:

    private boolean isTwoHanded(int itemId)
    {
        var stats = itemManager.getItemStats(itemId, false);
        return stats != null && stats.getEquipment() != null && stats.getEquipment().isTwoHanded();
    }

    // ... existing helpers ...

    private void blockEvent(MenuOptionClicked event, String message)
    {
        event.consume();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>" + message, null);
    }

    private int getEquipmentSlot(int itemId)
    {
        var stats = itemManager.getItemStats(itemId, false);
        return (stats != null && stats.getEquipment() != null) ? stats.getEquipment().getSlot() : -1;
    }

    private boolean nameContainsKeyword(int itemId, Set<String> keywords)
    {
        ItemComposition def = itemManager.getItemComposition(itemId);
        if (def == null) return false;
        String name = def.getName().toLowerCase();

        // Exception for Shields in banned list
        if (keywords == BANNED_OFFHAND_KEYWORDS && name.contains("shield")) return false;

        return keywords.stream().anyMatch(name::contains);
    }

    public void checkHookRemoval(MenuOptionClicked event) {
        if (!hookState.isPiratesHookEquipped()) return;

        if (isHookRemovalInteraction(event))
        {
            if (isHoldingIllegalItem() && !hookState.wasShieldJustRemoved())
            {
                blockEvent(event, "You cannot remove your hook while you're using it.");
            }
        }
    }

    private boolean isHookRemovalInteraction(MenuOptionClicked event)
    {
        String option = event.getMenuOption();
        if (option.equalsIgnoreCase("Remove"))
        {
            return Text.removeTags(event.getMenuTarget()).equalsIgnoreCase("Pirate's hook");
        }
        // Check implicit removal by swapping gloves
        if (option.equalsIgnoreCase("Wear") || option.equalsIgnoreCase("Wield"))
        {
            return getEquipmentSlot(event.getItemId()) == EquipmentInventorySlot.GLOVES.getSlotIdx();
        }
        return false;
    }

    private boolean isHoldingIllegalItem()
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) return false;

        if (equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx()) != null) return true;

        var weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        if (weapon != null)
        {
            var stats = itemManager.getItemStats(weapon.getId(), false);
            return stats != null && stats.getEquipment().isTwoHanded();
        }
        return false;
    }
}