package com.singlehandedmode;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.Client;
import net.runelite.api.NPC;

import javax.inject.Inject;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;

@Slf4j
public class AbleismGenerator {

    @Inject
    Client client;

    @Inject
    SingleHandedModeConfig config;

    private final Random random = new Random();
    private int textCooldown = 0; // Ticks until next possible comment

    // People being openly rude, pitying, or disgusted.
    private static final String[] ADULT_NO_HOOK_COMMENTS = {
            "Eww, look at his arm...",
            "I'd just stay home if I looked like that.",
            "Gross.",
            "Don't stare, honey, it's rude.",
            "I feel so sorry for him.",
            "At least he's trying, bless his heart.",
            "Did a shark eat it?",
            "Keep that thing away from me.",
            "Do you think it's contagious?",
            "He shouldn't be adventuring like that."
    };

    // People being overly patronizing, calling you "brave" just for existing.
    private static final String[] ADULT_HOOK_COMMENTS = {
            "So brave!",
            "He's such an inspiration.",
            "If he can do it, I have no excuse!",
            "Wow, look at him go with his little hook.",
            "A true survivor!",
            "It's amazing he actually leaves the house.",
            "I'd give him a gold star if I could.",
            "You're a hero just for waking up today!",
            "So strong. So resilient.",
            "Look! He's acting like a normal person!"
    };

    private static final String[] CHILD_NO_HOOK_COMMENTS = {
            "Mommy, that man is broken!",
            "Where is your hand?",
            "Did you forget your hand at home?",
            "Ewww, it looks weird!",
            "Can it grow back like a lizard?",
            "Why is your arm short?",
            "Look! He's melting!",
            "I bet a dragon ate it."
    };

    private static final String[] CHILD_HOOK_COMMENTS = {
            "Are you a pirate?!",
            "Cool hook!",
            "Can you stab stuff with that?",
            "I want a hook hand too!",
            "Where is your parrot?",
            "Do you fight sharks?",
            "You look scary!",
            "Is that real silver?"
    };


    // 1. Child Keywords (High Priority)
    private static final Set<String> CHILD_KEYWORDS = ImmutableSet.of(
            "child", "boy", "girl", "kid", "orphan", "student"
    );

    // 2. Adult Human Keywords (The "Safe" List)
    private static final Set<String> HUMAN_KEYWORDS = ImmutableSet.of(
            "man", "woman", "guard", "farmer", "villager", "citizen",
            "banker", "shopkeeper", "merchant", "squire", "knight",
            "monk", "priest", "clerk", "forester", "aristocrat", "thief"
    );

    // ... imports ...

    // ... imports ...

    public void maybeGenerateAbleistNpcComment(boolean isPiratesHookEquipped) {
        if (!config.enableAbleism()) return;

        if (textCooldown > 0) {
            --textCooldown;
            return;
        }

        // 1. Calculate Window (Probability) using 1-100 scale
        int level = Math.max(1, Math.min(100, config.ableismLevel()));

        // Formula: 500 - (Level * 5)
        // Level 1:   495 ticks (~5 mins)
        // Level 80:  100 ticks (~60 secs)
        // Level 100: 0 ticks (Instant)
        int probabilityWindow = 500 - (level * 5);

        // Safety Clamp:
        // If Level 100, window becomes 0 -> Clamp to 1 (100% chance).
        if (probabilityWindow < 1) probabilityWindow = 1;

        // 2. Roll the dice
        // nextInt(1) always returns 0, guaranteeing a "hit" at Level 100.
        if (random.nextInt(probabilityWindow) == 0) {
            makeRandomNpcSpeak(isPiratesHookEquipped);

            // 3. Dynamic Cooldown
            // Level 80+: Disable safety cooldown to allow overlapping "mob" speech.
            // Level <80: Keep safety buffer (50% of window) to pace comments out.
            if (level >= 80) {
                textCooldown = 0;
            } else {
                textCooldown = probabilityWindow / 2;
            }
        }
    }

    public void makeRandomNpcSpeak(boolean isPiratesHookEquipped) {
        // 1. Get local player location once
        if (client.getLocalPlayer() == null) return;
        WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
        if (playerLoc == null) return;

        // 2. Use WorldView (replacing deprecated getNpcs)
        // WorldView.npcs() returns an Iterable, so we wrap it in StreamSupport
        List<NPC> candidates = StreamSupport.stream(client.getTopLevelWorldView().npcs().spliterator(), false)
                .filter(npc -> {
                    // Null Safety
                    if (npc == null || npc.getName() == null) return false;

                    // Distance Check (Reduced to 10)
                    if (npc.getWorldLocation().distanceTo(playerLoc) > 10) return false;

                    // If their overhead cycle is in the future, they are busy.
                    if (npc.getOverheadCycle() > 0) return false;

                    // Logic Check (Is it a Human/Child?)
                    return isHumanOrChild(npc);
                })
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return;

        // 3. Pick a random target
        NPC speaker = candidates.get(random.nextInt(candidates.size()));

        // 4. Determine Child vs Adult
        boolean isChild = checkNameMatch(speaker, CHILD_KEYWORDS);

        // 5. Select Dialogue based on Hook status
        String dialogue;
        if (isChild) {
            dialogue = isPiratesHookEquipped
                    ? CHILD_HOOK_COMMENTS[random.nextInt(CHILD_HOOK_COMMENTS.length)]
                    : CHILD_NO_HOOK_COMMENTS[random.nextInt(CHILD_NO_HOOK_COMMENTS.length)];
        } else {
            dialogue = isPiratesHookEquipped
                    ? ADULT_HOOK_COMMENTS[random.nextInt(ADULT_HOOK_COMMENTS.length)]
                    : ADULT_NO_HOOK_COMMENTS[random.nextInt(ADULT_NO_HOOK_COMMENTS.length)];
        }

        // 6. Apply Text
        speaker.setOverheadText(dialogue);
        speaker.setOverheadCycle(150);
    }

    private boolean isHumanOrChild(NPC npc) {
        NPCComposition comp = npc.getComposition();
        if (comp == null || !comp.isInteractible()) return false;

        // Check Child List
        if (checkNameMatch(npc, CHILD_KEYWORDS)) return true;

        // Check Adult List
        if (checkNameMatch(npc, HUMAN_KEYWORDS)) return true;

        return false;
    }

    /**
     * Returns true if the NPC's name contains any of the keywords (Case-Insensitive).
     */
    private boolean checkNameMatch(NPC npc, Set<String> keywords) {
        String name = npc.getName().toLowerCase();

        // Quick "Monster" Safety Check (Optional but recommended)
        // Prevents "Goblin Guard" from triggering as "Guard"
        if (name.contains("goblin") || name.contains("zombie") || name.contains("gnome")) {
            return false;
        }

        for (String keyword : keywords) {
            // We use 'contains' so "Market Guard" matches "Guard"
            if (name.contains(keyword)) return true;
        }
        return false;
    }
}
