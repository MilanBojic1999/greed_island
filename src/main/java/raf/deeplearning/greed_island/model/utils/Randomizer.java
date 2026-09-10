package raf.deeplearning.greed_island.model.utils;

import java.util.Random;

/**
 * The randomness of one world: loot draws, the wandering of the NPCs and the presents of the
 * villager all come from here.
 * <p>
 * There is one per GameMap and not one per process, which is what makes seeding survive parallel
 * play: eight sessions training at the same time each draw from their own seed, so an episode plays
 * out the same way no matter what the other seven are doing. Reach it through GameMap.randomizer().
 */
public class Randomizer {

    private final Random random;
    private long seed;

    public Randomizer(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public void reseed(long seed) {
        this.seed = seed;
        this.random.setSeed(seed);
    }

    public long getSeed() {
        return this.seed;
    }

    public float randomPresent() {
        return this.random.nextFloat();
    }
    /*
        0 - left
        1 - right
        2 - up
        3 - down
     */
    public int randomMove() {
        return this.random.nextInt(4);
    }

    public int randomInt(int bound) {
        return this.random.nextInt(bound);
    }
}
