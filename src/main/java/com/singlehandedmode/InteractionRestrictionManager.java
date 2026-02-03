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

@Singleton
public class InteractionRestrictionManager
{
    private final Client client;
    private final HookStateManager hookState;

    private static final Set<Integer> BANNED_TWO_HANDED_TOOLS = ImmutableSet.of(
            ItemID.KNIFE, ItemID.CHISEL, ItemID.NEEDLE, ItemID.GLASSBLOWINGPIPE,
            ItemID.BALL_OF_WOOL, ItemID.TINDERBOX, ItemID.SPADE, ItemID.RAKE,
            ItemID.DIBBER, ItemID.GARDENING_TROWEL, ItemID.PESTLE_AND_MORTAR,
            ItemID.HAMMER, ItemID.POH_SAW
    );

    @Inject
    public InteractionRestrictionManager(Client client, HookStateManager hookState)
    {
        this.client = client;
        this.hookState = hookState;
    }

    public void checkRestrictions(MenuOptionClicked event)
    {
        // If hook is equipped, all actions are allowed
        if (hookState.isPiratesHookEquipped()) return;

        if (isTwoHandedAction(event) || isTwoHandedToolUsage(event))
        {
            event.consume();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>You need two hands to do that properly!", null);
        }
    }

    private boolean isTwoHandedAction(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption()).toLowerCase();
        String target = Text.removeTags(event.getMenuTarget()).toLowerCase();

        // 1. Processing / Gathering
        if (option.equals("mine")) return true;
        if (option.equals("smith")) return true;
        if (option.equals("lure") || option.equals("bait") || option.equals("net")) return true;
        if (option.equals("rake")) return true;

        // 2. Agility / Movement
        if (option.contains("swing-across") && target.contains("monkey bars")) return true;
        if (option.contains("swing") && target.contains("rope")) return true;
        if (option.contains("climb-across") && target.contains("hand holds")) return true;
        if (option.contains("use") && target.contains("rope -> rock")) return true; // Waterfall

        // Block all climbs except standard ladders/walls (implied climbing up ropes etc)
        if (option.contains("climb") && !target.contains("ladder") && !target.contains("crumbling wall"))
        {
            return true;
        }

        return false;
    }

    private boolean isTwoHandedToolUsage(MenuOptionClicked event)
    {
        MenuAction action = event.getMenuAction();

        if (action == MenuAction.WIDGET_TARGET_ON_WIDGET ||
                action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
                action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM)
        {
            int selectedItemId = -1;
            if (client.getSelectedWidget() != null)
            {
                selectedItemId = client.getSelectedWidget().getItemId();
            }
            int targetItemId = event.getItemId();

            if (BANNED_TWO_HANDED_TOOLS.contains(selectedItemId)) return true;

            if (action == MenuAction.WIDGET_TARGET_ON_WIDGET && BANNED_TWO_HANDED_TOOLS.contains(targetItemId))
            {
                return true;
            }
        }
        return false;
    }
}