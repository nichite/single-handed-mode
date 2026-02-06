package com.singlehandedmode;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
    private EventBus eventBus;

    @Inject
    private SingleHandedModeConfig config;

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
    private BrokenHookOverlay brokenHookOverlay;

    @Inject
    private InsuranceAgentOverlay insuranceAgentOverlay;

    @Inject
    private AbleismGenerator ableismGenerator;

    @Inject
    private DurabilityManager durabilityManager;

    @Inject
    private InsuranceAgentManager agentManager;

    @Inject
    private PaymentHandler paymentHandler;

    @Inject
    private DoctorInteractionManager doctorInteractionManager;

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(insuranceAgentOverlay);
        updateOverlayState();
        eventBus.register(doctorInteractionManager);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(shieldOverlay);
        overlayManager.remove(brokenHookOverlay);
        overlayManager.remove(insuranceAgentOverlay);
        eventBus.unregister(doctorInteractionManager);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // filter by our group "singlehandedmode"
        if (!event.getGroup().equals("singlehandedmode")) return;

        // Check if the specific key for shields changed
        if (event.getKey().equals("disableShieldsNoHook"))
        {
            updateOverlayState();
        }
    }

    /**
     * Adds or removes the overlay based on the current config state.
     */
    private void updateOverlayState()
    {
        // Safe Pattern: Remove it first to ensure we don't duplicate it.
        // OverlayManager.remove() is safe to call even if it's not currently added.
        overlayManager.remove(shieldOverlay);
        overlayManager.remove(brokenHookOverlay);

        // Only add it back if the restriction is enabled
        if (config.disableShieldsNoHook())
        {
            overlayManager.add(shieldOverlay);
        }

        if (durabilityManager.isHookBroken()) {
            overlayManager.add(brokenHookOverlay);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        durabilityManager.onGameTick();
        hookState.onGameTick();
        agentManager.onGameTick();

        ableismGenerator.maybeGenerateAbleistNpcComment(hookState.isWearingFunctionalHook());
        updateOverlayState();
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
        paymentHandler.onMenuOptionClicked(event);

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

    @Subscribe
    public void onItemDespawned(ItemDespawned event)
    {
        paymentHandler.onItemDespawned(event);
    }

    @Provides
    SingleHandedModeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SingleHandedModeConfig.class);
    }
}