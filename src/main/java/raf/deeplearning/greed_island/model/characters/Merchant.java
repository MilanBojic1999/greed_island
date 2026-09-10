package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.EpisodeConfig;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    public void interactWithWorld(GameMap world, ASpace[][] view) {
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

    /**
     * Buys what the player is carrying, as far as the gold of this refill goes.
     * <p>
     * The order the stacks are offered in is what decides which of them still fit in the budget
     * once it starts binding, so it must not be the order a hash map happens to hand them out in:
     * the most valuable item goes first and stacks worth the same are taken by name. That makes
     * "sell now or carry on looting" a decision with a predictable answer, which is the point of
     * the budget in the first place.
     */
    @Override
    public void interactWithPlayer(GameMap world, Player player) {
        Map<String, Integer> playerInventory = player.getBagOfLoot();
        Map<String, Integer> merchantInventory = new TreeMap<>();

        List<String> offered = new ArrayList<>(playerInventory.keySet());
        Comparator<String> byValue = Comparator.comparingInt(Merchant::goldValueOf);
        offered.sort(byValue.reversed().thenComparing(Comparator.naturalOrder()));

        int tmpGoldOfMerchant = currentAmountOfGold;

        for(String lootName:offered) {
            int valueOfAnItem = goldValueOf(lootName);
            int numberOfAnItem = playerInventory.get(lootName);

            int maxNumberOfAnItemToBuy = tmpGoldOfMerchant/Math.max(1,valueOfAnItem);
            int actualNumberOfAnItemToBuy = Math.min(maxNumberOfAnItemToBuy,numberOfAnItem);
            if(actualNumberOfAnItemToBuy <= 0)
                continue;

            tmpGoldOfMerchant -= actualNumberOfAnItemToBuy*valueOfAnItem;

            try {
                // through the player, so that a stack that is sold out leaves the bag instead of
                // staying in it as a zero the merchant would keep being offered next time
                player.removeItems(lootName, actualNumberOfAnItemToBuy);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            merchantInventory.put(lootName,actualNumberOfAnItemToBuy);
            world.addEvent("SOLD " + actualNumberOfAnItemToBuy + " " + lootName
                    + " " + actualNumberOfAnItemToBuy*valueOfAnItem);
        }

        int paid = currentAmountOfGold - tmpGoldOfMerchant;
        System.out.println("Merchant bought: " + merchantInventory + "\n With value: " + paid);

        currentAmountOfGold = tmpGoldOfMerchant;
        player.setCurrentAmountOfGold(player.getCurrentAmountOfGold() + paid);
    }

    /**
     * What one item of a kind of loot is worth, looked up from the name it is kept under in the
     * bag. An unknown name is a bug in the map or in the loot package, not something to trade
     * around, so it is thrown rather than priced at zero.
     */
    private static int goldValueOf(String lootName) {
        try {
            Class<? extends ILoot> loot = Class.forName("raf.deeplearning.greed_island.model.loot."+lootName)
                    .asSubclass(ILoot.class);
            return loot.getConstructor().newInstance().getValueInGold();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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

