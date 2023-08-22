package raf.deeplearning.greed_island.model.utils;

import java.util.Random;

public class Randomizer {

    private static Randomizer instance;
    private final Random random;


    private Randomizer() {
        this.random = new Random();
    }

    public static Randomizer getInstance() {
        if(instance == null)
            instance = new Randomizer();
        return instance;
    }

    public float randomPresent() {
        return this.random.nextFloat();
    }

    public int randomMove() {
        return this.random.nextInt(4);
    }
}
