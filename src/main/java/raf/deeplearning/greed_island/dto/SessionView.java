package raf.deeplearning.greed_island.dto;

/**
 * One line about a session the server is keeping, for GET /game/sessions.
 *
 * @param gameId      the id to address this session with
 * @param mapNumber   the level it is playing, -1 for the generated fallback map
 * @param seed        the seed of its current episode
 * @param round       how many rounds have been played in it
 * @param done        whether its episode is over
 * @param won         whether that episode ended at the gate
 * @param turnBased   true when its rounds are made by /game/step, false for the old round loop
 * @param idleSeconds how long ago it was last used - idle sessions are dropped after 30 minutes
 */
public record SessionView(
        String gameId,
        int mapNumber,
        long seed,
        int round,
        boolean done,
        boolean won,
        boolean turnBased,
        long idleSeconds
) {
}
