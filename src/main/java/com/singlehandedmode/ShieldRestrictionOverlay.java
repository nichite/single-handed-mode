package com.singlehandedmode;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
public class ShieldRestrictionOverlay extends Overlay
{
    // A list of all widgets that represent the Shield Slot
    private static final int[] SHIELD_SLOTS = {
            25362452, // Standard Equipment Tab
            786498,   // Bank "Worn Items"
            5505039,  // "View Equipment Stats" window
    };

    private final Client client;
    private final HookStateManager hookStateManager;
    private final ItemManager itemManager;
    private final SingleHandedModeConfig config;

    @Inject
    public ShieldRestrictionOverlay(Client client, HookStateManager hookStateManager, ItemManager itemManager, SingleHandedModeConfig config)
    {
        this.client = client;
        this.hookStateManager = hookStateManager;
        this.itemManager = itemManager;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // 1. Logic Check: Only draw if rule is active and hook is missing
        if (hookStateManager.isWearingFunctionalHook() || !config.disableShieldsNoHook())
        {
            return null;
        }

        // 2. Load Image (Bank Filler / Cancel Sign)
        BufferedImage bankFillerImage = itemManager.getImage(ItemID.BANK_FILLER);
        if (bankFillerImage == null)
        {
            return null;
        }

        // 3. Iterate through all known shield slots
        for (int widgetId : SHIELD_SLOTS)
        {
            Widget shieldSlot = client.getWidget(widgetId);

            // Skip if this specific widget isn't currently on screen
            if (shieldSlot == null || shieldSlot.isHidden())
            {
                continue;
            }

            drawBlocker(graphics, shieldSlot, bankFillerImage);
        }

        return null;
    }

    private void drawBlocker(Graphics2D graphics, Widget widget, BufferedImage image)
    {
        Rectangle bounds = widget.getBounds();

        // Calculate center position
        int x = bounds.x + (bounds.width - image.getWidth()) / 2;
        int y = bounds.y + (bounds.height - image.getHeight()) / 2;

        graphics.drawImage(image, x, y, null);
    }
}