package raf.deeplearning.greed_island.model;

import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Wood;
import raf.deeplearning.greed_island.model.spaces.factory.*;

import java.io.*;

public class GameMapSerializer {


    public static void toSimpleMap(GameMap map,String path) {

        try(BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write(map.getHeight() + " " + map.getWidth());
            bw.newLine();
            bw.write(map.getMapString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GameMap fromSimpleMap(String path) {


        try (BufferedReader bw = new BufferedReader(new FileReader(path))) {
            String sizeStr = bw.readLine();
            String[] sz = sizeStr.split(" ");
            int height = Integer.parseInt(sz[0]);
            int width = Integer.parseInt(sz[1]);
            ASpace[][] spaces = new ASpace[height][width];

            String line = bw.readLine();
            int j = 0;
            while(line != null) {
                char[] charArray = line.toCharArray();
                for(int i=0;i<width;i++) {
                    System.out.print(charArray);
                    switch (charArray[i]) {
                        case '>' -> spaces[j][i] = new ElevationFactory().crateSpace(i, j, 0);
                        case '$' -> spaces[j][i] = new MountainFactory().crateSpace(i, j, 0);
                        case '_' -> spaces[j][i] = new PastureFactory().crateSpace(i, j, 0);
                        case '-' -> spaces[j][i] = new WaterFactory().crateSpace(i, j, 0);
                        case '+' -> spaces[j][i] = new WoodFactory().crateSpace(i, j, 0);
                        case '|' -> spaces[j][i] = new GateFactory().crateSpace(i, j, 0);
                        default -> System.err.println("There was an error for space " + charArray[i] + " at spaces [" + i + "," + j + "] ");
                    }
                }
                line = bw.readLine();
                j++;
            }

            GameMap gameMap = new GameMap(width,height);
            gameMap.setSpaces(spaces);

            return gameMap;
        }catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static void toJsonMap(GameMap map, String path) {
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write(map.toJson());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
