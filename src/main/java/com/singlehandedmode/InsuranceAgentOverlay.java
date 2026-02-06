package com.singlehandedmode;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class InsuranceAgentOverlay extends Overlay
{
    private final Client client;
    private final InsuranceAgentManager agentManager;

    @Inject
    public InsuranceAgentOverlay(Client client, InsuranceAgentManager agentManager)
    {
        this.client = client;
        this.agentManager = agentManager;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_MED);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        RuneLiteObject agent = agentManager.getAgent();
        String text = agentManager.getOverheadText();

        // Safety Checks
        if (agent == null || !agent.isActive() || text == null)
        {
            return null;
        }

        // 1. Get Location
        LocalPoint lp = agent.getLocation();
        if (lp == null) return null;

        // 2. Calculate Text Position
        // We add a Z-offset (Height) to make it float above his head.
        // A standard human NPC is about 180-200 units tall.
        // We add ~40 extra units for the text float height.
        int zOffset = 240;

        Point textLocation = Perspective.getCanvasTextLocation(client, graphics, lp, text, zOffset);

        if (textLocation != null)
        {
            // 3. Draw the Text
            // Standard OSRS overhead color is Yellow (Color.YELLOW).
            // You can use a custom font if you want, but default looks "native".

            // Optional: Font styling to match OSRS overheads better
            graphics.setFont(new Font("RuneScape", Font.BOLD, 16));

            OverlayUtil.renderTextLocation(graphics, textLocation, text, Color.YELLOW);
        }

        return null;
    }
}