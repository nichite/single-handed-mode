package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
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

    private int wearTicks;
    private int penaltyDebt;

    private boolean isBroken = false;

    @Inject
    public DurabilityManager(Client client, ConfigManager configManager, SingleHandedModeConfig config, HookStateManager hookState)
    {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
        this.hookState = hookState;

        // Load initial state
        log.debug("Initial state: wearTicks = {}", wearTicks);
        this.wearTicks = config.currentWearTicks();
        this.penaltyDebt = config.accumulatedDebt();
        this.isBroken = wearTicks > config.hookDurabilityTicks();
    }

    public void onGameTick()
    {
//        if (!hookState.isPiratesHookEquipped()) return;

        if (hookState.isWearingFunctionalHook())
        {
            // NORMAL WEAR
            ++wearTicks;
            checkBrokenState();
//            log.debug("Wear ticks: {}", wearTicks);
            if (wearTicks % TICKS_PER_SAVE == 0)
            {
                saveProgress();

            }
        }
        else if (hookState.isPiratesHookEquipped())
        {
            // BROKEN & EQUIPPED -> PENALTY PHASE
            // We are broken, but the player hasn't unequipped it yet.
            // Tick up the debt!

            // Formula: Config GP/Sec divided by ticks/sec (0.6s per tick? No, 1 sec = 1.66 ticks).
            // Simpler: Add (ConfigVal / 1.6) roughly, or just do it every second.
            // Let's just track ticks and apply math periodically to avoid float errors.
            // Or simpler: Config is "GP Per Tick" internally?
            // Let's assume Config is "GP Per Second".
            // 1 Tick = 0.6 seconds. So add (0.6 * GP_PER_SEC).
            ++wearTicks;
            double increase = config.penaltyPerSecond() * 0.6;
            penaltyDebt += (int) Math.ceil(increase);

            // Notify player periodically of their rising debt (every ~10 seconds)
            if (client.getTickCount() % TICKS_PER_SAVE == 0)
            {
                // We don't save every tick to save disk I/O, but maybe every few seconds
                saveProgress();
            }
        } else if (!isBroken) {
//            log.debug("Not broken but not equipped. Wear ticks: {}", wearTicks);
        } else {
//            log.debug("Broken and not equipped. Wear ticks: {}", wearTicks);
        }

    }

    public boolean isHookBroken()
    {
        return isBroken;
    }

    public int getTotalRepairCost()
    {
        return penaltyDebt;
    }

    public boolean hasUnpaidDebt() {
        return penaltyDebt > 0;
    }

    public void settleDebt() {
        penaltyDebt = 0;
        saveProgress();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=00ff00>Payment accepted. Your medical debt has been cleared...for now.", null);
    }

    public void repairHook()
    {
        wearTicks = 0;
        isBroken = false;
        saveProgress();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=00ff00>Your pirate's hook has been repaired!", null);
    }

    private void checkBrokenState()
    {
        boolean newBrokenState = wearTicks >= config.hookDurabilityTicks();;
        if (newBrokenState && !isBroken)
        {
            // Just broke this tick
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>Your pirate's hook has broken!", null);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>You must pay your insurance deductible (Drop " + config.repairCost() + " gp) to fix it.", null);

            penaltyDebt += config.repairCost();
            saveProgress();
        }

        isBroken = newBrokenState;
    }

    private void saveProgress()
    {
        log.debug("Saving hook durability: wear ticks: " + wearTicks + ", penalty debt: " + penaltyDebt);
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "currentWearTicks", wearTicks);
        configManager.setConfiguration(SingleHandedModeConfig.GROUP, "accumulatedDebt", penaltyDebt);
    }
}