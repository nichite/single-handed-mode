package com.singlehandedmode;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;
import net.runelite.client.util.QuantityFormatter; // Import RuneLite formatter

public class HookCostInfoBox extends InfoBox
{
    private final DurabilityManager durabilityManager;

    public HookCostInfoBox(BufferedImage image, Plugin plugin, DurabilityManager durabilityManager)
    {
        super(image, plugin);
        this.durabilityManager = durabilityManager;
        setPriority(InfoBoxPriority.MED);
    }

    @Override
    public String getText()
    {
        int cost = durabilityManager.getAccruedCost();
        // This handles "100K", "10M" formatting automatically
        return QuantityFormatter.quantityToStackSize(cost);
    }

    @Override
    public Color getTextColor()
    {
        // Standard White for readability, Red for Debt
        return durabilityManager.hasUnpaidDebt() ? Color.RED : Color.WHITE;
    }

    @Override
    public String getTooltip()
    {
        if (durabilityManager.hasUnpaidDebt())
        {
            return "Overdue medical debt (Active Penalty)";
        }
        return "Projected repair deductible";
    }
}