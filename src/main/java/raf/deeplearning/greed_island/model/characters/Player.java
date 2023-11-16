package raf.deeplearning.greed_island.model.characters;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.exception.NonexistingItem;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Gate;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Getter
@Setter
public class Player implements ICharacter{

    private int x,y;
    private ASpace currentSpace;

    private Map<String,Integer> bagOfLoot;
    private BlockingDeque<PlayerActions> bufferedActions;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;

        bagOfLoot = new HashMap<>();
        bufferedActions = new LinkedBlockingDeque<>();
    }

    @Override
    public void interactWithWorld(ASpace[][] view) {
        System.out.println("Player is interacting with the world");
        while (this.bufferedActions.isEmpty()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
//        System.out.println("Player is interacting with the world 2");

        PlayerActions action = this.bufferedActions.poll();
        System.out.println(action);
        switch (action) {
            case UP -> GameMap.getInstance().moveCharacter(this,x-1,y);
            case DOWN -> GameMap.getInstance().moveCharacter(this,x+1,y);
            case LEFT -> GameMap.getInstance().moveCharacter(this,x,y-1);
            case RIGHT -> GameMap.getInstance().moveCharacter(this,x,y+1);
            case WAIT -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Player is on space:  "+this.getCurrentSpace().getClass().getSimpleName());
        if (this.getCurrentSpace() instanceof Gate) {
            GameMap.getInstance().endGame();
        }
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

    @Override
    public void setCurrentSpace(ASpace space) {
        this.currentSpace = space;
    }
}
