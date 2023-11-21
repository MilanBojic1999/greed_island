package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.services.WorldService;

@RestController
@CrossOrigin
@RequestMapping(Paths.MAP_PATH)
public class WorldController {


    private WorldService worldService;

    @Autowired
    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }

    @PostMapping("/reset")
    public void resetMap() {
        System.out.println("RESET MAP");
        GameMap.getInstance().setRunning(false);
        worldService.rerunGame();

    }
}
