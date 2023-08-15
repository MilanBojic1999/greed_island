package raf.deeplearning.greed_island.model.spaces;

public class Pasture extends ASpace{

    public Pasture(int x, int y,int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
    }

    @Override
    public String toString() {
        return "PASTURE (" + getX() +", "+ getY()+")";
    }


    @Override
    public char getSpaceSymbol() {
        return '_';
    }
}
