package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.loot.*;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

@Getter
@Setter
@AllArgsConstructor
public class Villager implements INonPlayableCharacter{

    private int x,y;
    private ASpace currentSpace;
    private transient int waitPeriod;

    private static int MaxWaitPeriod = 2;


    public Villager(int x, int y) {
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
    public void setCoordinates(Pair p) {
        this.setX(p.getX1());
        this.setY(p.getX2());
    }

    @Override
    public Pair getCoordinates() {
        return new Pair(x,y);
    }

    @Override
    public void interactWithPlayer(GameMap world, Player player) {
        System.out.println("Villager: Hello there, I have a present for you!");
        float number = world.randomizer().randomPresent();
        ILoot loot;
        if (number > 0.95) {
            loot = new Gem();
        } else if(number > 0.55) {
            loot = new Wood();
        } else {
            loot = new Rice();
        }

        try {
            player.addItems(loot.toString(),1);
            world.addEvent("GIFTED " + loot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public char getCharacterSymbol() {
        return 'V';
    }

    @Override
    public void setCurrentSpace(ASpace space) {
        this.currentSpace = space;
    }
}
