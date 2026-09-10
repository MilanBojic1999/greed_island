package raf.deeplearning.greed_island.dto;

/**
 * The configuration of the episode that is actually being played, read back from the world itself
 * rather than from what somebody asked for - so a map without a merchant reports a budget of -1
 * instead of the number that was requested for a merchant that is not there.
 *
 * @param gatePrice            the gold needed to open the gate, -1 when the map has no gate
 * @param merchantBudget       the gold the merchant trades with, -1 when the map has no merchant
 * @param merchantRefillRounds rounds between two refills of that budget
 * @param barbarianEnabled     whether a barbarian is on the map
 * @param villagerEnabled      whether a villager is on the map
 * @param maxRounds            the round limit of the episode, 0 when there is none
 */
public record EpisodeConfigView(
        int gatePrice,
        int merchantBudget,
        int merchantRefillRounds,
        boolean barbarianEnabled,
        boolean villagerEnabled,
        int maxRounds
) {
}
