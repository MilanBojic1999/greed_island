package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.GameMapSerializer;

import java.io.IOException;
import java.util.Arrays;

@RestController
@CrossOrigin
public class TestController {

    private GameMap gameMap;

    public TestController() {
//        gameMap = GameMap.getInstance();
//        System.out.println(Arrays.deepToString(gameMap.getSpaces()));
//        System.out.println(Arrays.deepToString(gameMap.getSpaces()));
//        System.out.println(gameMap.toJson());
        String path = "/home/milan/IdeaProjects/greed_island/src/main/resources/static/map.txt";
        String json_path = "/home/milan/IdeaProjects/greed_island/src/main/resources/static/map_test.json";

        path = "C:\\Users\\mboji\\Desktop\\greed_island\\src\\main\\resources\\static\\map_test_alpha.txt";
        json_path = "C:\\Users\\mboji\\Desktop\\greed_island\\src\\main\\resources\\static\\map_test_alpha.json";
        try {
            gameMap = GameMapSerializer.fromSimpleMap(path);
//            gameMap = GameMapSerializer.fromJsonMap(json_path);
//            GameMapSerializer.toSimpleMap(gameMap,path);

            GameMapSerializer.toJsonMap(gameMap,json_path);
        } catch (Exception e) {
            gameMap = GameMap.getInstance();
            e.printStackTrace();
        }

        Thread t1 = new Thread(gameMap);
        t1.start();
    }

    @GetMapping("/")
    public String getHello() {
        System.out.println("CALL");
        return gameMap.getMapString();
    }

    @GetMapping("/full")
    public String getFullHello() {
        return Arrays.toString(gameMap.getSpaces());
    }

}
