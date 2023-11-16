package raf.deeplearning.greed_island.services;

import org.springframework.stereotype.Service;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.GameMapSerializer;

@Service
public class WorldService {

    private GameMap gameMap;

    public WorldService() {
//        gameMap = GameMap.getInstance();
//        System.out.println(Arrays.deepToString(gameMap.getSpaces()));
//        System.out.println(Arrays.deepToString(gameMap.getSpaces()));
//        System.out.println(gameMap.toJson());
        String path = "/home/milan/IdeaProjects/greed_island/src/main/resources/static/map.txt";
        String json_path = "/home/milan/IdeaProjects/greed_island/src/main/resources/static/map_test.json";

        path = "C:\\Users\\mboji\\Desktop\\greed_island\\src\\main\\resources\\static\\map_test_alpha_2.txt";
        json_path = "C:\\Users\\mboji\\Desktop\\greed_island\\src\\main\\resources\\static\\map_test_alpha_2.json";
        try {
            gameMap = GameMapSerializer.fromSimpleMap(path);
//            gameMap = GameMapSerializer.fromJsonMap(json_path);
//            GameMapSerializer.toSimpleMap(gameMap,path);

//            GameMapSerializer.toJsonMap(gameMap,json_path);
        } catch (Exception e) {
            gameMap = GameMap.getInstance();
            e.printStackTrace();
        }
        GameMap.tryToSetCurrentMap(gameMap);
        System.out.println("GAME MAP: "+gameMap);
        Thread t1 = new Thread(gameMap);
        t1.start();
    }
}
