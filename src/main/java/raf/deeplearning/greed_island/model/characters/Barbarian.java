package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.util.Map;

@Getter
@Setter
public class Barbarian implements INonPlayableCharacter{

    private int x,y;
    private ASpace currentSpace;
    private transient int waitPeriod;

    private static int MaxWaitPeriod = 1;



    public Barbarian(int x, int y) {
        this.x = x;
        this.y = y;
        this.waitPeriod = MaxWaitPeriod;
    }

    @Override
    public void interactWithWorld(GameMap world, ASpace[][] view) {

        if(waitPeriod<MaxWaitPeriod) {
            ++waitPeriod;
            return;
        }

        this.waitPeriod = 0;

        chaseOrWander(world, view);
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
    public void interactWithPlayer(GameMap world, Player player) {
        System.out.println("Barbarian is taking your loot!");
        Map<String,Integer> bag = player.getBagOfLoot();
        System.out.println(bag);
        if(bag.isEmpty())
            return;
        String[] items = bag.keySet().toArray(String[]::new);

        int itemInd = world.randomizer().randomInt(items.length);

        int itemCount = bag.get(items[itemInd]);
        if(itemCount==0)
            return;
        // Barbarin uzima izmedju 1 i n objekata od igraca
        int takeCount = world.randomizer().randomInt(itemCount) + 1;
        System.out.println("Barbarian took " + takeCount + " " + items[itemInd] + " from you!");
        try {
            player.removeItems(items[itemInd],takeCount);
            if(takeCount > 0)
                world.addEvent("ROBBED " + takeCount + " " + items[itemInd]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public char getCharacterSymbol() {
        return 'B';
    }

    @Override
    public void setCurrentSpace(ASpace space) {
        this.currentSpace = space;
    }
}
