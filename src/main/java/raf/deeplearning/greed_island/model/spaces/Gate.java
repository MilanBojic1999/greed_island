package raf.deeplearning.greed_island.model.spaces;

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
}
