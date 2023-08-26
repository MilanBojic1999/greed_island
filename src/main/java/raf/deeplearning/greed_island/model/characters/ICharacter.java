package raf.deeplearning.greed_island.model.characters;

import raf.deeplearning.greed_island.model.utils.Pair;

public interface ICharacter {
    void interactWithWorld();
    Pair getCoordinates();
    char getCharacterSymbol();
}
