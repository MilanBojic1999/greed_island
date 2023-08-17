package raf.deeplearning.greed_island.model.loot;

public class Ore implements ILoot{

    int value;

    public Ore() {
        this.value = 14;
    }

    @Override
    public int getValueInGold() {
        return value;
    }
}
