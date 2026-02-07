package com.singlehandedmode;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Singleton
public class SingleHandedModeInfoBoxManager
{
    private final DurabilityManager durabilityManager;
    private final HookStateManager hookState;
    private final SingleHandedModeConfig config;
    private final InfoBoxManager runeLiteInfoBoxManager;
    private final ItemManager itemManager;

    private HookDurabilityInfoBox durabilityBox;
    private HookCostInfoBox costBox;
    private Plugin plugin;

    // Use the 10,000+ coin stack sprite (Fat stack)
    private static final int COIN_STACK_ID = ItemID.COINS_10000;

    @Inject
    public SingleHandedModeInfoBoxManager(
            DurabilityManager durabilityManager,
            HookStateManager hookState,
            SingleHandedModeConfig config,
            InfoBoxManager runeLiteInfoBoxManager,
            ItemManager itemManager
    )
    {
        this.durabilityManager = durabilityManager;
        this.hookState = hookState;
        this.config = config;
        this.runeLiteInfoBoxManager = runeLiteInfoBoxManager;
        this.itemManager = itemManager;
    }

    public void startUp(Plugin plugin)
    {
        this.plugin = plugin;
    }

    public void shutDown()
    {
        removeInfoBoxes();
        this.plugin = null;
    }

    public void onGameTick()
    {
        if (plugin == null) return;
        updateInfoBoxes();
    }

    private void updateInfoBoxes()
    {
        if (!hookState.isPiratesHookEquipped())
        {
            removeInfoBoxes();
            return;
        }

        // --- Durability Box ---
        if (config.showDurabilityInfobox())
        {
            if (durabilityBox == null)
            {
                BufferedImage image = itemManager.getImage(ItemID.PIRATEHOOK);
                durabilityBox = new HookDurabilityInfoBox(image, plugin, durabilityManager);
                runeLiteInfoBoxManager.addInfoBox(durabilityBox);
            }
        }
        else if (durabilityBox != null)
        {
            runeLiteInfoBoxManager.removeInfoBox(durabilityBox);
            durabilityBox = null;
        }

        // --- Cost Box ---
        if (config.showCostInfobox())
        {
            int cost = durabilityManager.getAccruedCost();
            if (cost > 0)
            {
                if (costBox == null)
                {
                    // Use the fat stack image
                    BufferedImage image = itemManager.getImage(COIN_STACK_ID);
                    costBox = new HookCostInfoBox(image, plugin, durabilityManager);
                    runeLiteInfoBoxManager.addInfoBox(costBox);
                }
            }
            else if (costBox != null)
            {
                runeLiteInfoBoxManager.removeInfoBox(costBox);
                costBox = null;
            }
        }
        else if (costBox != null)
        {
            runeLiteInfoBoxManager.removeInfoBox(costBox);
            costBox = null;
        }
    }

    private void removeInfoBoxes()
    {
        if (durabilityBox != null)
        {
            runeLiteInfoBoxManager.removeInfoBox(durabilityBox);
            durabilityBox = null;
        }
        if (costBox != null)
        {
            runeLiteInfoBoxManager.removeInfoBox(costBox);
            costBox = null;
        }
    }
}