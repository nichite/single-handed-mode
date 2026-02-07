package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
public class DurabilityManager
{
    private static final int TICKS_PER_SAVE = 16;

    private final Client client;
    private final ConfigManager configManager;
    private final SingleHandedModeConfig config;
    private final HookStateManager hookState;

    @Getter
    private int wearTicks;
    private int penaltyDebt;
    private boolean isBroken = false;

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
        }
        // Don't spam the config system with writes every tick
        if (client.getTickCount() % TICKS_PER_SAVE == 0) saveProgress();
    }

    public void shutDown()
    {
        saveProgress();
    }

    // --- Data Accessors for UI ---

    public int getCurrentDurability() { return Math.max(0, config.hookDurabilityTicks() - wearTicks); }
    public boolean isHookBroken() { return isBroken; }
    public boolean hasUnpaidDebt() { return penaltyDebt > 0; }
    public int getTotalRepairCost() { return penaltyDebt; }

    public int getAccruedCost()
    {
        if (hasUnpaidDebt()) return penaltyDebt;

        double maxTicks = config.hookDurabilityTicks();
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