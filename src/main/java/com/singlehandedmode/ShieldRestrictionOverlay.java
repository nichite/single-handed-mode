package com.singlehandedmode;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay; // <--- The correct import
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
public class ShieldRestrictionOverlay extends Overlay // <--- Change this
{
    // Appears to not be in the ComponentID list
    private static final int SHIELD_SLOT_COMPONENT_ID = 25362452;

    private final Client client;
    private final HookStateManager hookStateManager;
    private final ItemManager itemManager;

    @Inject
    public ShieldRestrictionOverlay(Client client, HookStateManager hookStateManager, ItemManager itemManager)
    {
        this.client = client;
        this.hookStateManager = hookStateManager;
        this.itemManager = itemManager;

        // Set up the overlay rules
        setPosition(OverlayPosition.DYNAMIC); // Allows us to draw anywhere (like over a specific widget)
        setLayer(OverlayLayer.ABOVE_WIDGETS); // Draws on top of the inventory interface
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (hookStateManager.isWearingFunctionalHook())
        {
            return null;
        }

        // Group 387 (Equipment), Child 38 (Shield Slot)
        // This is the specific slot ID for the shield on the equipment screen.
        Widget shieldSlot = client.getWidget(SHIELD_SLOT_COMPONENT_ID);
        if (shieldSlot == null || shieldSlot.isHidden())
        {
            return null;
        }


        // 3. Get the Bank Filler Image
        // Use getImage() to get the standard inventory sprite for the item
        BufferedImage bankFillerImage = itemManager.getImage(ItemID.BANK_FILLER);

        if (bankFillerImage == null)
        {
            return null;
        }

        // 4. Center the Image
        Rectangle bounds = shieldSlot.getBounds();

        // Calculate the center position
        // bounds.x + (SlotWidth - ImageWidth) / 2
        int x = bounds.x + (bounds.width - bankFillerImage.getWidth()) / 2;
        int y = bounds.y + (bounds.height - bankFillerImage.getHeight()) / 2;

        // 5. Draw the Image
        graphics.drawImage(bankFillerImage, x, y, null);

        return null;
    }
}