package com.singlehandedmode;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants; // Import for GAME_TICK_LENGTH (600)
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class DurabilityStatsOverlay extends Overlay
{
    private final Client client;
    private final HookStateManager hookStateManager;
    private final DurabilityManager durabilityManager;
    private final SingleHandedModeConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    // Fields for smooth interpolation
    private int lastTick = -1;
    private long lastTickTime = 0;

    @Inject
    public DurabilityStatsOverlay(Client client, HookStateManager hookStateManager, DurabilityManager durabilityManager, SingleHandedModeConfig config)
    {
        this.client = client;
        this.hookStateManager = hookStateManager;
        this.durabilityManager = durabilityManager;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(220, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // 1. Config & State Check
        if (!config.showStatsPanel())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        // --- TITLE ---
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Insurance Policy")
                .color(Color.ORANGE)
                .build());

        // --- STATUS ---
        boolean isBroken = durabilityManager.isHookBroken();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(isBroken ? "BROKEN" : "Active")
                .rightColor(isBroken ? Color.RED : Color.GREEN)
                .build());

        // --- TIME REMAINING (Smooth) ---
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Coverage:")
                .right(getSmoothTimeRemaining()) // Call local helper
                .build());

        // --- CURRENT COST ---
        int cost = durabilityManager.getAccruedCost();
        if (cost > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(isBroken ? "Late Fees:" : "Deductible:")
                    .right(formatGp(cost))
                    .rightColor(isBroken ? Color.RED : Color.WHITE)
                    .build());
        }

        // --- LIFETIME STATS ---
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Lifetime Worn:")
                .right(getLifetimeTimeWorn())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Lifetime Paid:")
                .right(formatGp(durabilityManager.getLifetimePaid()))
                .build());

        return panelComponent.render(graphics);
    }

    public String getLifetimeTimeWorn()
    {
        // Assuming you track 'totalWearTicks' as a long in config too
        long totalSeconds = durabilityManager.getLifetimeWorn() * 60 / 100; // 0.6 sec per tick

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long mins = (totalSeconds % 3600) / 60;

        if (days > 0) return String.format("%dd %dh %dm", days, hours, mins);
        if (hours > 0) return String.format("%dh %dm", hours, mins);
        return String.format("%dm", mins);
    }

    /**
     * Calculates smooth time remaining by comparing current time vs last tick time.
     * Logic is fully self-contained here.
     */
    private String getSmoothTimeRemaining()
    {
        // 1. Get base duration from manager (in Ticks)
        long ticksLeft = durabilityManager.getCurrentDurability();
        long baseMillis = ticksLeft * Constants.GAME_TICK_LENGTH; // 600ms per tick

        // 2. ONLY Interpolate if the timer is actually running
        if (hookStateManager.isPiratesHookEquipped())
        {
            // Detect new tick to sync our stopwatch
            int currentTick = client.getTickCount();
            if (currentTick != lastTick)
            {
                lastTick = currentTick;
                lastTickTime = System.currentTimeMillis();
            }

            // Calculate elapsed time since the tick started
            long elapsed = System.currentTimeMillis() - lastTickTime;

            // Clamp to avoid visual jitter
            if (elapsed > Constants.GAME_TICK_LENGTH) elapsed = Constants.GAME_TICK_LENGTH;
            if (elapsed < 0) elapsed = 0;

            // Apply the smoothing
            baseMillis -= elapsed;
        }
        // ELSE: We are paused. Do not subtract 'elapsed'. baseMillis remains static.

        if (baseMillis <= 0) return "00:00";

        // 3. Format
        long totalSeconds = baseMillis / 1000;
        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long mins = remainder / 60;
        long secs = remainder % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, mins, secs);
        }

        return String.format("%02d:%02d", mins, secs);
    }

    private String formatGp(long number)
    {
        return String.format("%,d gp", number);
    }
}