package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.client.util.Text;

@Singleton
public class PaymentHandler
{
    private final Client client;
    private final DurabilityManager durabilityManager;

    // Tracking
    @Getter
    private WorldPoint paymentLocation = null;
    private int paymentAmount = 0;
    private long lastCoinCount = 0;

    @Inject
    public PaymentHandler(Client client, DurabilityManager durabilityManager)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
    }

    public void onGameTick()
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv != null)
        {
            lastCoinCount = inv.count(ItemID.COINS_995);
        }
    }

    // Inside PaymentHandler.java

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // 1. Only accept payment if Broken AND Agent is Active
        if (!durabilityManager.isHookBroken() | !durabilityManager.hasUnpaidDebt()) return;

        // 2. Validate the Item ID first (Fastest check)
        if (event.getItemId() != ItemID.COINS_995) return;

        // 3. Check if the action is "Drop"
        // We use Text.removeTags to handle any weird formatting, and toLowerCase for safety.
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();

        if (option.equals("drop"))
        {
            // 4. Verify it came from the Inventory
            // This prevents edge cases (though unlikely for coins)
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory != null)
            {
                // event.getParam0() holds the Inventory Slot Index
                int slot = event.getParam0();
                var item = inventory.getItem(slot);

                // Double-check the item exists and has enough quantity
                if (item != null && item.getId() == ItemID.COINS_995
                        && item.getQuantity() >= durabilityManager.getTotalRepairCost())
                {
                    // Start Tracking (Agent will move to this spot)
                    paymentAmount = item.getQuantity();
                    paymentLocation = client.getLocalPlayer().getWorldLocation();

                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                            "<col=0000ff>[Insurance Agent]: Don't touch that pile. I'm counting it.", null);
                }
            }
        }
    }

    public void onItemDespawned(ItemDespawned event)
    {
        if (paymentLocation == null) return; // Not tracking anything

        TileItem item = event.getItem();

        // Match the pile
        if (item.getId() == ItemID.COINS_995
                && item.getQuantity() == paymentAmount
                && event.getTile().getWorldLocation().equals(paymentLocation))
        {
            // Pile is gone. Check for fraud.
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            long currentCoinCount = inv != null ? inv.count(ItemID.COINS_995) : 0;

            if (currentCoinCount > lastCoinCount)
            {
                // FRAUD: Picked up
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "<col=ff0000>[Insurance Agent]: I saw that! No refund!", null);
            }
            else
            {
                // SUCCESS: Natural Despawn
                durabilityManager.settleDebt();
            }

            // Stop tracking
            paymentLocation = null;
            paymentAmount = 0;
        }
    }

    public boolean isTrackingPayment()
    {
        return paymentLocation != null;
    }

}