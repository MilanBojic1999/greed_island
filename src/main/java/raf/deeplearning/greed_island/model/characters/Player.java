package raf.deeplearning.greed_island.model.characters;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.exception.NonexistingItem;
import raf.deeplearning.greed_island.model.loot.ILoot;
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
    private Integer currentAmountOfGold;
    private BlockingDeque<PlayerActions> bufferedActions;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;

        this.bagOfLoot = new HashMap<>();
        this.currentAmountOfGold = 0;
        this.bufferedActions = new LinkedBlockingDeque<>();
        System.out.println("Player is on space:  "+this.getCurrentSpace());
    }

    public Player() {
        System.out.println("Empty constructor called");
        throw new RuntimeException("Empty constructor called but shouldn't be called");
    }

    @Override
    public void interactWithWorld(ASpace[][] view) {
        PlayerActions action = this.bufferedActions.poll();
        if (action == null) {
            action = PlayerActions.WAIT;
        }
        performAction(action);
    }

    /**
     * Waits for an action to be buffered by the /player endpoints. Returns null when the game is
     * over or the round loop was handed over to the turn based mode, so the caller can stop.
     */
    public PlayerActions awaitBufferedAction() {
        while (true) {
            PlayerActions action = this.bufferedActions.poll();
            if (action != null) {
                return action;
            }

            GameMap map = GameMap.getInstance();
            if (!map.isRunning() || map.isTurnBased()) {
                return null;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * The player half of a round: move, then resolve whatever is on the space we ended up on.
     */
    public void performAction(PlayerActions action) {
        System.out.println(action);
        switch (action) {
            case UP -> GameMap.getInstance().movePlayer(this,x-1,y);
            case DOWN -> GameMap.getInstance().movePlayer(this,x+1,y);
            case LEFT -> GameMap.getInstance().movePlayer(this,x,y-1);
            case RIGHT -> GameMap.getInstance().movePlayer(this,x,y+1);
            case WAIT -> {
            }
        }
        if (this.getCurrentSpace() == null) {
            return;
        }

        if (this.getCurrentSpace() instanceof Gate gate) {
            if(gate.getPriceOfEntrance() <= this.currentAmountOfGold) {
                GameMap.getInstance().addEvent("GATE_OPENED");
                GameMap.getInstance().endGame(true);
            }
            else {
                System.out.println("Player doesn't have enough gold to enter the gate");
                GameMap.getInstance().addEvent("GATE_REFUSED " + gate.getPriceOfEntrance());
            }
            return;
        }

        ILoot loot = this.getCurrentSpace().loot();
        if(loot != null) {
            try {
                addItems(loot.getClass().getSimpleName(),1);
                GameMap.getInstance().addEvent("LOOTED " + loot.getClass().getSimpleName());
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public void addCoins(int coins) {
        if(this.currentAmountOfGold + coins < 0) {
            this.currentAmountOfGold = 0;
        }
        this.currentAmountOfGold += coins;
    }
}
