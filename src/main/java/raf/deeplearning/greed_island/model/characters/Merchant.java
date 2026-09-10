package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.EpisodeConfig;
import raf.deeplearning.greed_island.model.GameMap;
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

    /*
        How often the budget comes back, in rounds, and how many rounds have been played since it
        last did. It is a field of the merchant and not a constant of the class, because it is one
        of the difficulty knobs a level sets - see EpisodeConfig.merchantRefillRounds.
     */
    private transient int refillRounds = EpisodeConfig.DEFAULT_MERCHANT_REFILL_ROUNDS;
    private transient int roundsSinceRefill;

    public Merchant(int x, int y, int fullAmountOfGold) {
        this.x = x;
        this.y = y;
        this.fullAmountOfGold = fullAmountOfGold;
        this.currentAmountOfGold = fullAmountOfGold;
        this.roundsSinceRefill = 0;
    }

    @Override
    public void interactWithWorld(ASpace[][] view) {
        if(++roundsSinceRefill < Math.max(1, refillRounds))
            return;

        roundsSinceRefill = 0;
        currentAmountOfGold = fullAmountOfGold;
        System.out.println(currentAmountOfGold);
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

                if(actualNumberOfAnItemToBuy > 0)
                    GameMap.getInstance().addEvent("SOLD " + actualNumberOfAnItemToBuy + " " + lootName
                            + " " + actualNumberOfAnItemToBuy*valueOfAnItem);

            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                    InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Merchant bought: " + merchantInventory + "\n With value: " + (currentAmountOfGold-tmpGoldOfMerchant));

        int tmpGold = player.getCurrentAmountOfGold();
        tmpGold += currentAmountOfGold-tmpGoldOfMerchant;

        currentAmountOfGold = tmpGoldOfMerchant;
        player.setBagOfLoot(playerInventory);

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

