package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.utils.Pair;

@Getter
@Setter
@AllArgsConstructor
public class Merchant implements INonPlayableCharacter{

    private int x,y;
    private int fullAmountOfGold;
    private int currentAmountOfGold;

    public Merchant(int x, int y, int fullAmountOfGold) {
        this.x = x;
        this.y = y;
        this.fullAmountOfGold = fullAmountOfGold;
        this.currentAmountOfGold = fullAmountOfGold;
    }

    @Override
    public void interactWithWorld() {
        currentAmountOfGold = fullAmountOfGold;
    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }

    @Override
    public void interactWithPlayer(Player player) {

    }
}

