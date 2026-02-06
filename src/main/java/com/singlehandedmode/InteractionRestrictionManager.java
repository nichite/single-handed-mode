package com.singlehandedmode;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InteractionRestrictionManager
{
    private final Client client;
    private final HookStateManager hookState;
    private final SingleHandedModeConfig config;

    // Split tool lists for granular checking
    private static final Set<Integer> TOOLS_FLETCHING = ImmutableSet.of(
            ItemID.KNIFE
    );

    private static final Set<Integer> TOOLS_CRAFTING = ImmutableSet.of(
            ItemID.CHISEL, ItemID.NEEDLE, ItemID.GLASSBLOWINGPIPE
    );

    private static final Set<Integer> TOOLS_FARMING = ImmutableSet.of(
            ItemID.SPADE, ItemID.RAKE
    );

    private static final Set<Integer> TOOLS_SMITHING = ImmutableSet.of(
            ItemID.HAMMER
    );

    private static final Set<Integer> TOOLS_CONSTRUCTION = ImmutableSet.of(
            ItemID.POH_SAW
    );

    private static final Set<Integer> TOOLS_FIREMAKING = ImmutableSet.of(
            ItemID.TINDERBOX
    );

    @Inject
    public InteractionRestrictionManager(Client client, HookStateManager hookState, SingleHandedModeConfig config)
    {
        this.client = client;
        this.hookState = hookState;
        this.config = config;
    }

    public void checkRestrictions(MenuOptionClicked event)
    {
        // Hook bypasses all restrictions
        if (hookState.isWearingFunctionalHook()) return;

        if (checkGatheringRestrictions(event) || checkAgilityRestrictions(event) || checkToolRestrictions(event))
        {
            event.consume();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>You need two hands to do that properly!", null);
        }
    }

    private boolean checkGatheringRestrictions(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();

        if (config.disableMining() && option.equals("mine")) return true;

        if (config.disableSmithing() && option.equals("smith")) return true;

        if (config.disableFishing() && (option.equals("lure") || option.equals("bait") || option.equals("net"))) return true;

        return config.disableFarming() && (option.equals("rake") || option.equals("dig"));
    }

    private boolean checkAgilityRestrictions(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        String target = Text.removeTags(event.getMenuTarget()).toLowerCase();

        if (config.disableAgilityObstacles())
        {
            if (option.contains("swing-across") && target.contains("monkey bars")) return true;
            if (option.contains("swing") && target.contains("rope")) return true;
            if (option.contains("climb-across") && target.contains("hand holds")) return true;
            if (option.contains("use") && target.contains("rope -> rock")) return true; // Waterfall Quest
        }

        // Generic climbing checks, separated to handle ladders and ropes.
        // Stairs always allowed.
        if (option.contains("climb") && !target.contains("stair"))
        {
            log.debug("option contains climb");
            if (target.contains("ladder"))
            {
                return config.disableLadders();
            }
            // Climbing UP ropes is disallowed, but climbing down is kinda fine.
            else if (option.contains("climb-up") && target.contains("rope"))
            {
                return config.disableClimbingUpRopes();
            }
            // If it is NOT a ladder or rope (e.g. rock face, tree), it falls under Agility Obstacles
            else {
                log.debug("was not a rope or ladder. Must be climbing a normal agility obstacle");
                return config.disableAgilityObstacles();
            }
        }

        return false;
    }

    private boolean checkToolRestrictions(MenuOptionClicked event)
    {
        // Identify if a tool is being used
        int toolId = getActiveToolId(event);
        if (toolId == -1) return false;

        if (config.disableFletching() && TOOLS_FLETCHING.contains(toolId)) return true;
        if (config.disableCrafting() && TOOLS_CRAFTING.contains(toolId)) return true;
        if (config.disableFarming() && TOOLS_FARMING.contains(toolId)) return true;
        if (config.disableSmithing() && TOOLS_SMITHING.contains(toolId)) return true;


        // Firemaking usually grouped with "Survival" or Fletching/Crafting logic
        // For now, let's map Tinderbox to Fletching/Crafting as a "Dexterity Tool"
        if (config.disableFiremaking() && TOOLS_FIREMAKING.contains(toolId)) return true;

        // Construction Saw
        if (config.disableConstruction() && TOOLS_CONSTRUCTION.contains(toolId)) return true;

        if (config.disablePestleAndMortar() && toolId == ItemID.PESTLE_AND_MORTAR) return true;

        return false;
    }

    private int getActiveToolId(MenuOptionClicked event)
    {
        MenuAction action = event.getMenuAction();

        if (action == MenuAction.WIDGET_TARGET_ON_WIDGET ||
                action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
                action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
        {
            // Check Selected Item (Cursor)
            if (client.getSelectedWidget() != null)
            {
                return client.getSelectedWidget().getItemId();
            }
            // Check Target Item (Inventory Click) - Only for Widget on Widget
            if (action == MenuAction.WIDGET_TARGET_ON_WIDGET)
            {
                return event.getItemId();
            }
        }
        return -1;
    }
}