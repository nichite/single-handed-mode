package com.singlehandedmode;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;
import net.runelite.client.eventbus.Subscribe;

public class DoctorInteractionManager
{
    @Inject
    private Client client;
    @Inject
    private DurabilityManager durabilityManager;
    @Inject
    private SingleHandedModeConfig config;
    @Inject
    private FakeDialogueManager dialogueManager;

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
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

//    @Subscribe
//    public void onMenuOptionClicked(MenuOptionClicked event)
//    {
//        if (event.getMenuOption().equals("Fit-Prosthetic"))
//        {
//            event.consume();
//
//            // 1. Get Actor ID for the face
//            int doctorId = 3343; // Default Tafani
//            net.runelite.api.Actor actor = event.getMenuEntry().getActor();
//            if (actor instanceof net.runelite.api.NPC)
//            {
//                doctorId = ((net.runelite.api.NPC) actor).getId();
//            }
//
//            // 2. Determine Text
//    //        String name = config.insurancePolicy().getDoctorName();
//            String text = "I've reattached the hook. It should hold for about " +
//                    Math.round(config.hookDurabilityTicks() / 6000.0) +
//                    " hours. Try not to break it again, okay?";
//
//            // 3. Trigger the Cinematic Dialogue
//            dialogueManager.openNpcDialogue(doctorId, "Surgeon General Tafani ", text, () ->
//            {
//                // This runs when the user clicks "Continue"
//                client.getLocalPlayer().setAnimation(862); // Cheer
//                durabilityManager.repairHook();
//            });
//        }
//    }
}