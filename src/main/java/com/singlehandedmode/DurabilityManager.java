package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
public class DurabilityManager
{
    private final int TICKS_PER_SAVE = 16;
    private final Client client;
    private final ConfigManager configManager;
    private final SingleHandedModeConfig config;
    private final HookStateManager hookState;

    @Getter
    private int wearTicks;
    private int penaltyDebt;
    private boolean isBroken = false;
    private long lastTickMillis = 0;

    // Lifetime stats
    @Getter
    private long lifetimeWorn;
    @Getter
    private long lifetimePaid;

    @Inject
    public DurabilityManager(Client client, ConfigManager configManager, SingleHandedModeConfig config, HookStateManager hookState)
    {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
        this.hookState = hookState;

        // Load initial state
        this.wearTicks = config.currentWearTicks();
        this.penaltyDebt = config.accumulatedDebt();
        this.isBroken = wearTicks >= config.hookDurabilityTicks();
        this.lifetimeWorn = config.lifetimeWorn();
        this.lifetimePaid = config.lifetimePaid();
    }

    public void onGameTick()
    {
        lastTickMillis = System.currentTimeMillis();

        if (hookState.isPiratesHookEquipped())
        {
            ++wearTicks;
            ++lifetimeWorn;
            if (isBroken) {
                double increase = config.penaltyPerSecond() * 0.6;
                penaltyDebt += (int) Math.ceil(increase);
            } else {
                checkBrokenState();
            }
            if (client.getTickCount() % TICKS_PER_SAVE == 0) saveProgress();
        }
    }

    public void shutDown()
    {
        saveProgress();
    }

    public String getSmoothTimeRemaining()
    {
        // 1. Calculate base remaining time in milliseconds
        // (wearTicks are "past", so we subtract them from max)
        long ticksRemaining = Math.max(0, config.hookDurabilityTicks() - wearTicks);
        long baseMillisRemaining = ticksRemaining * Constants.GAME_TICK_LENGTH; // 600ms per tick

        // 2. Subtract time elapsed since the last tick (Interpolation)
        // This makes the timer count down smoothly between ticks
        long timeSinceLastTick = System.currentTimeMillis() - lastTickMillis;

        // Clamp: Don't let it go below 0 or subtract more than one tick's worth (to prevent jitter)
        if (timeSinceLastTick > Constants.GAME_TICK_LENGTH)
        {
            timeSinceLastTick = Constants.GAME_TICK_LENGTH;
        }

        long smoothMillis = baseMillisRemaining - timeSinceLastTick;

        if (smoothMillis <= 0) return "00:00";

        // 3. Format nicely
        long totalSeconds = smoothMillis / 1000;
        long hours = totalSeconds / 3600;
        long remainder = totalSeconds % 3600;
        long mins = remainder / 60;
        long secs = remainder % 60;

        if (hours > 0)
        {
            return String.format("%d:%02d:%02d", hours, mins, secs);
        }

        return String.format("%02d:%02d", mins, secs);
    }

    // --- Data Accessors for UI ---

    public int getCurrentDurability() { return Math.max(0, config.hookDurabilityTicks() - wearTicks); }
    public boolean isHookBroken() { return isBroken; }
    public boolean hasUnpaidDebt() { return penaltyDebt > 0; }
    public int getTotalRepairCost() { return penaltyDebt; }

    public int getAccruedCost()
    {
        if (hasUnpaidDebt()) return penaltyDebt;

        double maxTicks = (double) config.hookDurabilityTicks();
        if (maxTicks == 0) return 0;

        double percentWorn = (double) wearTicks / maxTicks;
        if (percentWorn > 1.0) percentWorn = 1.0;

        return (int) (percentWorn * config.repairCost());
    }

    // --- Actions ---

    public void settleDebt() {
        lifetimePaid += penaltyDebt;
        penaltyDebt = 0;
        saveProgress();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ff00>Payment accepted. Debt cleared.", null);
    }

    public void repairHook()
    {
        wearTicks = 0;
        isBroken = false;
        saveProgress();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00ff00>Your pirate's hook has been repaired!", null);
    }

    private void checkBrokenState()
    {
        boolean newBrokenState = wearTicks >= config.hookDurabilityTicks();
        if (newBrokenState && !isBroken)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>Your pirate's hook has broken!", null);
            penaltyDebt += config.repairCost();
            saveProgress();
        }
        isBroken = newBrokenState;
    }

    private void saveProgress()
    {
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "currentWearTicks", wearTicks);
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "accumulatedDebt", penaltyDebt);
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "lifetimeWorn", lifetimeWorn);
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "lifetimePaid", lifetimePaid);
    }
}