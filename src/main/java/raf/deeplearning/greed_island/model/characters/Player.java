package raf.deeplearning.greed_island.model.characters;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.exception.NonexistingItem;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Player implements ICharacter{

    private int x,y;

    private Map<String,Integer> bagOfLoot;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;

        bagOfLoot = new HashMap<>();
    }

    @Override
    public void interactWithWorld(ASpace[][] view) {

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



    public void removeItems(String item, int number) throws Exception {
        if(!this.bagOfLoot.containsKey(item)) {
            throw  new NonexistingItem(item);
        }

        int contains = this.bagOfLoot.get(item);
        number = Math.min(contains,number);
        contains -= number;
        this.bagOfLoot.put(item,contains);
    }

    public void addItems(String item, int number) throws Exception {
//        if(!this.bagOfLoot.containsKey(item)) {
//            throw  new NonexistingItem(item);
//        }

        int contains = 0;
        if (this.bagOfLoot.containsKey(item)) {
            contains = this.bagOfLoot.get(item);
        }


        this.bagOfLoot.put(item,contains+number);
    }

    @Override
    public char getCharacterSymbol() {
        return 'P';
    }
}
