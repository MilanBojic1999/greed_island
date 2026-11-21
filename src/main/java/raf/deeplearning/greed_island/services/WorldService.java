package raf.deeplearning.greed_island.services;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import raf.deeplearning.greed_island.model.EpisodeConfig;
import raf.deeplearning.greed_island.model.GameMap;
import raf.deeplearning.greed_island.model.GameMapSerializer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Keeps the worlds the server is playing: one session per episode that is going on.
 * <p>
 * A session is addressed by the "game_id" of a request. No id at all means the default session, the
 * one the server starts on and the one the frontend renders, and that session still plays the old
 * asynchronous way - a thread of its own, actions buffered by the /player endpoints. Every other id
 * is a turn based session: it has no thread, one /game/step is one round, and that is what lets a
 * student run several environments in parallel without them interfering.
 * <p>
 * Sessions are cheap but not free, so unused ones do not pile up forever: a session that has not
 * been touched for {@link #SESSION_IDLE_TIMEOUT_MS} is dropped the next time anybody looks the
 * sessions up, and there can be at most {@link #MAX_SESSIONS} of them at a time.
 */
@Service
public class WorldService implements ApplicationRunner {

    /** The session every request that does not name one gets. */
    public static final String DEFAULT_SESSION_ID = "default";

    /** The level the server starts its default session on, as it always has. */
    private static final int BOOTSTRAP_MAP_NUMBER = 5;

    /** The level a restart loads when it does not say which one. */
    public static final int FALLBACK_MAP_NUMBER = 1;

    private static final int MAX_SESSIONS = 64;
    private static final long SESSION_IDLE_TIMEOUT_MS = 30 * 60 * 1000L;

    /*
        An id ends up in a log and in an URL path, so it is kept to the characters that are safe in
        both. Long enough for "ppo_run7_env3", short enough not to be a way to waste memory.
     */
    private static final Pattern VALID_SESSION_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public WorldService() {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++");
        bootstrapDefaultSession();
    }

    /**
     * Starts the session the server runs on before anybody asked for anything.
     * <p>
     * A map that cannot be read stops the server instead of being replaced by a generated one. The
     * generated map is not one of the levels: it is 10x10 of nothing but pasture, so an agent
     * trained against it learns a game nobody set. Coming up on it looks like a working server and
     * only shows much later, in results that make no sense, whereas a server that refuses to start
     * says what is wrong on the line that says it.
     */
    private void bootstrapDefaultSession() {
        GameMap map;
        try {
            map = loadMap(BOOTSTRAP_MAP_NUMBER);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the starting map " + BOOTSTRAP_MAP_NUMBER
                    + ". The levels are read from the classpath as static/map_test_beta_<n>.json -"
                    + " check that they are on it and that they parse.", e);
        }

        map.resetEpisode();

        GameSession session = new GameSession(DEFAULT_SESSION_ID, map, BOOTSTRAP_MAP_NUMBER);
        sessions.put(DEFAULT_SESSION_ID, session);
        session.startRoundLoop();
    }

    /**
     * Reads one of the levels and builds a world out of it. Every call gives a fresh map, so two
     * sessions on the same level share nothing at all.
     */
    public GameMap loadMap(int mapNumber) throws Exception {
        // Classpath resource names always use '/', whatever the OS separator is.
        String basePath = "static/map_test_beta_" + mapNumber;
        String jsonPathStr = basePath + ".json";
        String txtPathStr = basePath + ".txt";

        String targetPath;
        GameMap map;
        // The levels are classpath resources (inside the jar when it is run as one), not files on disk.
        if (new ClassPathResource(jsonPathStr).exists()) {
            targetPath = jsonPathStr;
            System.out.println("Loading JSON map from: " + jsonPathStr);
            map = GameMapSerializer.fromJsonMap(targetPath);

        } else {
            targetPath = txtPathStr;
            System.out.println("JSON not found. Falling back to TXT at: " + txtPathStr);
            map = GameMapSerializer.fromSimpleMap(targetPath);
        }

        if (map == null || map.getSpaces() == null || map.getThePlayer() == null)
            throw new RuntimeException("Map " + mapNumber + " could not be read from " + targetPath);

        return map;
    }

    /**
     * The map at a path, kept for the .txt maps and for anything that loads a map by name.
     */
    public GameMap loadMapFromPath(String path) throws Exception {
        if (path.endsWith(".txt")) {
            GameMap map = GameMapSerializer.fromSimpleMap(path);
            System.out.println("GAME MAP: " + Arrays.deepToString(map.getSpaces()));
            return map;
        } else if (path.endsWith(".json")) {
            return GameMapSerializer.fromJsonMap(path);
        }
        throw new RuntimeException("Invalid file format");
    }

    /**
     * The session a request is about. No id - or an empty one - is the default session; an id that
     * no session has is an error rather than a new session, because a typo in a training loop would
     * otherwise quietly start playing an episode that is not the one being trained.
     */
    public GameSession session(String gameId) {
        evictIdleSessions();

        String id = (gameId == null || gameId.isBlank()) ? DEFAULT_SESSION_ID : gameId.trim();

        GameSession session = sessions.get(id);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No game session '" + id + "'. Start one with PUT /map/restart?game_id=" + id);
        }

        session.touch();
        return session;
    }

    public GameSession defaultSession() {
        return session(DEFAULT_SESSION_ID);
    }

    /**
     * Starts an episode: in the session that is asked for, creating it when it is not there yet.
     * <p>
     * This is both "give me a new environment" and "reset the one I have" - an RL loop calls exactly
     * the same thing at the start of training and between two episodes. The settings of the level
     * come from the map itself with the overrides on top, and the seed makes the episode repeatable.
     */
    public GameSession restart(String gameId, Integer mapNumber, Long seed, EpisodeConfig overrides) throws Exception {
        evictIdleSessions();

        String id = (gameId == null || gameId.isBlank()) ? DEFAULT_SESSION_ID : gameId.trim();
        if (!DEFAULT_SESSION_ID.equals(id) && !VALID_SESSION_ID.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A game_id may only hold letters, digits, '_', '-', '.' and ':' (up to 64 of them), got '" + id + "'");
        }

        int level = mapNumber == null ? FALLBACK_MAP_NUMBER : mapNumber;

        GameSession session = sessions.get(id);
        if (session == null && sessions.size() >= MAX_SESSIONS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many game sessions open (" + MAX_SESSIONS + "). Close one with DELETE /game/{gameId}"
                            + " or wait for the idle ones to be dropped.");
        }

        GameMap map = loadMap(level);

        // the config has to be in place before the episode is reset: resetting is what applies it
        map.setConfig(EpisodeConfig.merge(map.getConfig(), overrides));
        if (seed != null)
            map.resetEpisode(seed);
        else
            map.resetEpisode();

        if (session == null) {
            session = new GameSession(id, map, level);
            sessions.put(id, session);
        } else {
            session.replaceMap(map, level);
        }

        if (DEFAULT_SESSION_ID.equals(id)) {
            // the default session keeps playing by itself, so the frontend and the buffered
            // /player actions work the way they always did
            session.startRoundLoop();
        } else {
            // every other session is driven by /game/step alone: no thread, nothing to race with
            map.enterTurnBasedMode();
        }

        System.out.println("SESSION " + id + " started on map " + level + " with seed " + map.getGameSeed());
        return session;
    }

    /**
     * Closes a session and stops whatever it was running. The default session cannot be closed -
     * the server would have nothing left to show - so it is restarted instead.
     */
    public void close(String gameId) {
        String id = (gameId == null || gameId.isBlank()) ? DEFAULT_SESSION_ID : gameId.trim();

        if (DEFAULT_SESSION_ID.equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The default session cannot be closed, restart it with PUT /map/restart instead");
        }

        GameSession session = sessions.remove(id);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No game session '" + id + "'");
        }

        session.stopRoundLoop();
    }

    /** Every session the server has, oldest id first, for a look at what is going on. */
    public List<GameSession> allSessions() {
        evictIdleSessions();

        List<GameSession> all = new ArrayList<>(sessions.values());
        all.sort(Comparator.comparing(GameSession::getId));
        return all;
    }

    /**
     * Drops the sessions nobody has touched for a while. A student whose training run dies halfway
     * through leaves its environments behind, and without this they would hold their maps until the
     * server is restarted.
     */
    private void evictIdleSessions() {
        for (GameSession session : sessions.values()) {
            if (DEFAULT_SESSION_ID.equals(session.getId()))
                continue;
            if (session.idleMillis() < SESSION_IDLE_TIMEOUT_MS)
                continue;

            if (sessions.remove(session.getId(), session)) {
                System.out.println("Dropping idle game session " + session.getId());
                session.stopRoundLoop();
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        GameSession session = sessions.get(DEFAULT_SESSION_ID);
        System.out.println("World service is running");
        System.out.println("Default session: " + session);
    }

    /**
     * Plays the default session over again on a new seed, for the legacy POST /map/reset.
     */
    public void rerunGame() throws Exception {
        GameSession session = defaultSession();
        this.restart(DEFAULT_SESSION_ID, session.getMapNumber(), null, null);
        synchronized (session) {
            session.stopRoundLoop();

            GameMap map = session.getMap();
            map.getThePlayer().getBufferedActions().clear();
            map.resetEpisode();

            session.startRoundLoop();
        }
    }
}
