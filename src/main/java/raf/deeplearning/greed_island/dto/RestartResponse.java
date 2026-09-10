package raf.deeplearning.greed_island.dto;

/**
 * The answer of a restart: everything needed to write down which episode was just started, so that
 * a run in a training log can be reproduced later on.
 *
 * @param seed      the seed the episode runs on, drawn at random when none was asked for
 * @param mapNumber the level that was loaded
 * @param config    the configuration the episode ended up with, map defaults and overrides applied
 */
public record RestartResponse(
        long seed,
        int mapNumber,
        EpisodeConfigView config
) {
}
