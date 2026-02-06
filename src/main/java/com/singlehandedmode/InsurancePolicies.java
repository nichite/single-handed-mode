package com.singlehandedmode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Currently unused
@Getter
@RequiredArgsConstructor
public enum InsurancePolicies
{
    // OPTION 1: THE BUDGET PLAN
    // Doctor: Mi-Gor (Zombie Pirate)
    // Lore: "Does it hurt? Good. That means it's working."
    BRONZE_BUCCANEER("Mi-Gor", 50_000, 2),

    // OPTION 2: THE STANDARD PLAN
    // Doctor: Surgeon General Tafani
    // Lore: Reliable, standard duel arena care.
    SILVER_STANDARD("Surgeon General Tafani", 200_000, 4),

    // OPTION 3: THE PREMIUM PLAN
    // Doctor: Nurse Wooned
    // Lore: Military-grade Shayzien trauma care. Built to last.
    GOLD_GUARDIAN("Nurse Wooned", 1_000_000, 10);

    private final String doctorName;
    private final int cost;
    private final int durationHours;
}