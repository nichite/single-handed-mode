package com.singlehandedmode;

import java.util.Random;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import lombok.Getter;

@Singleton
@Slf4j
public class InsuranceAgentManager
{
    private final Client client;
    private final DurabilityManager durabilityManager;
    private final PaymentHandler paymentHandler; // Assuming you injected this for location

    @Getter
    private RuneLiteObject agent;

    // NEW: Fields to store the "Fake" overhead text
    @Getter
    private String overheadText = null;
    private int textExpiryTick = -1;

    private final Random random = new Random();
    private static final int AGENT_MODEL_ID = 3225;

    @Inject
    public InsuranceAgentManager(Client client, DurabilityManager durabilityManager, PaymentHandler paymentHandler)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
        this.paymentHandler = paymentHandler;
    }

    public void onGameTick()
    {
        // 1. Check Expiry for Overhead Text
        if (textExpiryTick != -1 && client.getTickCount() > textExpiryTick)
        {
            overheadText = null;
            textExpiryTick = -1;
        }

        // 2. Determine Behavior
        // If the agent shouldn't exist (Paid or Not Broken), despawn.
        if (!durabilityManager.hasUnpaidDebt())
        {
            log.debug("Don't have unpaid debt");
            despawnAgent();
            return;
        }

        // 3. Counting vs Chasing
        if (paymentHandler.isTrackingPayment())
        {
            // STATE: COUNTING
            // Stand on the coins
            spawnOrMoveAgent(paymentHandler.getPaymentLocation());
            mutterAboutNumbers();
        }
        else
        {
            // STATE: CHASING
            // Follow player
            WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
            // Offset by 1 tile so he isn't standing INSIDE you
            spawnOrMoveAgent(new WorldPoint(playerLoc.getX() - 1, playerLoc.getY(), playerLoc.getPlane()));
            maybeTalk();
        }
    }

    private void spawnOrMoveAgent(WorldPoint location)
    {
        if (client.getLocalPlayer() == null) return;

        // Create if not exists
        if (agent == null)
        {
            agent = client.createRuneLiteObject();
            agent.setModel(client.loadModel(AGENT_MODEL_ID));
            // Optional: Animation ID (808 is standard human stand)
            agent.setAnimation(client.loadAnimation(808));
            agent.setShouldLoop(true);
        }

        LocalPoint localLoc = LocalPoint.fromWorld(client, location);

        if (localLoc != null)
        {
            agent.setLocation(localLoc, client.getPlane());
            agent.setActive(true);
        }
    }

    // --- Helper to set text ---
    public void say(String text, int durationTicks)
    {
        this.overheadText = text;
        this.textExpiryTick = client.getTickCount() + durationTicks;
    }

    private void maybeTalk()
    {
        if (random.nextInt(100) < 2)
        {
            int total = durabilityManager.getTotalRepairCost();
            // Use our new helper method instead of the non-existent RLO method
            say("You owe " + total + " gp and rising!", 4); // 4-5 ticks is readable
        }
    }

    private void mutterAboutNumbers()
    {
        if (random.nextInt(100) < 3)
        {
            String[] phrases = {
                    "998... 999... wait, was that 999?",
                    "Don't touch this, I'm verifying the serial numbers.",
                    "Processing... Processing...",
                    "This keeps the lights on, you know.",
                    "Tabulating late fees...",
                    "Just stand back, sir."
            };

            String msg = phrases[random.nextInt(phrases.length)];

            say(msg, 4);
        }
    }

    private void despawnAgent()
    {
        if (agent != null)
        {
            agent.setActive(false);
            // We keep the object instance but hide it to save resources
        }
    }

    // ... spawnOrMoveAgent and despawnAgent remain the same ...
    // Note: ensure spawnOrMoveAgent sets agent.setActive(true)
}