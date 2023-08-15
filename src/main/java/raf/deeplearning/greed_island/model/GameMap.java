package raf.deeplearning.greed_island.model;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.model.characters.ICharacter;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.factory.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Getter
@Setter
public class GameMap {

    private int width, height;
    private ASpace[][] spaces;
    private List<ICharacter> characters;

    private static GameMap currentGameMap;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.spaces = new ASpace[height][width];

        characters = new ArrayList<>();
        ISpaceFactory[] all_available_spaces = new ISpaceFactory[]{new ElevationFactory(),new MountainFactory(),new PastureFactory(),new WaterFactory(),new WoodFactory(),};
        Random r = new Random();
        for(int i=0;i<height;i++) {
            for(int j=0;j<width;j++) {
                this.spaces[i][j] = all_available_spaces[r.nextInt(5)].crateSpace(i,j,1);
            }
        }
    }

    public static GameMap getInstance() {
        if (currentGameMap == null) {
            Random r = new Random();
            currentGameMap = new GameMap(r.nextInt(20),r.nextInt(20));
        }

        return currentGameMap;
    }

    @Override
    public String toString() {
        return "Map{" +
                "spaces=" + spaces +
                ", characters=" + characters +
                '}';
    }

    public static GameMap createMap(int width, int height) {
        if(currentGameMap !=null) {
            System.err.println("Map already exists:"+ currentGameMap);
            return currentGameMap;
        }

        currentGameMap = new GameMap(width,height);
        return currentGameMap;
    }

    public String getMapString() {

        StringBuilder sb = new StringBuilder();
        for(ASpace[] arr:this.spaces) {
            sb.append(Arrays.stream(arr).map(ASpace::getSpaceSymbol)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString());
            sb.append("\n");
        }
        return sb.toString();

    }

    public String toJson() {
        Gson gs = new Gson();
        return gs.toJson(this);
    }
}
