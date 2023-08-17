package raf.deeplearning.greed_island.model.loot;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Wood implements ILoot{

    int value;

    public Wood() {
        this.value = 2;
    }

    @Override
    public int getValueInGold() {
        return value;
    }
}
