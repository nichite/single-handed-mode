package com.singlehandedmode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
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
    private final AgentPathFinder agentPathFinder;

    @Getter
    private final InsuranceAgent agent;

    @Getter
    private String overheadText = null;
    private int textExpiryTick = -1;
    private final Random random = new Random();

    // --- ID CONSTANTS ---
    private static final int NPC_GILES_LAND = 5438;
    private static final int ANIM_LAND_IDLE = 808;
    private static final int ANIM_LAND_WALK = 819;

    // UNDERWATER
    private static final int NPC_GILES_WATER = 5441; // Correct Diving Gear ID

    // Use 772 (Treading Water) for BOTH idle and move to prevent sliding.
    private static final int ANIM_WATER_IDLE = 772;
    private static final int ANIM_WATER_MOVE = 772;

    private static final int ANIM_WAVE = 863;
    private static final int ANIM_CRY = 860;

    // REGIONS
    private static final Set<Integer> UNDERWATER_REGIONS = new HashSet<>(Arrays.asList(
            15008, // Fossil Island Underwater (North)
            15264, // Fossil Island Underwater (South)
            11924, // Mogre Camp
            13194  // Coral nursery
    ));

    // STATE
    private boolean isUnderwater = false;
    private boolean needsRespawn = false;
    private boolean isLeaving = false;
    private int departureTick = -1;

    @Inject
    public InsuranceAgentManager(Client client, DurabilityManager durabilityManager,
                                 PaymentHandler paymentHandler, AgentPathFinder agentPathFinder)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
        this.paymentHandler = paymentHandler;
        this.agentPathFinder = agentPathFinder;
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

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (agent != null)
        {
            agent.render();
        }
    }

    public void onGameTick()
    {
        if (textExpiryTick != -1 && client.getTickCount() > textExpiryTick) {
            overheadText = null; textExpiryTick = -1;
        }

        checkBiome();

        if (needsRespawn && client.getGameState() == GameState.LOGGED_IN)
        {
            agent.despawn();
            needsRespawn = false;
        }

        if (isLeaving)
        {
            handleDepartureSequence();
            return;
        }

        if (!durabilityManager.hasUnpaidDebt())
        {
            if (agent.isActive()) startDepartureSequence();
            else agent.despawn();
            return;
        }

        runAgentBehavior();
    }

    private void checkBiome()
    {
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        if (wp == null) return;

        boolean nowUnderwater = UNDERWATER_REGIONS.contains(wp.getRegionID());

        if (nowUnderwater != isUnderwater)
        {
            isUnderwater = nowUnderwater;
            // Force respawn to swap model immediately
            if (agent.isActive())
            {
                agent.despawn();
            }
        }
    }

    private void runAgentBehavior()
    {
        WorldPoint goal = paymentHandler.isTrackingPayment()
                ? paymentHandler.getPaymentLocation()
                : client.getLocalPlayer().getWorldLocation();

        if (paymentHandler.isTrackingPayment()) mutterAboutNumbers();
        else maybeTalk();

        // 1. DETERMINE VISUALS
        int currentNpcId = isUnderwater ? NPC_GILES_WATER : NPC_GILES_LAND;
        int currentIdleAnim = isUnderwater ? ANIM_WATER_IDLE : ANIM_LAND_IDLE;
        int currentWalkAnim = isUnderwater ? ANIM_WATER_MOVE : ANIM_LAND_WALK;

        // 2. SPAWN
        if (!agent.isActive() || agent.getCurrentPos() == null)
        {
            WorldPoint spawnPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.spawn(spawnPos, currentNpcId);
        }

        // 3. SNAP (Scene Safety)
        if (LocalPoint.fromWorld(client, agent.getCurrentPos()) == null)
        {
            WorldPoint snapPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.snapTo(snapPos);
            return;
        }

        // 4. TELEPORT (Distance Safety)
        if (agent.getCurrentPos().distanceTo(goal) > 15 || agent.getCurrentPos().getPlane() != goal.getPlane())
        {
            WorldPoint newPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.despawn();
            agent.spawn(newPos, currentNpcId);
            return;
        }

        // 5. MOVEMENT
        int distance = agent.getCurrentPos().distanceTo(goal);

        if (distance > 1)
        {
            WorldPoint nextStep = agentPathFinder.findNextStep(agent.getCurrentPos(), goal);
            if (nextStep != null)
            {
                agent.moveTo(nextStep);
                agent.setAnimation(currentWalkAnim);
                agent.faceTarget(goal);
            }
            else
            {
                agent.setAnimation(currentIdleAnim);
                agent.faceTarget(goal);
            }
        }
        else
        {
            agent.setAnimation(currentIdleAnim);
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