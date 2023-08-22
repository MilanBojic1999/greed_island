package raf.deeplearning.greed_island.model.spaces;

import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.loot.Stone;

public class Gate extends ASpace{


    public Gate(int x, int y, int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
    }

    public String toString() {
        return "GATE (" + getX() +", "+ getY()+")";
    }


    @Override
    public char getSpaceSymbol() {
        return '|';
    }

    @Override
    public ILoot loot() {
        if(this.isLooted()) {
            return null;
        }
        this.setLooted(true);

        return new Stone();
    }
}
