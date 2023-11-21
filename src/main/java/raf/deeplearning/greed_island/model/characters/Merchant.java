package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class Merchant implements INonPlayableCharacter{

    private int x,y;
    private ASpace currentSpace;

    private int fullAmountOfGold;
    private transient int currentAmountOfGold;

    public Merchant(int x, int y, int fullAmountOfGold) {
        this.x = x;
        this.y = y;
        this.fullAmountOfGold = fullAmountOfGold;
        this.currentAmountOfGold = fullAmountOfGold;
    }

    @Override
    public void interactWithWorld(ASpace[][] view) {
        currentAmountOfGold = fullAmountOfGold;
    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }

    @Override
    public void setCoordinates(Pair p) {
        this.setX(p.getX1());
        this.setY(p.getX2());
    }

    @Override
    public void interactWithPlayer(Player player) {
        Map<String, Integer> playerInventory = player.getBagOfLoot();
        Map<String, Integer> merchantInventory = new HashMap<>();

        int tmpGoldOfMerchant = currentAmountOfGold;

        for(String lootName:playerInventory.keySet()) {
            try {
                Class<? extends ILoot> loot = Class.forName("raf.deeplearning.greed_island.model.loot."+lootName).asSubclass(ILoot.class);

                int numberOfAnItem = playerInventory.get(lootName);
                int valueOfAnItem = loot.getConstructor().newInstance().getValueInGold();

                int maxNumberOfAnItemToBut = tmpGoldOfMerchant/Math.max(1,valueOfAnItem);
                int actualNumberOfAnItemToBuy = Math.min(maxNumberOfAnItemToBut,numberOfAnItem);
                tmpGoldOfMerchant -= actualNumberOfAnItemToBuy*valueOfAnItem;
                playerInventory.put(lootName,numberOfAnItem-actualNumberOfAnItemToBuy);

                merchantInventory.put(lootName,actualNumberOfAnItemToBuy);

            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                    InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Merchant bought: " + merchantInventory + "\n With value: " + (fullAmountOfGold-tmpGoldOfMerchant));

        fullAmountOfGold = tmpGoldOfMerchant;
        player.setBagOfLoot(playerInventory);

        int tmpGold = player.getCurrentAmountOfGold();
        tmpGold += fullAmountOfGold-tmpGoldOfMerchant;
        player.setCurrentAmountOfGold(tmpGold);
    }

    @Override
    public void setCurrentSpace(ASpace space) {
        this.currentSpace = space;
    }

    @Override
    public char getCharacterSymbol() {
        return 'M';
    }
}

