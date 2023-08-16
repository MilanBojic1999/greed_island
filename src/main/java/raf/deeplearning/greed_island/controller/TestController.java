package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.GameMapSerializer;

import java.util.Arrays;

@RestController
@CrossOrigin
public class TestController {

    private final GameMap gameMap;

    public TestController() {
//        gameMap = GameMap.getInstance();
//        System.out.println(Arrays.deepToString(gameMap.getSpaces()));
//        System.out.println(gameMap.toJson());
        String path = "/home/milan/IdeaProjects/greed_island/src/main/resources/static/map.txt";
        path = "C:\\Users\\mboji\\Desktop\\greed_island\\src\\main\\resources\\static\\map.txt";
//        GameMapSerializer.toSimpleMap(gameMap,path);
        gameMap = GameMapSerializer.fromSimpleMap(path);
    }

    @GetMapping("/")
    public String getHello() {
        return gameMap.getMapString();
    }

    @GetMapping("/full")
    public String getFullHello() {
        return Arrays.toString(gameMap.getSpaces());
    }

}
