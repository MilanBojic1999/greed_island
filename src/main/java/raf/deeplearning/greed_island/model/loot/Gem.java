package raf.deeplearning.greed_island.model.loot;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Gem implements ILoot{

    int value;

    public Gem() {
        this.value = 20;
    }

    @Override
    public int getValueInGold() {
        return value;
    }
}