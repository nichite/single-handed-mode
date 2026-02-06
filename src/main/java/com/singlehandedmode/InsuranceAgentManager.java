package com.singlehandedmode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@Slf4j
public class InsuranceAgentManager
{
    private final Client client;
    private final DurabilityManager durabilityManager;
    private final PaymentHandler paymentHandler;

    @Getter
    private RuneLiteObject agent;

    @Getter
    private String overheadText = null;
    private int textExpiryTick = -1;

    private final Random random = new Random();

    // ANIMATIONS
    private static final int ANIM_IDLE = 808;
    private static final int ANIM_WALK = 819;
    private static final int ANIM_WAVE = 863; // Added
    private static final int ANIM_CRY = 860;  // Added

    // NPC ID for Giles (5438)
    private static final int GILES_NPC_ID = 5438;

    // STATE TRACKING
    private WorldPoint prevPos = null;
    private WorldPoint currPos = null;
    private long lastTickTime = 0;
    private int currentAnimationId = -1;

    // DEPARTURE STATE
    private boolean isLeaving = false;
    private int departureTick = -1;

    @Inject
    public InsuranceAgentManager(Client client, DurabilityManager durabilityManager, PaymentHandler paymentHandler)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
        this.paymentHandler = paymentHandler;
    }

    public void onGameTick()
    {
        if (textExpiryTick != -1 && client.getTickCount() > textExpiryTick) {
            overheadText = null; textExpiryTick = -1;
        }

        // 1. DEPARTURE SEQUENCE
        // If we are currently leaving, ignore normal logic
        if (isLeaving)
        {
            handleDepartureSequence();
            return;
        }

        // 2. CHECK STATUS
        if (!durabilityManager.hasUnpaidDebt())
        {
            // If agent is visible, trigger the wave goodbye
            if (agent != null && agent.isActive())
            {
                startDepartureSequence();
            }
            else
            {
                despawnAgent();
            }
            return;
        }

        // 3. NORMAL AI
        WorldPoint goal = paymentHandler.isTrackingPayment()
                ? paymentHandler.getPaymentLocation()
                : client.getLocalPlayer().getWorldLocation();

        if (paymentHandler.isTrackingPayment()) mutterAboutNumbers();
        else maybeTalk();

        updateAgentLogic(goal);
    }

    private void startDepartureSequence()
    {
        isLeaving = true;
        departureTick = 0;

        // Face the player
        WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
        rotateAgentTowards(playerLoc);

        // Agent Waves
        if (agent != null)
        {
            agent.setAnimation(client.loadAnimation(ANIM_WAVE));
            agent.setShouldLoop(false);
            currentAnimationId = ANIM_WAVE;
        }

        // Player Cries
        client.getLocalPlayer().setAnimation(ANIM_CRY);

        say("Pleasure doing business.", 5);
    }

    private void handleDepartureSequence()
    {
        departureTick++;

        // Keep updating time for the renderer, so he doesn't disappear
        lastTickTime = System.currentTimeMillis();

        // Wait 5 ticks then disappear
        if (departureTick >= 5)
        {
            despawnAgent();
            isLeaving = false;
        }
    }

    private void updateAgentLogic(WorldPoint goal)
    {
        if (agent == null || currPos == null)
        {
            // Try to spawn 1 tile away
            WorldPoint spawnPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            initializeAgent(spawnPos);
            prevPos = spawnPos;
            currPos = spawnPos;
        }
        else
        {
            prevPos = currPos;
        }

        lastTickTime = System.currentTimeMillis();

        // 1. Safety Teleport & Plane Change Check
        // FIX: Added plane check to ensure he teleports if you go up/down stairs
        if (currPos.distanceTo(goal) > 15 || currPos.getPlane() != goal.getPlane())
        {
            currPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            prevPos = currPos;

            // FIX: Use currPos.getPlane() instead of 0
            LocalPoint lp = LocalPoint.fromWorld(client, currPos);
            if (lp != null) agent.setLocation(lp, currPos.getPlane());
            return;
        }

        // 2. PATHFINDING
        int distance = currPos.distanceTo(goal);
        int animToPlay = ANIM_IDLE;

        if (distance > 1)
        {
            // Use BFS to find the next valid tile
            WorldPoint nextStep = findNextStep(currPos, goal);

            if (nextStep != null)
            {
                currPos = nextStep;
                animToPlay = ANIM_WALK;
                rotateAgentTowards(goal); // Strafe
            }
            else
            {
                rotateAgentTowards(goal);
            }
        }
        else
        {
            rotateAgentTowards(goal);
        }

        // 3. Update Animation
        if (currentAnimationId != animToPlay)
        {
            agent.setAnimation(client.loadAnimation(animToPlay));
            agent.setShouldLoop(true);
            currentAnimationId = animToPlay;
        }
    }

    private void rotateAgentTowards(WorldPoint target)
    {
        double dx = target.getX() - currPos.getX();
        double dy = target.getY() - currPos.getY();
        double theta = Math.atan2(dy, dx);
        int jagexOri = (int) (1536 - (theta * 1024 / Math.PI)) & 2047;
        agent.setOrientation(jagexOri);
    }

    /**
     * BFS Pathfinding (Collision Aware)
     */
    private WorldPoint findNextStep(WorldPoint start, WorldPoint target)
    {
        final int MAX_DEPTH = 20;
        CollisionData[] collisionMaps = client.getCollisionMaps();
        if (collisionMaps == null) return null;

        int plane = client.getPlane();
        int[][] flags = collisionMaps[plane].getFlags();

        LocalPoint startLp = LocalPoint.fromWorld(client, start);
        LocalPoint targetLp = LocalPoint.fromWorld(client, target);
        if (startLp == null || targetLp == null) return null;

        int startX = startLp.getSceneX();
        int startY = startLp.getSceneY();
        int targetX = targetLp.getSceneX();
        int targetY = targetLp.getSceneY();

        Queue<Node> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(new Node(startX, startY, null));
        visited.add((startX << 16) | startY);

        Node solution = null;

        while (!queue.isEmpty())
        {
            Node current = queue.poll();

            if (current.x == targetX && current.y == targetY) { solution = current; break; }
            if (Math.abs(current.x - targetX) <= 1 && Math.abs(current.y - targetY) <= 1) { solution = current; break; }
            if (getPathLength(current) >= MAX_DEPTH) continue;

            int[][] directions = {{0, 1}, {0, -1}, {-1, 0}, {1, 0}};

            for (int[] dir : directions)
            {
                int nextX = current.x + dir[0];
                int nextY = current.y + dir[1];

                if (nextX < 0 || nextY < 0 || nextX >= 104 || nextY >= 104) continue;
                if (visited.contains((nextX << 16) | nextY)) continue;

                if (canMove(flags, current.x, current.y, dir[0], dir[1]))
                {
                    visited.add((nextX << 16) | nextY);
                    queue.add(new Node(nextX, nextY, current));
                }
            }
        }

        if (solution != null)
        {
            Node step = solution;
            while (step.parent != null && step.parent.parent != null)
            {
                step = step.parent;
            }
            return WorldPoint.fromScene(client, step.x, step.y, plane);
        }
        return null;
    }

    private boolean canMove(int[][] flags, int currentX, int currentY, int dx, int dy)
    {
        int nextX = currentX + dx;
        int nextY = currentY + dy;
        int currentFlag = flags[currentX][currentY];
        int nextFlag = flags[nextX][nextY];

        if (dy == 1 && dx == 0) {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
        }
        else if (dy == -1 && dx == 0) {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
        }
        else if (dx == 1 && dy == 0) {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
        }
        else if (dx == -1 && dy == 0) {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
        }
        if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) return false;
        return true;
    }

    private static class Node { int x, y; Node parent; Node(int x, int y, Node parent) { this.x = x; this.y = y; this.parent = parent; } }
    private int getPathLength(Node node) { int count = 0; while (node.parent != null) { node = node.parent; count++; } return count; }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (agent == null || !agent.isActive() || prevPos == null || currPos == null) return;

        LocalPoint startLp = LocalPoint.fromWorld(client, prevPos);
        LocalPoint endLp = LocalPoint.fromWorld(client, currPos);

        if (startLp == null || endLp == null)
        {
            // FIX: Use currPos.getPlane() instead of 0
            if (endLp != null) agent.setLocation(endLp, currPos.getPlane());
            return;
        }

        long timePassed = System.currentTimeMillis() - lastTickTime;
        float progress = (float) timePassed / Constants.GAME_TICK_LENGTH;
        if (progress > 1.0f) progress = 1.0f;

        int x = (int) (startLp.getX() + ((endLp.getX() - startLp.getX()) * progress));
        int y = (int) (startLp.getY() + ((endLp.getY() - startLp.getY()) * progress));

        // FIX: Use currPos.getPlane() instead of 0
        agent.setLocation(new LocalPoint(x, y), currPos.getPlane());
    }

    private void initializeAgent(WorldPoint startPos)
    {
        agent = client.createRuneLiteObject();
        updateAgentModel(GILES_NPC_ID);
        agent.setShouldLoop(true);
        agent.setActive(true);
        agent.setAnimation(client.loadAnimation(ANIM_IDLE));
        currentAnimationId = ANIM_IDLE;
        LocalPoint lp = LocalPoint.fromWorld(client, startPos);

        // FIX: Use startPos.getPlane()
        if (lp != null) agent.setLocation(lp, startPos.getPlane());
    }

    private void updateAgentModel(int npcId)
    {
        net.runelite.api.NPCComposition config = client.getNpcDefinition(npcId);
        if (config == null) return;
        int[] modelIds = config.getModels();
        if (modelIds != null)
        {
            net.runelite.api.ModelData[] parts = new net.runelite.api.ModelData[modelIds.length];
            for (int i = 0; i < modelIds.length; i++) parts[i] = client.loadModelData(modelIds[i]);

            // NOTE: If mergeModels(ModelData[]) is not available, you must use the
            // Model[] array conversion logic. For now, keeping your starting point logic.
            net.runelite.api.ModelData mergedData = client.mergeModels(parts);

            net.runelite.api.Model finalModel = mergedData.light(64, 850, -30, -50, -30);
            agent.setModel(finalModel);
        }
    }

    public void say(String text, int durationTicks)
    {
        this.overheadText = text;
        this.textExpiryTick = client.getTickCount() + durationTicks;
    }
    private void maybeTalk() {
        if (random.nextInt(100) < 2) say("You owe " + durabilityManager.getTotalRepairCost() + " gp!", 4);
    }
    private void mutterAboutNumbers() {
        if (random.nextInt(100) < 3) say("Counting...", 4);
    }
    private void despawnAgent()
    {
        if (agent != null) agent.setActive(false);
        prevPos = null;
        currPos = null;
    }
}