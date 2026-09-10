package raf.deeplearning.greed_island.services;

import lombok.Getter;
import raf.deeplearning.greed_island.model.GameMap;

/**
 * One world somebody is playing in, with the bookkeeping the server needs around it.
 * <p>
 * A session is what makes several episodes possible at the same time: a student training eight
 * environments in parallel keeps eight of these, each with its own map, its own seed and its own
 * round counter, and nothing in one can be seen or disturbed from another.
 * <p>
 * Everything that touches the world goes through {@link #play(Work)}, which holds the lock of this
 * session for the whole round. Two requests for the same session therefore queue up instead of
 * moving the same player twice at once, while requests for different sessions never wait for each
 * other - that is where the parallelism comes from.
 */
@Getter
public class GameSession {

    /** What a caller asks for something to be done with the world of this session. */
    public interface Work<T> {
        T run(GameMap map) throws Exception;
    }

    private final String id;

    /*
        Both change when the session is restarted, and are read outside the lock by the session
        listing, so they are volatile rather than plain fields.
     */
    private volatile GameMap map;
    private volatile int mapNumber;

    private volatile long lastUsedAt = System.currentTimeMillis();

    /*
        The thread that drives the rounds by itself, for the sessions that play in the old
        asynchronous way (see WorldService: only the default session does). A turn based session
        has none - its rounds are made by the request thread of /game/step.
     */
    private volatile Thread roundLoop;

    public GameSession(String id, GameMap map, int mapNumber) {
        this.id = id;
        this.map = map;
        this.mapNumber = mapNumber;
    }

    /**
     * Does something with the world of this session, with nobody else touching it in the meantime.
     * Checked exceptions of the game logic are wrapped, which is what the callers did anyway.
     */
    public synchronized <T> T play(Work<T> work) {
        try {
            return work.run(this.map);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            touch();
        }
    }

    /**
     * Puts a new episode in this session, stopping whatever was running in the old one first, so a
     * restart cannot leave a round loop behind that keeps playing a map nobody can see anymore.
     */
    public synchronized void replaceMap(GameMap newMap, int newMapNumber) {
        stopRoundLoop();
        this.map = newMap;
        this.mapNumber = newMapNumber;
        touch();
    }

    /**
     * Starts the asynchronous round loop of this session: the game plays on its own and the actions
     * come in buffered through the /player endpoints. Only the default session uses this.
     */
    public synchronized void startRoundLoop() {
        stopRoundLoop();

        // stopping the old loop is done by taking its map off "running", so the map of the loop
        // that is about to start has to be put back on it - it may well be the very same map
        this.map.setRunning(true);

        Thread thread = new Thread(this.map, "game-" + this.id);
        this.roundLoop = thread;
        thread.start();
    }

    /**
     * Stops the round loop of this session and waits a moment for it to notice. The loop leaves as
     * soon as its map is not running anymore - see GameMap.run() and Player.awaitBufferedAction().
     */
    public synchronized void stopRoundLoop() {
        Thread thread = this.roundLoop;
        this.roundLoop = null;

        if(this.map != null)
            this.map.setRunning(false);

        if(thread == null || !thread.isAlive())
            return;

        thread.interrupt();
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Whether this session plays by itself, as opposed to one round per /game/step. */
    public boolean hasRoundLoop() {
        Thread thread = this.roundLoop;
        return thread != null && thread.isAlive();
    }

    public void touch() {
        this.lastUsedAt = System.currentTimeMillis();
    }

    public long idleMillis() {
        return System.currentTimeMillis() - this.lastUsedAt;
    }

    @Override
    public String toString() {
        return "GameSession{id=" + id + ", map=" + mapNumber + ", round=" + map.getRound() + '}';
    }
}
