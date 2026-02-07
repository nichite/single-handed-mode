package com.singlehandedmode;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class InsuranceAgent
{
    private final Client client;

    @Getter
    private RuneLiteObject rlo;

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

    private void loadModel(int npcId)
    {
        NPCComposition config = client.getNpcDefinition(npcId);
        if (config == null) return;

        int[] modelIds = config.getModels();
        if (modelIds == null) return;

        net.runelite.api.ModelData[] parts = new net.runelite.api.ModelData[modelIds.length];
        for (int i = 0; i < modelIds.length; i++) parts[i] = client.loadModelData(modelIds[i]);

        net.runelite.api.ModelData mergedData = client.mergeModels(parts);

        // Apply basic colors (Essential for kits to be visible)
        short[] replace = config.getColorToReplace();
        short[] replaceWith = config.getColorToReplaceWith();
        if (replace != null && replaceWith != null)
        {
            for (int i = 0; i < replace.length && i < replaceWith.length; ++i)
            {
                mergedData.recolor(replace[i], replaceWith[i]);
            }
        }

        rlo.setModel(mergedData.light(64, 850, -30, -50, -30));
    }
}