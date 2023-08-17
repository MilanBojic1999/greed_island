package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.utils.Pair;

@Getter
@Setter
@AllArgsConstructor
public class Villager implements INonPlayableCharacter{

    int x,y;
    int fullAmountOfGold;
    int currentAmountOfGold;

    @Override
    public void interactWithWorld() {
        currentAmountOfGold = fullAmountOfGold;
    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }

    @Override
    public void interactWithPlayer() {

    }
}

