package raf.deeplearning.greed_island.model.characters;

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

    /**
     * How far the barbarian sees, in spaces: 1 is the 3x3 crop every NPC gets, 2 is 5x5 and so on.
     * Anything the player does inside it makes the barbarian chase.
     */
    private static int SightRadius = 2;


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

        huntOrFollowTrail(world, view);
    }

    /**
     * One move of the barbarian. While the player is inside its sight it chases the player (and
     * robs it once they are next to each other). Out of sight it does not know where the player
     * is, only where the player was GameMap.TRAIL_DELAY moves ago, and walks towards that space;
     * with nothing to follow - the player has not moved that often yet, the barbarian is already
     * standing on that space or the step is blocked - it wanders at random.
     */
    private void huntOrFollowTrail(GameMap world, ASpace[][] view) {
        view = viewOfSightRadius(world, view);

        int[] seen = findPlayer(view);
        if(seen != null) {
            if(Math.abs(seen[0]) + Math.abs(seen[1]) == 1) {
                interactWithPlayer(world, world.getThePlayer());
                return;
            }
            stepToward(world, seen[0], seen[1]);
            return;
        }

        Pair<Number, Number> trail = world.getDelayedPlayerPosition();
        if(trail != null && stepToward(world, trail.getX1() - x, trail.getX2() - y))
            return;

        wander(world);
    }

    /** The view handed in, or a wider one when it is smaller than this barbarian's sight. */
    private ASpace[][] viewOfSightRadius(GameMap world, ASpace[][] view) {
        if(view.length >= 2 * SightRadius + 1)
            return view;
        try {
            return world.lookupForCharacter(this, SightRadius);
        } catch (Exception e) {
            return view;
        }
    }

    /**
     * Where the player is relative to this barbarian, as {dx, dy}, or null when it is not in view.
     */
    private static int[] findPlayer(ASpace[][] view) {
        int centre = view.length / 2;
        for(int i = 0; i < view.length; i++) {
            for(int j = 0; j < view[i].length; j++) {
                if(i == centre && j == centre)
                    continue;
                if(view[i][j].getOccupyingCharacter() instanceof Player)
                    return new int[]{i - centre, j - centre};
            }
        }
        return null;
    }

    /**
     * One step closer to a space dx, dy away: along the axis with the larger distance first (the
     * vertical one on a tie) and along the other when that way is water, a mountain or occupied.
     *
     * @return true when the barbarian moved
     */
    private boolean stepToward(GameMap world, int dx, int dy) {
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        boolean xFirst = Math.abs(dx) >= Math.abs(dy);

        for(int attempt = 0; attempt < 2; attempt++) {
            boolean alongX = (attempt == 0) == xFirst;
            if(alongX && sx != 0 && world.moveCharacter(this, x + sx, y))
                return true;
            if(!alongX && sy != 0 && world.moveCharacter(this, x, y + sy))
                return true;
        }
        return false;
    }

    @Override
    public Pair<Number, Number> getCoordinates() {
        return new Pair<Number, Number>(x,y);
    }

    @Override
    public void setCoordinates(Pair<Number, Number> p) {
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
