package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.characters.ICharacter;
import raf.deeplearning.greed_island.model.characters.Player;
import raf.deeplearning.greed_island.model.spaces.ASpace;
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

    @GetMapping("/full/matrix")
    public String[][] getFullHelloMatrix() {
        ASpace[][] spaces = GameMap.getInstance().getSpaces();
        String[][] matrix = new String[spaces.length][spaces[0].length];

        for(int i = 0; i < spaces.length; i++) {
            for(int j = 0; j < spaces[i].length; j++) {
                matrix[i][j] = String.valueOf(spaces[i][j].getSpaceSymbol());
            }
        }

        for(ICharacter c:GameMap.getInstance().getCharacters()) {
            matrix[c.getCoordinates().getX1()][c.getCoordinates().getX2()] = String.valueOf(c.getCharacterSymbol());
        }

        Player p = GameMap.getInstance().getThePlayer();
        matrix[p.getCoordinates().getX1()][p.getCoordinates().getX2()] = String.valueOf(p.getCharacterSymbol());

//        System.err.println(matrix.length + " " + matrix[0].length);
        return matrix;
    }
}
