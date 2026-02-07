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

    // Timer for speech (15 seconds * 1.66 ticks/sec = ~25 ticks)
    private int nextSpeechTick = 0;
    private static final int SPEECH_INTERVAL_TICKS = 25;

    private final Random random = new Random();

    // --- DIALOGUE POOLS ---
    private static final String[] NAGGING_LINES = {
            "Your premium is overdue, sir.",
            "My records indicate a missing payment of {gp}.",
            "I can do this all day.",
            "Interest is accruing as we speak.",
            "That hook is a liability until you pay {gp}.",
            "Please, for your own sake, settle the debt.",
            "We really don't want to involve the bouncers.",
            "Coverage is suspended until {gp} are remitted.",
            "You can't outrun the actuarial tables.",
            "Just drop the {gp} and I'll be on my way.",
            "I'm legally required to follow you.",
            "Do you have any idea how much paperwork this creates?",
            "Failure to pay {gp} will result in a credit score drop."
    };

    private static final String[] COLLECTING_LINES = {
            "Let's verify this transaction...",
            "One... two... yes, very good.",
            "Standard deduction applied.",
            "Processing payment, please wait.",
            "I hope this covers the deductible.",
            "Let me just count this up.",
            "Don't worry, I have my receipt book right here.",
            "Liquid assets. Always preferred.",
            "Updating the ledger...",
            "Strictly business, nothing personal.",
            "Finally. Let's see if it's all here."
    };

    // --- ID CONSTANTS ---
    private static final int NPC_GILES_LAND = 5438;
    private static final int NPC_GILES_WATER = 5441;

    // ANIMATIONS
    private static final int ANIM_IDLE = 808;
    private static final int ANIM_WALK = 819;
    private static final int ANIM_WAVE = 863;
    private static final int ANIM_CRY = 860;

    // REGIONS
    private static final Set<Integer> UNDERWATER_REGIONS = new HashSet<>(Arrays.asList(
            15008, // Fossil Island Underwater (North)
            15264, // Fossil Island Underwater (South)
            11924, // Mogre Camp
            13194 // Coral nursery
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
            nextSpeechTick = client.getTickCount() + SPEECH_INTERVAL_TICKS;
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        agent.render();
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
            if (agent.isActive()) agent.despawn();
        }
    }

    private void runAgentBehavior()
    {
        // 1. Handle Dialogue (Timer Based)
        handleDialogue();

        // 2. Handle Movement / Spawning
        WorldPoint goal = paymentHandler.isTrackingPayment()
                ? paymentHandler.getPaymentLocation()
                : client.getLocalPlayer().getWorldLocation();

        int currentNpcId = isUnderwater ? NPC_GILES_WATER : NPC_GILES_LAND;

        if (!agent.isActive() || agent.getCurrentPos() == null)
        {
            WorldPoint spawnPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.spawn(spawnPos, currentNpcId);
        }

        if (LocalPoint.fromWorld(client, agent.getCurrentPos()) == null)
        {
            WorldPoint snapPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.snapTo(snapPos);
            return;
        }

        if (agent.getCurrentPos().distanceTo(goal) > 15 || agent.getCurrentPos().getPlane() != goal.getPlane())
        {
            WorldPoint newPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            agent.despawn();
            agent.spawn(newPos, currentNpcId);
            return;
        }

        int distance = agent.getCurrentPos().distanceTo(goal);

        if (distance > 1)
        {
            WorldPoint nextStep = agentPathFinder.findNextStep(agent.getCurrentPos(), goal);
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

    private void handleDialogue()
    {
        int currentTick = client.getTickCount();

        // Only speak if interval has passed
        if (currentTick >= nextSpeechTick)
        {
            if (paymentHandler.isTrackingPayment())
            {
                // He is picking up money (Collecting Mode)
                String line = COLLECTING_LINES[random.nextInt(COLLECTING_LINES.length)];
                say(line, 5);
            }
            else
            {
                // He is following (Nagging Mode)
                String line = NAGGING_LINES[random.nextInt(NAGGING_LINES.length)];

                // Inject the debt amount if the line requires it
                if (line.contains("{gp}"))
                {
                    // Format with commas (e.g. "1,250,000 coins")
                    String debtString = String.format("%,d coins", durabilityManager.getTotalRepairCost());
                    line = line.replace("{gp}", debtString);
                }

                say(line, 5);
            }

            // Reset Timer
            nextSpeechTick = currentTick + SPEECH_INTERVAL_TICKS;
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
}