package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.characters.PlayerActions;
import raf.deeplearning.greed_island.services.WorldService;

@RestController
@CrossOrigin
@RequestMapping(Paths.PLAYER_PATH)
public class PlayerController {


    @Autowired
    public PlayerController(WorldService worldService) {
        System.out.println("PLAYER CONTROLLER");
    }

    @PutMapping("/up")
    public void upAction(){
        System.out.println("UP action");
        System.out.println(GameMap.getInstance().getThePlayer()+"  WWWW  "+GameMap.getInstance());
        GameMap.getInstance().getThePlayer().getBufferedActions().add(PlayerActions.UP);
    }

    @PutMapping("/down")
    public void downAction(){
        GameMap.getInstance().getThePlayer().getBufferedActions().add(PlayerActions.DOWN);
    }

    @PutMapping("/left")
    public void leftAction(){
        GameMap.getInstance().getThePlayer().getBufferedActions().add(PlayerActions.LEFT);
    }

    @PutMapping("/right")
    public void rightAction(){
        GameMap.getInstance().getThePlayer().getBufferedActions().add(PlayerActions.RIGHT);
    }

    @PutMapping("/wait")
    public void waitAction(){
        GameMap.getInstance().getThePlayer().getBufferedActions().add(PlayerActions.WAIT);
    }
}
