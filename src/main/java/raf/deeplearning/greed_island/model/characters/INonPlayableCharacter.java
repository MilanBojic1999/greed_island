package raf.deeplearning.greed_island.model.characters;

import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;

public interface INonPlayableCharacter extends ICharacter{

    /**
     * What this character does to the player when the two meet, on the given map.
     */
    void interactWithPlayer(GameMap world, Player player);

    /**
     * One move of a character that goes after the player: it acts on the player when it is
     * standing right next to it, walks closer when it sees it in a corner of its view, and
     * wanders off at random when the player is nowhere in sight.
     * <p>
     * The view is the 3x3 crop around this character, so the player can only ever be one of the
     * eight cells around the centre. The four straight neighbours are within reach and mean an
     * interaction; the four corners are one step away on both axes, and closing either of them
     * puts the character next to the player on the following round. The vertical step is tried
     * first and the horizontal one is the fallback, so a corner chase still makes progress when
     * the way it would rather go is water, a mountain or another character - which is exactly
     * where the chase used to stall.
     */
    default void chaseOrWander(GameMap world, ASpace[][] view) {
        Pair here = getCoordinates();
        int x = here.getX1(), y = here.getX2();

        for(int i = 0; i < view.length; i++) {
            for(int j = 0; j < view[i].length; j++) {
                if(i == 1 && j == 1)
                    continue;
                if(!(view[i][j].getOccupyingCharacter() instanceof Player))
                    continue;

                int dx = i - 1, dy = j - 1;
                if(dx == 0 || dy == 0) {
                    interactWithPlayer(world, world.getThePlayer());
                    return;
                }

                if(!world.moveCharacter(this, x + dx, y))
                    world.moveCharacter(this, x, y + dy);
                return;
            }
        }

        wander(world);
    }

    /**
     * A step in a random direction, taken from the randomness of this world so that the same seed
     * always sends the character the same way.
     */
    default void wander(GameMap world) {
        Pair here = getCoordinates();
        int x = here.getX1(), y = here.getX2();

        switch (world.randomizer().randomMove()) {
            case 0 -> world.moveCharacter(this,x-1,y);
            case 1 -> world.moveCharacter(this,x+1,y);
            case 2 -> world.moveCharacter(this,x,y-1);
            case 3 -> world.moveCharacter(this,x,y+1);
        }
    }
}
