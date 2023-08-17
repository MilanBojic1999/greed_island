package raf.deeplearning.greed_island.model.loot;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Stone implements ILoot{

    int value;

    public Stone() {
        this.value = 3;
    }

    @Override
    public int getValueInGold() {
        return value;
    }
}
