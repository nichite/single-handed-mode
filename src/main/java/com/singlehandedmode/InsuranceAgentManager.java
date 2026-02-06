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

    // NPC ID for Giles (5438)
    private static final int GILES_NPC_ID = 5438;

    // STATE TRACKING
    private WorldPoint prevPos = null;
    private WorldPoint currPos = null;
    private long lastTickTime = 0;
    private int currentAnimationId = -1;

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

        if (!durabilityManager.hasUnpaidDebt())
        {
            despawnAgent();
            return;
        }

        WorldPoint goal = paymentHandler.isTrackingPayment()
                ? paymentHandler.getPaymentLocation()
                : client.getLocalPlayer().getWorldLocation();

        if (paymentHandler.isTrackingPayment()) mutterAboutNumbers();
        else maybeTalk();

        updateAgentLogic(goal);
    }

    private void updateAgentLogic(WorldPoint goal)
    {
        if (agent == null || currPos == null)
        {
            // Try to spawn 1 tile away, but ensure it's valid
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

        // 1. Safety Teleport (Distance > 15 or Plane change)
        if (currPos.distanceTo(goal) > 15 || currPos.getPlane() != goal.getPlane())
        {
            currPos = new WorldPoint(goal.getX() - 1, goal.getY(), goal.getPlane());
            prevPos = currPos;
            LocalPoint lp = LocalPoint.fromWorld(client, currPos);
            if (lp != null) agent.setLocation(lp, 0);
            return;
        }

        // 2. PATHFINDING: Find the next legal step
        int distance = currPos.distanceTo(goal);
        int animToPlay = ANIM_IDLE;

        // Only move if we are not adjacent to the target
        if (distance > 1)
        {
            // Use BFS to find the next valid tile
            WorldPoint nextStep = findNextStep(currPos, goal);

            if (nextStep != null)
            {
                // We found a path! Move there.
                currPos = nextStep;
                animToPlay = ANIM_WALK;

                // Face where we are going
                rotateAgentTowards(currPos);
            }
            else
            {
                // No path found (blocked by wall), stay Idle.
                // Optionally: Face the target anyway so he looks at you through the wall
                rotateAgentTowards(goal);
            }
        }
        else
        {
            // Close enough, just look at player
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

    /**
     * A Breadth-First Search (BFS) to find the next step towards the target
     * while respecting Collision Maps (Walls/Objects).
     */
    private WorldPoint findNextStep(WorldPoint start, WorldPoint target)
    {
        // Limit search depth to avoid lag (20 tiles is plenty for a follower)
        final int MAX_DEPTH = 20;

        // 1. Get Collision Data
        CollisionData[] collisionMaps = client.getCollisionMaps();
        if (collisionMaps == null) return null;

        int plane = client.getPlane();
        int[][] flags = collisionMaps[plane].getFlags();

        // Use LocalPoints for collision checks (Scene Coordinates)
        LocalPoint startLp = LocalPoint.fromWorld(client, start);
        LocalPoint targetLp = LocalPoint.fromWorld(client, target);
        if (startLp == null || targetLp == null) return null;

        int startX = startLp.getSceneX();
        int startY = startLp.getSceneY();
        int targetX = targetLp.getSceneX();
        int targetY = targetLp.getSceneY();

        // 2. BFS Setup
        Queue<Node> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(new Node(startX, startY, null));
        visited.add((startX << 16) | startY); // Simple hash for coordinate pair

        Node solution = null;

        // 3. Run Search
        while (!queue.isEmpty())
        {
            Node current = queue.poll();

            // Check if we reached target
            if (current.x == targetX && current.y == targetY)
            {
                solution = current;
                break;
            }

            // Or if we are adjacent to target (since we stop 1 tile away)
            if (Math.abs(current.x - targetX) <= 1 && Math.abs(current.y - targetY) <= 1)
            {
                solution = current;
                break;
            }

            // Stop if too deep
            if (getPathLength(current) >= MAX_DEPTH) continue;

            // Check Neighbors (North, South, East, West)
            // Order: Try to prioritize direction of target for efficiency
            int[][] directions = {{0, 1}, {0, -1}, {-1, 0}, {1, 0}};

            for (int[] dir : directions)
            {
                int nextX = current.x + dir[0];
                int nextY = current.y + dir[1];

                // Bounds check
                if (nextX < 0 || nextY < 0 || nextX >= 104 || nextY >= 104) continue;

                // Visited check
                if (visited.contains((nextX << 16) | nextY)) continue;

                // COLLISION CHECK
                if (canMove(flags, current.x, current.y, dir[0], dir[1]))
                {
                    visited.add((nextX << 16) | nextY);
                    queue.add(new Node(nextX, nextY, current));
                }
            }
        }

        // 4. Backtrack to find the FIRST step
        if (solution != null)
        {
            Node step = solution;
            // Trace back until the parent is the start node
            while (step.parent != null && step.parent.parent != null)
            {
                step = step.parent;
            }

            // Convert back to WorldPoint
            return WorldPoint.fromScene(client, step.x, step.y, plane);
        }

        return null; // No path found
    }

    // Check if movement is allowed between two tiles
    private boolean canMove(int[][] flags, int currentX, int currentY, int dx, int dy)
    {
        int nextX = currentX + dx;
        int nextY = currentY + dy;
        int currentFlag = flags[currentX][currentY];
        int nextFlag = flags[nextX][nextY];

        // North
        if (dy == 1 && dx == 0)
        {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
        }
        // South
        else if (dy == -1 && dx == 0)
        {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
        }
        // East
        else if (dx == 1 && dy == 0)
        {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
        }
        // West
        else if (dx == -1 && dy == 0)
        {
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
        }

        // Check for full blocks (walls/objects) on the destination tile
        if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) return false;

        return true;
    }

    // Helper class for BFS
    private static class Node
    {
        int x, y;
        Node parent;

        Node(int x, int y, Node parent)
        {
            this.x = x;
            this.y = y;
            this.parent = parent;
        }
    }

    private int getPathLength(Node node)
    {
        int count = 0;
        while (node.parent != null) { node = node.parent; count++; }
        return count;
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (agent == null || !agent.isActive() || prevPos == null || currPos == null) return;

        LocalPoint startLp = LocalPoint.fromWorld(client, prevPos);
        LocalPoint endLp = LocalPoint.fromWorld(client, currPos);

        if (startLp == null || endLp == null)
        {
            if (endLp != null) agent.setLocation(endLp, 0);
            return;
        }

        long timePassed = System.currentTimeMillis() - lastTickTime;
        float progress = (float) timePassed / Constants.GAME_TICK_LENGTH;
        if (progress > 1.0f) progress = 1.0f;

        int x = (int) (startLp.getX() + ((endLp.getX() - startLp.getX()) * progress));
        int y = (int) (startLp.getY() + ((endLp.getY() - startLp.getY()) * progress));

        agent.setLocation(new LocalPoint(x, y), 0);
    }

    private void rotateAgentTowards(WorldPoint target)
    {
        // For 'Next Step' rotation, we want to face the IMMEDIATE tile we are moving to
        // If we are idle, we face the final target.
        WorldPoint faceTarget = (currPos.distanceTo(target) <= 1) ? target : currPos;

        // If we are moving, faceTarget is actually 'currPos' because 'currPos'
        // has already been updated to the destination in the logic step above.
        // But we want to calculate rotation based on PREVIOUS pos vs CURRENT pos.

        if (prevPos != null && !prevPos.equals(currPos)) {
            double dx = currPos.getX() - prevPos.getX();
            double dy = currPos.getY() - prevPos.getY();
            double theta = Math.atan2(dy, dx);
            int jagexOri = (int) (1536 - (theta * 1024 / Math.PI)) & 2047;
            agent.setOrientation(jagexOri);
        } else {
            // Idle facing logic
            double dx = target.getX() - currPos.getX();
            double dy = target.getY() - currPos.getY();
            double theta = Math.atan2(dy, dx);
            int jagexOri = (int) (1536 - (theta * 1024 / Math.PI)) & 2047;
            agent.setOrientation(jagexOri);
        }
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
        if (lp != null) agent.setLocation(lp, 0);
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