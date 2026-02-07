package com.singlehandedmode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;

@Slf4j
public class InsuranceAgent
{
    private final Client client;

    @Getter
    private RuneLiteObject rlo;

    // Constant for the water check
    private static final int NPC_GILES_WATER = 5441;

    @Getter private WorldPoint currentPos;
    @Getter private WorldPoint previousPos;
    private long lastTickTime;

    private int currentAnimId = -1;

    public InsuranceAgent(Client client)
    {
        this.client = client;
    }

    public void spawn(WorldPoint location, int npcId)
    {
        despawn();
        rlo = client.createRuneLiteObject();

        loadModel(npcId);

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

    public void moveTo(WorldPoint newPos)
    {
        if (rlo == null) return;

        // If we have no history, snap instead of sliding
        if (currentPos == null)
        {
            snapTo(newPos);
            return;
        }

        previousPos = currentPos;
        currentPos = newPos;
        lastTickTime = System.currentTimeMillis();
    }

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

    public void render()
    {
        if (rlo == null || !rlo.isActive() || previousPos == null || currentPos == null) return;

        LocalPoint startLp = LocalPoint.fromWorld(client, previousPos);
        LocalPoint endLp = LocalPoint.fromWorld(client, currentPos);

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

    // TODO: fix missing legs
    private void loadModel(int npcId)
    {
        net.runelite.api.NPCComposition config = client.getNpcDefinition(npcId);
        if (config == null || config.getModels() == null) return;

        int[] modelIds = config.getModels();
        net.runelite.api.ModelData[] parts = new net.runelite.api.ModelData[modelIds.length];
        for (int i = 0; i < modelIds.length; i++) parts[i] = client.loadModelData(modelIds[i]);

        net.runelite.api.ModelData mergedData = client.mergeModels(parts);

        // Z-INDEX FIX: Lift him higher in the water (-200)
        if (npcId == NPC_GILES_WATER)
        {
            mergedData.translate(0, -200, 0);
        }

        rlo.setModel(mergedData.light(64, 850, -30, -50, -30));
    }
}