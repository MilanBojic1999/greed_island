package raf.deeplearning.greed_island.model.spaces;

public class Wood extends ASpace{

    public Wood(int x, int y,int z) {
        super(x,y,z,SpaceType.UNDISCOVERED,true);
    }

    @Override
    public String toString() {
        return "WOOD (" + getX() +", "+ getY()+")";
    }


    @Override
    public char getSpaceSymbol() {
        return '+';
    }
}
