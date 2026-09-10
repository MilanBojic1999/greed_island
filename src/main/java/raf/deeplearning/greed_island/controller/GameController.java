package raf.deeplearning.greed_island.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.dto.SessionView;
import raf.deeplearning.greed_island.dto.StepResponse;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.characters.Player;
import raf.deeplearning.greed_island.model.characters.PlayerActions;
import raf.deeplearning.greed_island.services.GameSession;
import raf.deeplearning.greed_island.services.WorldService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The turn based half of the API: one request is one full round, and the answer holds everything
 * the agent needs to learn from it. No polling and no buffered actions - when a step returns, the
 * player has moved and every NPC has moved too.
 * <p>
 * Every endpoint here works on one session. Which one is said either in the path
 * (/game/env_3/step) or in a "game_id" parameter (/game/step?game_id=env_3); saying nothing means
 * the default session. Sessions are started by PUT /map/restart?game_id=... and are independent of
 * each other, so a training run can step eight of them at the same time.
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
     * The action is UP, DOWN, LEFT, RIGHT or WAIT, in any letter case. Stepping a finished episode
     * changes nothing and just returns the final state, so a restart is needed to keep playing.
     * <p>
     * Two steps on the same session are played one after the other, never at the same time; steps
     * on different sessions do not wait for each other at all.
     */
    @PostMapping({"/step", "/{gameId}/step"})
    public StepResponse makeFullRound(@PathVariable(required = false, name = "gameId") String gameIdInPath,
                                      @RequestParam(required = false, name = "game_id") String gameIdInQuery,
                                      @RequestParam(required = true, name = "action") String action) {

        PlayerActions playerAction = parseAction(action);
        GameSession session = worldService.session(pickGameId(gameIdInPath, gameIdInQuery));

        return session.play(map -> {
            // the background round loop of the default session would otherwise keep eating actions
            // and moving the NPCs behind the caller's back
            map.enterTurnBasedMode();

            if (!map.isDone())
                map.makeARound(playerAction);

            return snapshot(session, map);
        });
    }

    /**
     * The same observation as a step, without playing a round. Useful as the first state of an
     * episode and for the frontend, which only renders what the agent has already done.
     */
    @GetMapping({"/state", "/{gameId}/state"})
    public StepResponse getState(@PathVariable(required = false, name = "gameId") String gameIdInPath,
                                 @RequestParam(required = false, name = "game_id") String gameIdInQuery) {
        GameSession session = worldService.session(pickGameId(gameIdInPath, gameIdInQuery));
        return session.play(map -> snapshot(session, map));
    }

    /**
     * Every session the server is keeping, to see what a training run has left behind.
     */
    @GetMapping("/sessions")
    public List<SessionView> getSessions() {
        List<SessionView> views = new ArrayList<>();

        for (GameSession session : worldService.allSessions()) {
            GameMap map = session.getMap();
            views.add(new SessionView(
                    session.getId(),
                    session.getMapNumber(),
                    map.getGameSeed(),
                    map.getRound(),
                    map.isDone(),
                    map.isWon(),
                    map.isTurnBased(),
                    session.idleMillis() / 1000
            ));
        }

        return views;
    }

    /**
     * Closes a session and frees its world. Not needed for correctness - a session nobody touches
     * for half an hour is dropped anyway - but an environment that is done with is better closed
     * than left to time out, and there is a limit on how many can be open at once.
     */
    @DeleteMapping("/{gameId}")
    public void closeSession(@PathVariable(name = "gameId") String gameId) {
        worldService.close(gameId);
    }

    private StepResponse snapshot(GameSession session, GameMap map) {
        Player player = map.getThePlayer();

        return new StepResponse(
                session.getId(),
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
     * A copy of the bag of loot, sorted by item name so the observation always comes in the same
     * order. Only what the player really carries is in it: a stack that runs out leaves the bag
     * itself, so there is nothing to filter out here.
     */
    private Map<String, Integer> inventoryOf(Player player) {
        return new TreeMap<>(player.getBagOfLoot());
    }

    /**
     * The session an endpoint that accepts both spellings is about: the path wins over the
     * parameter, because /game/env_3/step is the more specific thing to have written.
     */
    static String pickGameId(String gameIdInPath, String gameIdInQuery) {
        if (gameIdInPath != null && !gameIdInPath.isBlank())
            return gameIdInPath;
        return gameIdInQuery;
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
