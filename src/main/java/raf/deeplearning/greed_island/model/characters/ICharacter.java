package raf.deeplearning.greed_island.model.characters;

import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

public interface ICharacter {
    /**
     * One move of this character. The world it lives in is handed in, so a character can only ever
     * act on the map it was asked about - which is what lets several sessions play at the same time.
     *
     * @param world the map this character is on
     * @param view  the crop of that map around the character, as GameMap.lookupForCharacter gives it
     */
    void interactWithWorld(GameMap world, ASpace[][] view);
    Pair<Number, Number> getCoordinates();
    void setCoordinates(Pair<Number, Number> p);
    char getCharacterSymbol();
    void setCurrentSpace(ASpace space);
    ASpace getCurrentSpace();
}
