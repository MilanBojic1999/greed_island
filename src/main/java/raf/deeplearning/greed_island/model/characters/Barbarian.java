package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.utils.Pair;
import raf.deeplearning.greed_island.model.utils.Randomizer;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class Barbarian implements INonPlayableCharacter{

    private int x,y;

    @Override
    public void interactWithWorld() {

    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }

    @Override
    public void interactWithPlayer(Player player) {
        Map<String,Integer> bag = player.getBagOfLoot();

        String[] items = (String[]) bag.keySet().toArray();

        int itemInd = Randomizer.getInstance().randomInt(items.length);

        int itemCount = bag.get(items[itemInd]);

        int takeCount = Randomizer.getInstance().randomInt(itemCount);

        try {
            player.removeItems(items[itemInd],takeCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public char getCharacterSymbol() {
        return 'B';
    }
}
