package raf.deeplearning.greed_island.model.spaces;

import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.utils.Randomizer;

public class Water extends ASpace{
    public Water(int x, int y, int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,false);
    }

    @Override
    public String toString() {
        return "WATER (" + getX() +", "+ getY()+")";
    }

    @Override
    public ILoot loot(Randomizer randomizer) {
        return null;
    }


    @Override
    public char getSpaceSymbol() {
        return '-';
    }
}
