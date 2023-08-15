package raf.deeplearning.greed_island.model.spaces;

public class Water extends ASpace{
    public Water(int x, int y, int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,false);
    }

    @Override
    public String toString() {
        return "WATER (" + getX() +", "+ getY()+")";
    }


    @Override
    public char getSpaceSymbol() {
        return '-';
    }
}
