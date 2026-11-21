package raf.deeplearning.greed_island.model.spaces;

import raf.deeplearning.greed_island.model.loot.*;
import raf.deeplearning.greed_island.model.loot.Wood;
import raf.deeplearning.greed_island.model.utils.Randomizer;

/**
 * The high ground. A mountain can be walked on, but only from an elevation or from another
 * mountain, and it can only be left the same way: elevations are the one way up and the one way
 * down. A mountain that touches no elevation is therefore a wall, which is how the levels use
 * mountains as obstacles. The rule itself lives in {@link raf.deeplearning.greed_island.model.GameMap#canStep}.
 */
public class Mountain extends ASpace{


    public Mountain(int x, int y,int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
    }

    @Override
    public String toString() {
        return "MOUNTAIN (" + getX() +", "+ getY()+")";
    }



    @Override
    public char getSpaceSymbol() {
        if(this.isLooted())
            return '^';
        return '$';
    }

    @Override
    public ILoot loot(Randomizer randomizer) {
        if(this.isLooted()) {
            return null;
        }
        this.setLooted(true);

        float number = randomizer.randomPresent();
        if (number > 0.94) {
            return new Gem();
        } else if(number > 0.90) {
            return new Ore();
        } else if(number>0.70) {
            return new Bones();
        } else {
            return new Stone();
        }
    }
}
