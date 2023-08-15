package raf.deeplearning.greed_island.model.spaces;

public class Elevation extends ASpace{

    public Elevation(int x, int y, int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
    }

    @Override
    public String toString() {
        return "ELEVATION (" + getX() +", "+ getY()+")";
    }

    @Override
    public char getSpaceSymbol() {
        return '>';
    }
}
