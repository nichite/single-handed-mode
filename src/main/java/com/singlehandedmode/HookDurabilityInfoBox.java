package com.singlehandedmode;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin; // Need generic Plugin
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

public class HookDurabilityInfoBox extends InfoBox
{
    private final DurabilityManager durabilityManager;

    public HookDurabilityInfoBox(BufferedImage image, Plugin plugin, DurabilityManager durabilityManager)
    {
        super(image, plugin);
        this.durabilityManager = durabilityManager;
        setPriority(InfoBoxPriority.HIGH);
    }

    @Override
    public String getText()
    {
        return getTimeRemaining();
    }

    public String getTimeRemaining()
    {
        int ticksLeft = durabilityManager.getCurrentDurability();

        // 1 tick = 0.6 seconds
        double totalSeconds = ticksLeft * 0.6;

        int hours = (int) (totalSeconds / 3600);

        // If greater than 1 hour, show concise "N Hour(s)"
        if (hours >= 1)
        {
            return hours + "h";
        }

        // Otherwise show MM:SS
        int remainder = (int) (totalSeconds % 3600);
        int mins = remainder / 60;
        int secs = remainder % 60;

        return String.format("%02d:%02d", mins, secs);
    }

    @Override
    public Color getTextColor()
    {
        if (durabilityManager.isHookBroken())
        {
            return Color.RED;
        }
        return Color.WHITE;
    }

    @Override
    public String getTooltip()
    {
        if (durabilityManager.isHookBroken())
        {
            return "Hook is BROKEN! Repair immediately.";
        }
        return "Time until hook breaks";
    }
}