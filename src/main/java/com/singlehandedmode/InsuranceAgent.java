package com.singlehandedmode;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Handles the visual representation of the agent:
 * Model loading, Rendering, and Interpolation.
 */
public class InsuranceAgent
{
    private final Client client;

    @Getter
    private RuneLiteObject rlo;

    private static final int GILES_NPC_ID = 5438;

    // Position Tracking
    @Getter private WorldPoint currentPos;
    @Getter private WorldPoint previousPos;
    private long lastTickTime;

    private int currentAnimId = -1;

    public InsuranceAgent(Client client)
    {
        this.client = client;
    }

    public void spawn(WorldPoint location)
    {
        despawn(); // Safety cleanup

        rlo = client.createRuneLiteObject();
        loadModel(GILES_NPC_ID);
        rlo.setShouldLoop(true);
        rlo.setActive(true);

        snapTo(location);
    }

    public void despawn()
    {
        if (rlo != null)
        {
            rlo.setActive(false);
            rlo = null;
        }
        previousPos = null;
        currentPos = null;
    }

    public boolean isActive()
    {
        return rlo != null && rlo.isActive();
    }

    /**
     * Moves the agent logically to a new tile.
     * The renderer will interpolate towards this tile over the next tick.
     */
    public void moveTo(WorldPoint newPos)
    {
        if (rlo == null) return;
        previousPos = currentPos;
        currentPos = newPos;
        lastTickTime = System.currentTimeMillis();
    }

    /**
     * Instantly moves the agent without interpolation (Teleport).
     */
    public void snapTo(WorldPoint newPos)
    {
        if (rlo == null) return;
        currentPos = newPos;
        previousPos = newPos;
        lastTickTime = System.currentTimeMillis();

        LocalPoint lp = LocalPoint.fromWorld(client, newPos);
        if (lp != null) rlo.setLocation(lp, newPos.getPlane());
    }

    public void setAnimation(int animId)
    {
        setAnimation(animId, true);
    }

    public void setAnimation(int animId, boolean shouldLoop)
    {
        if (rlo == null) return;
        if (currentAnimId != animId)
        {
            rlo.setAnimation(client.loadAnimation(animId));
            rlo.setShouldLoop(shouldLoop);
            currentAnimId = animId;
        }
    }

    public void faceTarget(WorldPoint target)
    {
        if (rlo == null || currentPos == null) return;
        double dx = target.getX() - currentPos.getX();
        double dy = target.getY() - currentPos.getY();
        double theta = Math.atan2(dy, dx);
        int jagexOri = (int) (1536 - (theta * 1024 / Math.PI)) & 2047;
        rlo.setOrientation(jagexOri);
    }

    /**
     * Updates the visual location of the model (Interpolation).
     * Call this on ClientTick.
     */
    public void render()
    {
        if (rlo == null || !rlo.isActive() || previousPos == null || currentPos == null) return;

        LocalPoint startLp = LocalPoint.fromWorld(client, previousPos);
        LocalPoint endLp = LocalPoint.fromWorld(client, currentPos);

        // If off-map or scene changed, we can't interpolate.
        // Snap to current if possible.
        if (startLp == null || endLp == null)
        {
            if (endLp != null) rlo.setLocation(endLp, currentPos.getPlane());
            return;
        }

        long timePassed = System.currentTimeMillis() - lastTickTime;
        float progress = (float) timePassed / Constants.GAME_TICK_LENGTH;
        if (progress > 1.0f) progress = 1.0f;

        int x = (int) (startLp.getX() + ((endLp.getX() - startLp.getX()) * progress));
        int y = (int) (startLp.getY() + ((endLp.getY() - startLp.getY()) * progress));

        rlo.setLocation(new LocalPoint(x, y), currentPos.getPlane());
    }

    private void loadModel(int npcId)
    {
        net.runelite.api.NPCComposition config = client.getNpcDefinition(npcId);
        if (config == null || config.getModels() == null) return;

        int[] modelIds = config.getModels();
        net.runelite.api.ModelData[] parts = new net.runelite.api.ModelData[modelIds.length];
        for (int i = 0; i < modelIds.length; i++) parts[i] = client.loadModelData(modelIds[i]);

        net.runelite.api.ModelData mergedData = client.mergeModels(parts);
        rlo.setModel(mergedData.light(64, 850, -30, -50, -30));
    }
}