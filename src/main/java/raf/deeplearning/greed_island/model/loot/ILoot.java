package raf.deeplearning.greed_island.model.loot;


import java.io.Serializable;

public interface ILoot extends Serializable {

    int getValueInGold();

    String toString() {
        return this.getClass().getSimpleName();
    }
}
