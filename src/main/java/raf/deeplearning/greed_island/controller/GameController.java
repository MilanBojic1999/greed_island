package raf.deeplearning.greed_island.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.dto.StepResponse;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.characters.Player;
import raf.deeplearning.greed_island.model.characters.PlayerActions;
import raf.deeplearning.greed_island.services.WorldService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The turn based half of the API: one request is one full round, and the answer holds everything
 * the agent needs to learn from it. No polling and no buffered actions - when a step returns, the
 * player has moved and every NPC has moved too.
 */
@RestController
@CrossOrigin
@RequestMapping(Paths.GAME_PATH)
public class GameController {

    private WorldService worldService;

    @Autowired
    public GameController(WorldService worldService) {
        this.worldService = worldService;
    }


    /**
     * Applies the player action, runs the NPCs and returns the state the round ended in.
     * The action is accepted under the name "action" or, as before, "step" - UP, DOWN, LEFT,
     * RIGHT or WAIT, in any letter case. Stepping a finished episode changes nothing and just
     * returns the final state, so a restart is needed to keep playing.
     */
    @PostMapping("/step")
    public StepResponse makeFullRound(@RequestParam(required = true, name = "action") String action) {

        PlayerActions playerAction = parseAction(action);
        GameMap map = GameMap.getInstance();

        // the background round loop would otherwise keep eating actions and moving the NPCs
        map.enterTurnBasedMode();

        if (!map.isDone()) {
            try {
                map.makeARound(playerAction);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return snapshot(map);
    }

    /**
     * The same observation as a step, without playing a round. Useful as the first state of an
     * episode and for the frontend, which only renders what the agent has already done.
     */
    @GetMapping("/state")
    public StepResponse getState() {
        return snapshot(GameMap.getInstance());
    }

    private StepResponse snapshot(GameMap map) {
        Player player = map.getThePlayer();

        return new StepResponse(
                map.getMatrix(),
                inventoryOf(player),
                player.getCurrentAmountOfGold(),
                map.getRound(),
                map.isDone(),
                map.isWon(),
                List.copyOf(map.getEvents()),
                map.getGatePrice(),
                map.getMerchantGold(),
                map.getGameSeed(),
                map.getMaxRounds()
        );
    }

    /**
     * The bag of loot without the emptied stacks the merchant and the barbarian leave behind, so
     * the observation does not grow with keys that are always zero.
     */
    private Map<String, Integer> inventoryOf(Player player) {
        Map<String, Integer> inventory = new TreeMap<>();
        for (Map.Entry<String, Integer> item : player.getBagOfLoot().entrySet()) {
            if (item.getValue() != null && item.getValue() > 0)
                inventory.put(item.getKey(), item.getValue());
        }
        return inventory;
    }

    private PlayerActions parseAction(String action) {
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing action, expected one of " + Arrays.toString(PlayerActions.values()));
        }

        try {
            return PlayerActions.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown action '" + action + "', expected one of " + Arrays.toString(PlayerActions.values()));
        }
    }
}
