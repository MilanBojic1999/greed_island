package raf.deeplearning.greed_island.model.characters;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.exception.NonexistingItem;
import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Gate;
import raf.deeplearning.greed_island.model.utils.Pair;

import java.util.Map;
import java.util.TreeMap;
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

        /*
            Sorted and not hashed, so that the bag is always read in the same order: what the
            barbarian picks to steal and what the merchant gets offered first are decided by
            walking this map, and with a HashMap that order is an implementation detail.
         */
        this.bagOfLoot = new TreeMap<>();
        this.currentAmountOfGold = 0;
        this.bufferedActions = new LinkedBlockingDeque<>();
        System.out.println("Player is on space:  "+this.getCurrentSpace());
    }

    public Player() {
        System.out.println("Empty constructor called");
        throw new RuntimeException("Empty constructor called but shouldn't be called");
    }

    @Override
    public void interactWithWorld(GameMap world, ASpace[][] view) {
        PlayerActions action = this.bufferedActions.poll();
        if (action == null) {
            action = PlayerActions.WAIT;
        }
        performAction(world, action);
    }

    /**
     * Waits for an action to be buffered by the /player endpoints, for the world this player is
     * playing in. Returns null when that game is over or its round loop was handed over to the
     * turn based mode, so the caller can stop.
     */
    public PlayerActions awaitBufferedAction(GameMap world) {
        while (true) {
            PlayerActions action = this.bufferedActions.poll();
            if (action != null) {
                return action;
            }

            if (!world.isRunning() || world.isTurnBased()) {
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
    public void performAction(GameMap world, PlayerActions action) {
        System.out.println(action);
        switch (action) {
            case UP -> world.movePlayer(this,x-1,y);
            case DOWN -> world.movePlayer(this,x+1,y);
            case LEFT -> world.movePlayer(this,x,y-1);
            case RIGHT -> world.movePlayer(this,x,y+1);
            case WAIT -> {
            }
        }
        if (this.getCurrentSpace() == null) {
            return;
        }

        if (this.getCurrentSpace() instanceof Gate gate) {
            if(gate.getPriceOfEntrance() <= this.currentAmountOfGold) {
                world.addEvent("GATE_OPENED");
                world.endGame(true);
            }
            else {
                System.out.println("Player doesn't have enough gold to enter the gate");
                world.addEvent("GATE_REFUSED " + gate.getPriceOfEntrance());
            }
            return;
        }

        ILoot loot = this.getCurrentSpace().loot(world.randomizer());
        if(loot != null) {
            try {
                addItems(loot.getClass().getSimpleName(),1);
                world.addEvent("LOOTED " + loot.getClass().getSimpleName());
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



    /**
     * Takes items out of the bag, at most as many as are in it.
     * <p>
     * A stack that runs out leaves the bag instead of staying behind as a zero. That matters for
     * more than tidiness: the bag is the observation an agent learns from, and keys that are
     * always zero make it grow with every item the player has ever held and then lost. The
     * merchant and the barbarian walk the same map, so an emptied stack would also keep being
     * offered for sale and keep being picked as something to steal.
     */
    public void removeItems(String item, int number) throws Exception {
        if(!this.bagOfLoot.containsKey(item)) {
            throw  new NonexistingItem(item);
        }

        int contains = this.bagOfLoot.get(item);
        number = Math.min(contains,number);
        contains -= number;

        if(contains <= 0)
            this.bagOfLoot.remove(item);
        else
            this.bagOfLoot.put(item,contains);
    }

    /**
     * Puts items into the bag. Adding nothing is not the same as owning nothing: an item the
     * player does not have stays out of the bag rather than appearing in it as a zero.
     */
    public void addItems(String item, int number) throws Exception {
        int contains = 0;
        if (this.bagOfLoot.containsKey(item)) {
            contains = this.bagOfLoot.get(item);
        }

        if(contains + number <= 0) {
            this.bagOfLoot.remove(item);
            return;
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
