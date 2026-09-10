package raf.deeplearning.greed_island.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.Paths;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.characters.PlayerActions;
import raf.deeplearning.greed_island.model.loot.ILoot;
import raf.deeplearning.greed_island.services.GameSession;
import raf.deeplearning.greed_island.services.WorldService;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything about the player of one session. Which session is said in the optional "game_id"
 * parameter; without it the default session is meant, which is what the frontend and the old
 * Postman collection do.
 */
@RestController
@CrossOrigin
@RequestMapping(Paths.PLAYER_PATH)
public class PlayerController {

    /*
        The local view is a square around the player, so a radius of 2 is the 5x5 crop of the
        example in the docs. The upper bound is there only to keep a typo in a query parameter from
        asking for a matrix of millions of cells.
     */
    private static final int DEFAULT_VIEW_RADIUS = 2;
    private static final int MAX_VIEW_RADIUS = 64;

    private WorldService worldService;

    @Autowired
    public PlayerController(WorldService worldService) {
        System.out.println("PLAYER CONTROLLER");
        this.worldService = worldService;
    }

    @PutMapping("/up")
    public void upAction(@RequestParam(required = false, name = "game_id") String gameId) {
        bufferAction(gameId, PlayerActions.UP);
    }

    @PutMapping("/down")
    public void downAction(@RequestParam(required = false, name = "game_id") String gameId) {
        bufferAction(gameId, PlayerActions.DOWN);
    }

    @PutMapping("/left")
    public void leftAction(@RequestParam(required = false, name = "game_id") String gameId) {
        bufferAction(gameId, PlayerActions.LEFT);
    }

    @PutMapping("/right")
    public void rightAction(@RequestParam(required = false, name = "game_id") String gameId) {
        bufferAction(gameId, PlayerActions.RIGHT);
    }

    @PutMapping("/wait")
    public void waitAction(@RequestParam(required = false, name = "game_id") String gameId) {
        bufferAction(gameId, PlayerActions.WAIT);
    }

    /**
     * Hands an action to the round loop of a session, the asynchronous way the game was played
     * before /game/step existed.
     * <p>
     * A turn based session has no round loop to pick the action up, so asking it to buffer one is
     * refused instead of silently doing nothing: the action would sit in the queue forever and the
     * agent would be left waiting for a move that is never made.
     */
    private void bufferAction(String gameId, PlayerActions action) {
        GameSession session = worldService.session(gameId);

        session.play(map -> {
            if (map.isTurnBased()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Session '" + session.getId() + "' is turn based - play it with"
                                + " POST /game/" + session.getId() + "/step?action=" + action
                                + " instead of buffering actions.");
            }

            map.getThePlayer().getBufferedActions().add(action);
            return null;
        });
    }

    /**
     * What the player sees from where it stands: a (2*radius+1) x (2*radius+1) matrix of symbols
     * centered on it, cells outside the island filled with water ('-'). The symbols and the shape
     * of the answer are the ones of /map/full/matrix, so an agent can be fed the local view
     * instead of the whole map without changing how it reads an observation.
     * <p>
     * Reading a view does not play a round, so in the turn based mode of /game/step the view
     * belongs to the state the last step returned.
     *
     * @param radius how far around the player to look, 2 when not given, 0 for the player's cell
     * @param gameId the session to look in, the default one when not given
     */
    @GetMapping("/view")
    public String[][] getLocalView(@RequestParam(required = false, name = "radius") Integer radius,
                                   @RequestParam(required = false, name = "game_id") String gameId) {
        int viewRadius = radius == null ? DEFAULT_VIEW_RADIUS : radius;

        if(viewRadius < 0 || viewRadius > MAX_VIEW_RADIUS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "radius must be between 0 and " + MAX_VIEW_RADIUS + ", got " + viewRadius);
        }

        return worldService.session(gameId).play(map -> map.getViewMatrix(viewRadius));
    }

    /*
        The bag is copied while the lock of the session is held, and the copy is what leaves this
        class: the live one belongs to a world that may be playing its next round already, and
        reading it while it changes is how a ConcurrentModificationException happens.
     */
    @GetMapping("/inventory")
    public Map<String, Integer> getLoot(@RequestParam(required = false, name = "game_id") String gameId) {
        return worldService.session(gameId).play(map -> new TreeMap<>(map.getThePlayer().getBagOfLoot()));
    }

    @GetMapping("/inventory/value")
    public Integer getLootValue(@RequestParam(required = false, name = "game_id") String gameId) {
        Map<String,Integer> map = worldService.session(gameId)
                .play(world -> new TreeMap<>(world.getThePlayer().getBagOfLoot()));
        int full_value = 0;
        for(String lootName:map.keySet()) {
            try {
                Class<? extends ILoot> loot = Class.forName("raf.deeplearning.greed_island.model.loot."+lootName).asSubclass(ILoot.class);
                full_value += loot.getConstructor().newInstance().getValueInGold() * map.get(lootName);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                     InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return full_value;
    }

    @GetMapping("/inventory/gold")
    public Integer getGold(@RequestParam(required = false, name = "game_id") String gameId) {
        return worldService.session(gameId).play(map -> map.getThePlayer().getCurrentAmountOfGold());
    }
}
