package raf.deeplearning.greed_island.model.characters;

import raf.deeplearning.greed_island.model.utils.Pair;
import raf.deeplearning.greed_island.model.utils.Randomizer;

import java.util.Map;

public class Barbarian implements INonPlayableCharacter{

    @Override
    public void interactWithWorld() {

    }

    @Override
    public Pair getCoordinates() {
        return null;
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
}
