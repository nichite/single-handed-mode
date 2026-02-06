package com.singlehandedmode;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

public class BrokenHookOverlay extends WidgetItemOverlay
{
    private final DurabilityManager durabilityManager;
    private final BufferedImage brokenHookImage;

    // Defines which Item IDs trigger the overlay
    private static final int PIRATE_HOOK_ID = ItemID.PIRATEHOOK;
    // If you are using the Brimhaven cosmetic override or another variant, add that ID too.

    @Inject
    public BrokenHookOverlay(DurabilityManager durabilityManager)
    {
        this.durabilityManager = durabilityManager;

        // 1. Load the custom image from resources
        this.brokenHookImage = ImageUtil.loadImageResource(getClass(), "/broken_pirates_hook_32x32.png");

        // 2. Tell RuneLite where to draw this
        showOnInventory();
        showOnBank();
        showOnEquipment(); // Draws over the equipment slot if you have the stats tab open
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        // 1. Check if this is the hook
        if (itemId != PIRATE_HOOK_ID) return;

        // 2. Check if it is actually broken
        if (!durabilityManager.isHookBroken()) return;

        // 3. Draw the custom icon
        Rectangle bounds = widgetItem.getCanvasBounds();

        // Note: The original icon is still drawn *underneath*.
        // Make sure your PNG is opaque enough to cover it, or has a background
        // that matches the inventory grid if you want to completely hide the old one.
        graphics.drawImage(brokenHookImage, bounds.x, bounds.y, null);
    }
}