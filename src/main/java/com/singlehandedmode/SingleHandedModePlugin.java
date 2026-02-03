package com.singlehandedmode;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Single-Handed Mode"
)
public class SingleHandedModePlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    // --- LOGIC MODULES ---
    @Inject
    private HookStateManager hookState;

    @Inject
    private PlayerModelManager playerModelManager;

    @Inject
    private EquipmentRestrictionManager equipmentManager;

    @Inject
    private InteractionRestrictionManager interactionManager;

    @Inject
    private ShieldRestrictionOverlay shieldOverlay;

    @Inject
    private AbleismGenerator ableismGenerator;

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(shieldOverlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(shieldOverlay);
        // Note: Hands will naturally restore on next game animation/tick if we stop forcing them to 0
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // 1. Handle NPC Ableism
        ableismGenerator.maybeGenerateAbleistNpcComment(hookState.isPiratesHookEquipped());

        // 2. Update Hook State (Sync fallback)
        hookState.onGameTick();
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        // 1. Maintain Visuals (Run every frame to fight animation flicker)
        playerModelManager.updatePlayerModel();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        // 1. Update Truth State
        hookState.onItemContainerChanged(event);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // 1. Capture Context (Did we just click 'remove'?)
        hookState.captureClickIntent(event);

        // 2. Check: Can we remove the hook?
        equipmentManager.checkHookRemoval(event);
        if (event.isConsumed()) return;

        // 3. Check: Can we equip this item?
        equipmentManager.checkRestrictions(event);
        if (event.isConsumed()) return;

        // 4. Check: Can we perform this action?
        interactionManager.checkRestrictions(event);
    }

    @Provides
    SingleHandedModeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SingleHandedModeConfig.class);
    }
}