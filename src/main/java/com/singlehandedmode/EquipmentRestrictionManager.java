package com.singlehandedmode;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.Text;

@Singleton
public class EquipmentRestrictionManager
{
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private HookStateManager hookState;

    @Inject
    private DurabilityManager durabilityManager;

    @Inject
    private SingleHandedModeConfig config;

    private static final Set<String> BANNED_OFFHAND_KEYWORDS = ImmutableSet.of(
            "defender", "book", "torch", "lantern", "hammer", "orb", "chalice", "skull", "head", "tankard", "cane"
    );

    private static final Set<String> DUAL_WIELD_KEYWORDS = ImmutableSet.of(
            "torag's hammers", "macuahuitl", "claws", "boxing", "knuckles"
    );

    public void checkRestrictions(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();

        if (!option.equals("wield") && !option.equals("wear") && !option.equals("equip") && !option.equals("hold"))
        {
            return;
        }

        int itemId = event.getItemId();
        if (itemId == -1) return;


        if (itemId == ItemID.PIRATEHOOK && durabilityManager.isHookBroken())
        {
            blockEvent(event, "The broken hook simply slides off your arm.");
            return;
        }
        // -------------------------------------

        int equipmentSlot = getEquipmentSlot(itemId);
        if (equipmentSlot == -1) return;

        boolean isHookEquipped = hookState.isWearingFunctionalHook() || hookState.wasHookJustEquipped();

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

        // 3. Disable Bows
        if (config.disableBows() && slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            String name = itemManager.getItemComposition(itemId).getName().toLowerCase();
            boolean isBow = name.contains("bow") && !name.contains("crossbow") && !name.contains("ballista") && !name.contains("crystal bow");

            if (isBow)
            {
                blockEvent(event, "You cannot draw a bowstring with a hook.");
            }
        }
    }

    // --- Helpers ---

    private boolean isTwoHanded(int itemId)
    {
        ItemStats stats = itemManager.getItemStats(itemId);
        return stats != null && stats.getEquipment() != null && stats.getEquipment().isTwoHanded();
    }

    private void blockEvent(MenuOptionClicked event, String message)
    {
        event.consume();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>" + message, null);
    }

    private int getEquipmentSlot(int itemId)
    {
        ItemStats stats = itemManager.getItemStats(itemId);
        return (stats != null && stats.getEquipment() != null) ? stats.getEquipment().getSlot() : -1;
    }

    private boolean nameContainsKeyword(int itemId, Set<String> keywords)
    {
        var def = itemManager.getItemComposition(itemId);
        String name = def.getName().toLowerCase();
        if (keywords == BANNED_OFFHAND_KEYWORDS && name.contains("shield")) return false;

        return keywords.stream().anyMatch(name::contains);
    }

    // --- Hook Removal Logic ---

    public void checkHookRemoval(MenuOptionClicked event)
    {
        if (!hookState.isWearingFunctionalHook()) return;

        if (isHookRemovalInteraction(event))
        {
            if (isHoldingIllegalItem())
            {
                blockEvent(event, "You cannot remove your hook while you're using it to hold something.");
            }
        }
    }

    private boolean isHookRemovalInteraction(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption());
        String target = Text.removeTags(event.getMenuTarget());

        if (option.equalsIgnoreCase("Remove") && target.equalsIgnoreCase("Pirate's hook"))
        {
            return true;
        }

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

        Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        if (weapon != null)
        {
            return isTwoHanded(weapon.getId());
        }
        return false;
    }
}