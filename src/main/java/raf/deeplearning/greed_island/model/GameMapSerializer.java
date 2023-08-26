package raf.deeplearning.greed_island.model;

import raf.deeplearning.greed_island.model.characters.*;
import raf.deeplearning.greed_island.model.exception.UnknownCharacter;
import raf.deeplearning.greed_island.model.exception.UnknownSymbol;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.factory.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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

    private static ASpace returnSpaceFromChar(char input, int i, int j) throws Exception{
        switch (input) {
            case '>' -> {
                return new ElevationFactory().crateSpace(i, j, 0);
            }
            case '$' -> {
                return new MountainFactory().crateSpace(i, j, 0);
            }
            case '_' -> {
                return new PastureFactory().crateSpace(i, j, 0);
            }
            case '-' -> {
                return new WaterFactory().crateSpace(i, j, 0);
            }
            case '+' -> {
                return new WoodFactory().crateSpace(i, j, 0);
            }
            case '|' -> {
                return new GateFactory().crateSpace(i, j, 0);
            }
            default -> {
                throw new UnknownSymbol(input);
            }
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
            Player player = null;
            List<ICharacter> characters = new ArrayList<>();

            int j = 0;
            char prev_char = '-';
            while(line != null) {
                char[] charArray = line.toCharArray();
                for(int i=0;i<width;i++) {
                    System.out.print(charArray);
                    switch (charArray[i]) {
                        case 'P' -> {
                            player = new Player(i,j);
                            characters.add(player);
                            spaces[i][j] = returnSpaceFromChar(prev_char,i,j);
                        }
                        case 'M' -> {
                            characters.add(new Merchant(i, j, 50));
                            spaces[i][j] = returnSpaceFromChar(prev_char, i, j);
                        }
                        case 'V' -> {
                            characters.add(new Barbarian(i,j));
                            spaces[i][j] = returnSpaceFromChar(prev_char,i,j);
                        }
                        case 'B' -> {
                            characters.add(new Villager(i,j));
                            spaces[i][j] = returnSpaceFromChar(prev_char,i,j);
                        }
                        default -> {
                            spaces[i][j] = returnSpaceFromChar(charArray[i], i, j);
                            prev_char = charArray[i];
                        }

                    }
                }
                line = bw.readLine();
                j++;
            }

            GameMap gameMap = new GameMap(width,height);
            gameMap.setSpaces(spaces);
            gameMap.setThePlayer(player);
            gameMap.setCharacters(characters);

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
