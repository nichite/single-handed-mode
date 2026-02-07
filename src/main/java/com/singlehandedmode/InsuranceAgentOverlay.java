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
        // 1. Get the Wrapper
        InsuranceAgent agentWrapper = agentManager.getAgent();

        // 2. Get the Text
        String text = agentManager.getOverheadText();

        // Safety Checks
        // We must check if the wrapper exists, AND if the underlying RLO exists/is active
        if (agentWrapper == null || text == null)
        {
            return null;
        }

        RuneLiteObject rlo = agentWrapper.getRlo();
        if (rlo == null || !rlo.isActive())
        {
            return null;
        }

        // 3. Get Location from the RLO (Visual Location)
        LocalPoint lp = rlo.getLocation();
        if (lp == null) return null;

        // 4. Calculate Text Position
        // A standard human NPC is about 180-200 units tall.
        int zOffset = 190;

        Point textLocation = Perspective.getCanvasTextLocation(client, graphics, lp, text, zOffset);

        if (textLocation != null)
        {
            // Optional: Font styling to match OSRS overheads better
            // Standard OSRS overhead font is usually 12 or 16 bold.
            graphics.setFont(new Font("RuneScape", Font.BOLD, 16));

            OverlayUtil.renderTextLocation(graphics, textLocation, text, Color.YELLOW);
        }

        return null;
    }
}