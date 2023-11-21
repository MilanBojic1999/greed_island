package raf.deeplearning.greed_island.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.Paths;

@RestController
@CrossOrigin
@RequestMapping(Paths.MAP_PATH)
public class WorldController {

    @PostMapping("/reset")
    public void resetMap() {
        System.out.println("RESET MAP");

    }
}
