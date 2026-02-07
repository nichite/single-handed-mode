package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Singleton
public class BrokenHookTextOverride
{
    @Inject
    private DurabilityManager durabilityManager;

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        // 1. Quick Exit: Only override if broken
        if (!durabilityManager.isHookBroken())
        {
            return;
        }

        // 2. Check Target: Is this the hook?
        // use removeTags to ignore existing color codes
        String target = Text.removeTags(event.getTarget());

        if (target != null && target.equals("Pirate's hook"))
        {
            MenuEntry entry = event.getMenuEntry();

            // 3. Override: Append red "(Broken)" text
            String brokenSuffix = ColorUtil.wrapWithColorTag(" (Broken)", Color.RED);
            entry.setTarget(event.getTarget() + brokenSuffix);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Only override Examine text
        if (event.getType() != ChatMessageType.ITEM_EXAMINE)
        {
            return;
        }

        if (durabilityManager.isHookBroken())
        {
            String message = Text.removeTags(event.getMessage());

            // The default examine text for the hook is "You should see the shark..."
            if (message.contains("You should see the shark..."))
            {
                event.getMessageNode().setValue("More broken than a pre-rebalance blowpipe.");
            }
        }
    }
}