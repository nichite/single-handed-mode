package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;

@Singleton
public class PlayerModelManager
{
    @Inject
    private Client client;

    @Inject
    private SingleHandedModeConfig config;

    @Inject
    private HookStateManager hookState;

    private int lastHandId = -1;
    private int lastArmId = -1;

    public void updatePlayerModel()
    {
        Player player = client.getLocalPlayer();
        if (player == null) return;
        PlayerComposition playerComposition = player.getPlayerComposition();
        if (playerComposition == null) return;

        int[] equipmentIds = playerComposition.getEquipmentIds();

        if (hookState.isWearingFunctionalHook())
        {
            restoreHandSlot(equipmentIds);
            restoreArmSlot(equipmentIds);
        }
        else
        {
            // NO HOOK
            if (config.shouldRemoveLimb())
            {
                if (config.amputationLevel() == SingleHandedModeConfig.AmputationLevel.TRANSRADIAL)
                {
                    hideHandSlot(equipmentIds);
                    restoreArmSlot(equipmentIds);
                }
                else // SHOULDER
                {
                    hideHandSlot(equipmentIds);
                    hideArmSlot(equipmentIds);
                }
            }
            else
            {
                restoreHandSlot(equipmentIds);
                restoreArmSlot(equipmentIds);
            }
        }
        playerComposition.setHash();
    }

    private void hideHandSlot(int[] equipmentIds) {
        hideSlot(equipmentIds, KitType.HANDS, true);
    }

    private void hideArmSlot(int[] equipmentIds)
    {
        hideSlot(equipmentIds, KitType.ARMS, false);
    }

    private void hideSlot(int[] equipmentIds, KitType type, boolean isHandSlot)
    {
        int index = type.getIndex();
        int currentId = equipmentIds[index];

        if (currentId != 0)
        {
            if (isHandSlot) lastHandId = currentId;
            else lastArmId = currentId;
        }
        // Always force overwrite to 0 to fight animation engine
        equipmentIds[index] = 0;
    }

    private void restoreHandSlot(int[] equipmentIds) {
        restoreSlot(equipmentIds, KitType.HANDS, lastHandId);
    }

    private void restoreArmSlot(int[] equipmentIds) {
        restoreSlot(equipmentIds, KitType.ARMS, lastArmId);
    }

    private void restoreSlot(int[] equipmentIds, KitType type, int savedId)
    {
        int index = type.getIndex();
        if (equipmentIds[index] == 0 && savedId != -1)
        {
            equipmentIds[index] = savedId;
        }
    }
}