package com.singlehandedmode;

import java.util.Random;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@Slf4j
public class InsuranceAgentManager
{
    private final Client client;
    private final DurabilityManager durabilityManager;
    private final PaymentHandler paymentHandler;
    private final AgentPathFinder pathFinder;

    // The "Body" of the agent
    @Getter
    final private InsuranceAgent agent;

    @Getter
    private String overheadText = null;
    private int textExpiryTick = -1;
    private final Random random = new Random();

    // CONSTANTS
    private static final int ANIM_IDLE = 808;
    private static final int ANIM_WALK = 819;
    private static final int ANIM_WAVE = 863;
    private static final int ANIM_CRY = 860;

    // DEPARTURE STATE
    private boolean isLeaving = false;
    private int departureTick = -1;
    private boolean needsRespawn = false;

    @Inject
    public InsuranceAgentManager(Client client, DurabilityManager durabilityManager,
                                 PaymentHandler paymentHandler, AgentPathFinder pathFinder)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
        this.paymentHandler = paymentHandler;
        this.pathFinder = pathFinder;
        this.agent = new InsuranceAgent(client);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOADING)
        {
            needsRespawn = true;
        }
    }

    // --- MAIN LOGIC LOOP (Game Tick) ---
    public void onGameTick()
    {
        // 1. Cleanup
        if (textExpiryTick != -1 && client.getTickCount() > textExpiryTick) {
            overheadText = null; textExpiryTick = -1;
        }

        // 2. Teleport / Region Load Check
        if (needsRespawn && client.getGameState() == GameState.LOGGED_IN)
        {
            agent.despawn();
            needsRespawn = false;
        }

        // 3. Departure Sequence
        if (isLeaving)
        {
            handleDepartureSequence();
            return;
        }

        // 4. Debt Status Check
        if (!durabilityManager.hasUnpaidDebt())
        {
            if (agent.isActive()) startDepartureSequence();
            else agent.despawn();
            return;
        }

        // 5. Behavior Logic
        runAgentBehavior();
    }

    // --- RENDER LOOP (Client Tick) ---
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        // Delegate rendering to the entity class
        agent.render();
    }

    // --- PRIVATE LOGIC ---

    private void runAgentBehavior()
    {
        WorldPoint goal = paymentHandler.isTrackingPayment()
                ? paymentHandler.getPaymentLocation()
                : client.getLocalPlayer().getWorldLocation();

        if (paymentHandler.isTrackingPayment()) mutterAboutNumbers();
        else maybeTalk();

        // 1. Initialize if missing
        if (!agent.isActive() || agent.getCurrentPos() == null)
        {
            WorldPoint spawnPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.spawn(spawnPos);
        }

        // 2. Off-Map Snap (If agent is in a different scene context)
        if (LocalPoint.fromWorld(client, agent.getCurrentPos()) == null)
        {
            WorldPoint snapPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.snapTo(snapPos);
            return;
        }

        // 3. Distance Check (Teleport if too far)
        if (agent.getCurrentPos().distanceTo(goal) > 15 || agent.getCurrentPos().getPlane() != goal.getPlane())
        {
            // Re-spawn to handle scene boundaries safely
            WorldPoint newPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.despawn();
            agent.spawn(newPos);
            return;
        }

        // 4. Pathfinding
        int distance = agent.getCurrentPos().distanceTo(goal);

        if (distance > 1)
        {
            WorldPoint nextStep = pathFinder.findNextStep(agent.getCurrentPos(), goal);
            if (nextStep != null)
            {
                agent.moveTo(nextStep);
                agent.setAnimation(ANIM_WALK);
                agent.faceTarget(goal);
            }
            else
            {
                agent.setAnimation(ANIM_IDLE);
                agent.faceTarget(goal);
            }
        }
        else
        {
            agent.setAnimation(ANIM_IDLE);
            agent.faceTarget(goal);
        }
    }

    private void startDepartureSequence()
    {
        isLeaving = true;
        departureTick = 0;

        agent.faceTarget(client.getLocalPlayer().getWorldLocation());
        agent.setAnimation(ANIM_WAVE, false);
        client.getLocalPlayer().setAnimation(ANIM_CRY);

        say("Pleasure doing business.", 5);
    }

    private void handleDepartureSequence()
    {
        departureTick++;
        if (departureTick >= 4)
        {
            agent.despawn();
            isLeaving = false;
        }
    }

    // --- UTILS ---

    public void say(String text, int durationTicks)
    {
        this.overheadText = text;
        this.textExpiryTick = client.getTickCount() + durationTicks;
    }

    private void maybeTalk()
    {
        if (random.nextInt(100) < 2) say("You owe " + durabilityManager.getTotalRepairCost() + " gp!", 4);
    }

    private void mutterAboutNumbers()
    {
        if (random.nextInt(100) < 3) say("Counting...", 4);
    }
}