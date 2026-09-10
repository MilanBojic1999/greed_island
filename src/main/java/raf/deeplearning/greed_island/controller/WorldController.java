package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.services.WorldService;

import java.io.File;

@RestController
@CrossOrigin
@RequestMapping(Paths.MAP_PATH)
public class WorldController {


    private WorldService worldService;

    @Autowired
    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }

    @GetMapping("/full")
    public String getFullMap() {
        return GameMap.getInstance().getMapString();
    }

    @GetMapping("/full/matrix")
    public String[][] getFullMapMatrix() {
        return GameMap.getInstance().getMatrix();
    }

    @GetMapping("/isover")
    public boolean checkIfGameOver() {
        return GameMap.getInstance().isDone();
    }

    @PutMapping("/restart")
    public boolean restartGame(@RequestParam(required = false, name = "map_number") Integer mapNumber,
                               @RequestParam(required = false, name = "seed") Long seed) {
        if (mapNumber == null) {
            mapNumber = 1;
        }
        String map = "static"+ File.separator +"map_test_beta_"+mapNumber+".json";
        System.out.println(map);
        System.out.printf("SEED: %d ~~\n", seed);
        try {
            // loadGameMap seeds the episode and starts the game, calling rerunGame() on top of it
            // would reseed with another seed and leave a second round loop running on the same map
            worldService.loadGameMap(map, seed);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    @PostMapping("/reset")
    public void resetMap() {
        System.out.println("RESET MAP");
        GameMap.getInstance().setRunning(false);
        worldService.rerunGame();

    }
}
