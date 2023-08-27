package raf.deeplearning.greed_island.model.characters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.utils.Pair;
import raf.deeplearning.greed_island.model.utils.Randomizer;

@Getter
@Setter
@AllArgsConstructor
public class Villager implements INonPlayableCharacter{

    private int x,y;

    @Override
    public void interactWithWorld(ASpace[][] view) {
        if(view[0][0].getOccupyingCharacter() instanceof Player) {
            GameMap.getInstance().moveCharacter(this,x-1,y);
        } else if(view[0][1].getOccupyingCharacter() instanceof Player) {
            interactWithPlayer(GameMap.getInstance().getThePlayer());
        } else if(view[0][2].getOccupyingCharacter() instanceof Player) {
            GameMap.getInstance().moveCharacter(this,x+1,y);
        } else if(view[1][0].getOccupyingCharacter() instanceof Player) {
            interactWithPlayer(GameMap.getInstance().getThePlayer());
        } else if(view[1][2].getOccupyingCharacter() instanceof Player) {
            interactWithPlayer(GameMap.getInstance().getThePlayer());
        } else if(view[2][0].getOccupyingCharacter() instanceof Player) {
            GameMap.getInstance().moveCharacter(this,x-1,y);
        } else if(view[2][1].getOccupyingCharacter() instanceof Player) {
            interactWithPlayer(GameMap.getInstance().getThePlayer());
        } else if(view[2][2].getOccupyingCharacter() instanceof Player) {
            GameMap.getInstance().moveCharacter(this,x+1,y);
        } else {
            int move = Randomizer.getInstance().randomMove();
            switch (move) {
                case 0 -> GameMap.getInstance().moveCharacter(this,x-1,y);
                case 1 -> GameMap.getInstance().moveCharacter(this,x+1,y);
                case 2 -> GameMap.getInstance().moveCharacter(this,x,y-1);
                case 3 -> GameMap.getInstance().moveCharacter(this,x,y+1);
            }
        }
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
    public void interactWithPlayer(Player player) {

    }

    @Override
    public char getCharacterSymbol() {
        return 'V';
    }
}
