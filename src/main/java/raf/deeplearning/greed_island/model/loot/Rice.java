package raf.deeplearning.greed_island.model.loot;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Rice implements ILoot{

    int value;

    public Rice() {
        value = 4;
    }

    @Override
    public int getValueInGold() {
        return value;
    }
}
