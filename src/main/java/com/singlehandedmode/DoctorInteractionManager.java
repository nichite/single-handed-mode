package com.singlehandedmode;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;

public class DoctorInteractionManager
{
    private final Client client;
    private final DurabilityManager durabilityManager;
    private final SingleHandedModeConfig config;

    @Inject
    public DoctorInteractionManager(Client client, DurabilityManager durabilityManager, SingleHandedModeConfig config)
    {
        this.client = client;
        this.durabilityManager = durabilityManager;
        this.config = config;
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        // 1. Only modify menu if we are actually ready for surgery
        if (!durabilityManager.isHookBroken() || durabilityManager.hasUnpaidDebt()) return;

        String target = Text.removeTags(event.getTarget());
        String option = Text.removeTags(event.getOption());

        String requiredDoctor = "surgeon general tafani";

        if (target.equalsIgnoreCase(requiredDoctor) && option.equalsIgnoreCase("Talk-to"))
        {
            event.getMenuEntry().setOption("Fit-Prosthetic");
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuOption().equals("Fit-Prosthetic"))
        {
            event.consume();

            net.runelite.api.Actor actor = event.getMenuEntry().getActor();
            if (actor != null)
            {
//                String doctorName = config.insurancePolicy().getDoctorName();
                String msg = "All fixed! Try not to break this one.";

                // Optional: Custom lines based on who it is
//                if (doctorName.contains("Mi-Gor")) msg = "Arrr, good as new!";
//                if (doctorName.contains("Wooned")) msg = "Battle ready. Dismissed.";

                actor.setOverheadText(msg);
                actor.setOverheadCycle(80); // Shows for ~2.5 seconds
            }

            // Player Animation (Cheer)
            client.getLocalPlayer().setAnimation(862);

            durabilityManager.repairHook();
        }
    }
}