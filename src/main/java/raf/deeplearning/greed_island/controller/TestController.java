package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.services.WorldService;

import java.util.Arrays;

@RestController
@CrossOrigin
public class TestController {

    @Autowired
    public TestController() {
        System.out.println("TEST CONTROLLER");
    }

    @GetMapping("/")
    public String getHello() {
        System.out.println("CALL");
        return GameMap.getInstance().getMapString();
    }

    @GetMapping("/full")
    public String getFullHello() {
        return Arrays.toString(GameMap.getInstance().getSpaces());
    }

}
