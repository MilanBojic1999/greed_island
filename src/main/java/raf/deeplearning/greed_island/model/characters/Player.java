package raf.deeplearning.greed_island.model.characters;

import raf.deeplearning.greed_island.model.utils.Pair;

public class Player implements ICharacter{

    private int x,y;

    @Override
    public void interactWithWorld() {

    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }
}
