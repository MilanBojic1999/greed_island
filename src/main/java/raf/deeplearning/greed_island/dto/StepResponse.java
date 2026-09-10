package raf.deeplearning.greed_island.dto;

import java.util.List;
import java.util.Map;

/**
 * The whole result of one environment transition, the way an RL agent needs it.
 *
 * @param matrix       the full map as symbols, characters drawn over the spaces they stand on
 * @param inventory    the player's loot, item name to count, empty stacks left out, key sorted
 * @param gold         the gold the player has earned so far
 * @param round        how many rounds have been played on this map
 * @param done         true when the episode is over
 * @param won          true when the episode is over because the player opened the gate
 * @param events       everything that happened during this round, see the event vocabulary
 * @param gatePrice    the gold needed to open the gate, -1 when the map has no gate
 * @param merchantGold the gold the merchant can still spend, -1 when the map has no merchant
 * @param seed         seed of the map
 */
public record StepResponse(
        String[][] matrix,
        Map<String, Integer> inventory,
        int gold,
        int round,
        boolean done,
        boolean won,
        List<String> events,
        int gatePrice,
        int merchantGold,
        long seed
) {
}
