package raf.deeplearning.greed_island.model.spaces;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.utils.Randomizer;
import raf.deeplearning.greed_island.model.loot.Stone;


@Getter
@Setter
public class Gate extends ASpace{

    /*
        Not final: a level can set its own price through the "gatePrice" of its config, and the
        price of a gate that is already on the map is the only thing that has to change for it.
     */
    private int priceOfEntrance;


    public Gate(int x, int y, int z, int priceOfEntrance) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
        this.priceOfEntrance = priceOfEntrance;
    }

    public String toString() {
        return "GATE (" + getX() +", "+ getY()+")";
    }


    @Override
    public char getSpaceSymbol() {
        return '|';
    }

    @Override
    public ILoot loot(Randomizer randomizer) {
        if(this.isLooted()) {
            return null;
        }
        this.setLooted(true);

        return new Stone();
    }

}
