package com.singlehandedmode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@Singleton
public class AgentPathFinder
{
    private final Client client;
    private static final int MAX_DEPTH = 20;

    @Inject
    public AgentPathFinder(Client client)
    {
        this.client = client;
    }

    public WorldPoint findNextStep(WorldPoint start, WorldPoint target)
    {
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

            // Success Conditions
            if (current.x == targetX && current.y == targetY) { solution = current; break; }
            if (Math.abs(current.x - targetX) <= 1 && Math.abs(current.y - targetY) <= 1) { solution = current; break; }

            if (getPathLength(current) >= MAX_DEPTH) continue;

            // Cardinal Directions
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
            // Backtrack to first step
            Node step = solution;
            while (step.parent != null && step.parent.parent != null) step = step.parent;
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

        if (dy == 1 && dx == 0) { // North
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
        }
        else if (dy == -1 && dx == 0) { // South
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) return false;
        }
        else if (dx == 1 && dy == 0) { // East
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
        }
        else if (dx == -1 && dy == 0) { // West
            if ((currentFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) return false;
            if ((nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) return false;
        }

        return (nextFlag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }

    private static class Node { int x, y; Node parent; Node(int x, int y, Node parent) { this.x = x; this.y = y; this.parent = parent; } }
    private int getPathLength(Node node) { int count = 0; while (node.parent != null) { node = node.parent; count++; } return count; }
}