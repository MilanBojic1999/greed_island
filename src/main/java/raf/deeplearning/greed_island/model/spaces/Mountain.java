package raf.deeplearning.greed_island.model.spaces;

public class Mountain extends ASpace{


    public Mountain(int x, int y,int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,false);
    }

    @Override
    public String toString() {
        return "MOUNTAIN (" + getX() +", "+ getY()+")";
    }



    @Override
    public char getSpaceSymbol() {
        return '$';
    }
}
