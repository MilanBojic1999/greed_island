package raf.deeplearning.greed_island.model;

import lombok.Getter;
import lombok.Setter;
import raf.deeplearning.greed_island.dto.EpisodeConfigView;
import raf.deeplearning.greed_island.model.characters.*;
import raf.deeplearning.greed_island.model.exception.UnknownCharacter;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Elevation;
import raf.deeplearning.greed_island.model.spaces.Gate;
import raf.deeplearning.greed_island.model.spaces.Mountain;
import raf.deeplearning.greed_island.model.spaces.Water;
import raf.deeplearning.greed_island.model.spaces.factory.*;
import raf.deeplearning.greed_island.model.utils.Pair;
import raf.deeplearning.greed_island.model.utils.Randomizer;

import java.util.*;
import java.util.function.BiFunction;

@Getter
@Setter
public class GameMap implements Runnable{

    /*
        What a map that is generated rather than read from a file is worth playing with. The gate
        price is the one the documentation names; it used to be 105 here, so a generated map was
        quietly twice as hard as every level the students actually get.
     */
    private static final int GENERATED_GATE_PRICE = 50;
    private static final int GENERATED_MERCHANT_BUDGET = 50;

    /** How many times a spawn is drawn at random before the map is simply scanned for a spot. */
    private static final int FREE_SPACE_DRAWS = 100;

    private int width, height;
    private ASpace[][] spaces;
    private List<ICharacter> characters;
    //        System.out.println("Getting the player>>>> "+thePlayer+ " <<<< in world  "+this.hashCode());
    private Player thePlayer;

    /**
     * How many moves behind the player the barbarian's information is: it knows the space the
     * player stood on this many moves ago and nothing newer. A static constant on purpose - a map
     * read from JSON is allocated without running initializers, so an instance field would be 0.
     */
    public static final int TRAIL_DELAY = 4;

    /*
        The spaces the player has stood on, oldest first: the current one plus the TRAIL_DELAY
        before it, seeded with the starting space. Only moves that really happened are recorded.
        Transient and created on demand for the same reason as the randomizer.
     */
    private transient Deque<Pair<Number, Number>> playerMovementHistory;
    private volatile boolean running = true;

    /*
        The episode settings of this level, read from the optional "config" object of the map JSON.
        Not transient, so a map saved back to JSON keeps them. Like everything else that comes from
        Gson it can be null - getConfig() is the only safe way to reach it.
     */
    private EpisodeConfig config;

    /*
        Episode bookkeeping for the turn based mode. These fields are transient because a map is
        deserialized from JSON by Gson, which allocates the object without calling any constructor -
        the initializers below only run for maps built in code, so never rely on them being set and
        treat 0/false as the starting value.
     */
    private transient int round;
    private transient volatile boolean done;
    private transient volatile boolean won;
    private transient List<String> events;
    private transient long seed;
    private transient int maxRounds;

    /*
        Turn based mode: as long as it is off, the background thread of run() drives the rounds.
        The first call of /game/step switches it on, which makes that thread leave its loop so the
        rounds are made only by the request thread - see enterTurnBasedMode().
     */
    private transient volatile boolean turnBased;
    private transient volatile boolean loopRunning;

    private transient Water outboundWater;

    /*
        The randomness of this world. One per map and not one per process: that is what makes a
        seed mean something while several sessions are playing at the same time. Transient, because
        a map read from JSON is allocated without a constructor - randomizer() is the safe way in.
     */
    private transient Randomizer randomizer;

    /**
     * Builds a map on a seed nobody asked for: one is drawn and set anyway, so that even a map
     * created out of thin air can be replayed later from the seed the API reports.
     */
    public GameMap(int width, int height) {
        this(width,height,new Random().nextLong());
    }

    /**
     * Builds a map on a given seed. The seed is set before anything random is drawn, so the NPC
     * spawns below - and every loot draw and NPC move afterwards - repeat for the same seed.
     */
    public GameMap(int width, int height, long seed) {
        this.seed = seed;
        this.randomizer = new Randomizer(seed);

        this.width = width;
        this.height = height;
        this.spaces = new ASpace[height][width];
        this.outboundWater = new Water(-1,-1,-1);
        System.out.println(width + " " + height);
        System.out.println(this.spaces.length + " " + this.spaces[0].length);

        ISpaceFactory[] all_available_spaces = new ISpaceFactory[]{new ElevationFactory(),new MountainFactory(),new PastureFactory(),new WaterFactory(),new WoodFactory(),};
        for(int i=0;i<height;i++) {
            for(int j=0;j<width;j++) {
                if (i == 0 || j == 0 || i == height-1 || j == width-1)
                    this.spaces[i][j] = this.outboundWater;
                else
                    this.spaces[i][j] = all_available_spaces[2].crateSpace(i,j,1);
            }
        }

        this.spaces[height/2][width/2] = new Gate(height/2,width/2,1, GENERATED_GATE_PRICE);

        this.thePlayer = new Player(height/2-1,width/2-1);
        Pair<Number, Number> p = thePlayer.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(thePlayer);
        thePlayer.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);

        /*
            The NPCs go onto the map after it exists and only onto spaces that can be walked on:
            drawing their coordinates before the terrain is there is how a merchant ends up in the
            water, unreachable for the whole episode and with it the gold the gate needs.
         */
        this.characters = new ArrayList<>();
        addOnFreeSpace(Barbarian::new);
        addOnFreeSpace(Villager::new);
        addOnFreeSpace((x,y) -> new Merchant(x,y,GENERATED_MERCHANT_BUDGET));
    }

    /**
     * Puts a character on a space that is free, or leaves it out of the world when the map has no
     * free space left - a character standing in the water would be worse than one that is missing.
     */
    private void addOnFreeSpace(BiFunction<Integer,Integer,ICharacter> create) {
        Pair<Number, Number> spot = findFreeSpace();
        if(spot == null)
            return;

        ICharacter character = create.apply(spot.getX1(), spot.getX2());
        this.characters.add(character);
        this.spaces[spot.getX1()][spot.getX2()].setOccupyingCharacter(character);
        character.setCurrentSpace(this.spaces[spot.getX1()][spot.getX2()]);
    }

    /**
     * A space nobody stands on that can be walked onto and is not the gate, drawn at random so the
     * seed decides where the NPCs start. Falls back to a scan when the draws keep missing, and
     * gives back null only when the map really has nowhere left to put anybody.
     */
    private Pair<Number, Number> findFreeSpace() {
        for(int attempt = 0; attempt < FREE_SPACE_DRAWS; attempt++) {
            int x = randomizer.randomInt(height), y = randomizer.randomInt(width);
            if(isFreeSpace(x,y))
                return new Pair<Number, Number>(x,y);
        }

        for(int x = 0; x < height; x++)
            for(int y = 0; y < width; y++)
                if(isFreeSpace(x,y))
                    return new Pair<Number, Number>(x,y);

        return null;
    }

    private boolean isFreeSpace(int x, int y) {
        ASpace space = this.spaces[x][y];
        return space.isReachable() && space.getOccupyingCharacter() == null
                && !(space instanceof Gate) && !(space instanceof Mountain);
    }

    /**
     * Whether a character standing on one space may step onto the next, as far as the terrain is
     * concerned (who is standing there is checked separately).
     * <p>
     * The island has two levels: mountains are the high ground, everything else is the low
     * ground, and elevations are the only spaces that connect the two. So a step between two
     * mountains, or between two low spaces, only needs the target to be reachable, but a step
     * onto or off a mountain needs the other end of it to be an elevation. A mountain that
     * touches no elevation can never be reached, which keeps the mountains that a level uses as
     * walls exactly that.
     */
    public static boolean canStep(ASpace from, ASpace to) {
        if(!to.isReachable())
            return false;

        boolean fromMountain = from instanceof Mountain, toMountain = to instanceof Mountain;
        if(fromMountain == toMountain)
            return true;

        return (fromMountain ? to : from) instanceof Elevation;
    }

    private ASpace spaceOf(ICharacter character) {
        Pair<Number, Number> p = character.getCoordinates();
        return this.spaces[p.getX1()][p.getX2()];
    }

    /**
     * The randomness of this world, created on demand for the maps that come from JSON. A map that
     * has not been given a seed yet draws from the seed it carries, which is 0 until an episode is
     * started - resetEpisode() is what puts a real seed in.
     */
    public Randomizer randomizer() {
        if(this.randomizer == null)
            this.randomizer = new Randomizer(this.seed);
        return this.randomizer;
    }


    /**
     * The 3x3 crop of the world around a character, the way every NPC sees it.
     */
    public ASpace[][] lookupForCharacter(ICharacter character) throws Exception{
        return lookupForCharacter(character, 1);
    }

    /**
     * The (2*radius+1) x (2*radius+1) crop of the world centered on a character. Cells that fall
     * outside the island are water, so a view has the same shape wherever it is taken - which is
     * what makes it usable as the input of a network with fixed dimensions.
     */
    public ASpace[][] lookupForCharacter(ICharacter character, int radius) throws Exception{
        if(radius < 0)
            throw new IllegalArgumentException("Radius must not be negative, got " + radius);

        if(!this.characters.contains(character) && !character.equals(this.thePlayer)) {
            throw new UnknownCharacter(character);
        }

        Pair<Number, Number> p = character.getCoordinates();

        int x = p.getX1(), y = p.getX2();
        int subMatrixSize = 2*radius + 1;

        ASpace[][] subMatrix = new ASpace[subMatrixSize][subMatrixSize];

        for (int i = x-radius; i <= x+radius; i++) {
            for (int j = y-radius; j <= y+radius; j++) {
                if(i<0 || i >= height || j<0 || j >= width)
                    subMatrix[i-x+radius][j-y+radius] = outboundWater();
                else {
                    ASpace space = this.spaces[i][j];
                    subMatrix[i - x + radius][j - y + radius] = space;
                }
            }
        }

        return subMatrix;
    }

    /**
     * The water every cell outside the island is made of. Created on demand, because a map read
     * from JSON is allocated by Gson without running a constructor and starts out without it.
     */
    private Water outboundWater() {
        if(this.outboundWater == null)
            this.outboundWater = new Water(-1,-1,-1);
        return this.outboundWater;
    }

    private void moveCharacterAfterCheck(ICharacter character,int x,int y) {
        this.spaces[x][y].setOccupyingCharacter(character);
        Pair<Number, Number> p = character.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(null);
        p.setX1(x);
        p.setX2(y);
        character.setCoordinates(p);
        if(character instanceof Player)
            rememberPlayerAt(x, y);
        character.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
    }

    /**
     * The history of the player's moves, created on demand (a map from JSON has none).
     */
    public Deque<Pair<Number, Number>> getPlayerMovementHistory() {
        if(this.playerMovementHistory == null)
            this.playerMovementHistory = new ArrayDeque<>();
        return this.playerMovementHistory;
    }

    private void rememberPlayerAt(int x, int y) {
        Deque<Pair<Number, Number>> history = getPlayerMovementHistory();
        history.addLast(new Pair<>(x, y));
        while(history.size() > TRAIL_DELAY + 1)
            history.removeFirst();
    }

    /** Forgets the trail and starts it again from where the player stands now. */
    private void restartPlayerTrail() {
        getPlayerMovementHistory().clear();
        if(this.thePlayer != null) {
            Pair<Number, Number> p = this.thePlayer.getCoordinates();
            rememberPlayerAt(p.getX1(), p.getX2());
        }
    }

    /**
     * The space the player stood on TRAIL_DELAY moves ago, which is all the barbarian knows about
     * where the player is when it cannot see it. Null until the player has made that many moves.
     */
    public Pair<Number, Number> getDelayedPlayerPosition() {
        Deque<Pair<Number, Number>> history = getPlayerMovementHistory();
        if(history.size() < TRAIL_DELAY + 1)
            return null;
        Pair<Number, Number> oldest = history.peekFirst();
        return new Pair<>(oldest.getX1(), oldest.getX2());
    }

    /**
     * Walks a character one space, if that space can be walked onto at all.
     *
     * @return true when the character actually moved, false when the space is off the island, not
     *         reachable or already taken - which is what lets a chase try another way around.
     */
    public boolean moveCharacter(ICharacter character,int x,int y){
        if(x<0 || x >= height || y<0 || y >= width) {
            return false;
        }
        if(!canStep(spaceOf(character), this.spaces[x][y]) || this.spaces[x][y].getOccupyingCharacter() != null)
            return false;

        moveCharacterAfterCheck(character,x,y);
        return true;
    }

    public void movePlayer(Player character,int x,int y){
        if(x<0 || x >= height || y<0 || y >= width) {
            addEvent("BLOCKED");
            return;
        }

        if(!this.spaces[x][y].isReachable()) {
            addEvent("BLOCKED");
            return;
        }

        if(this.spaces[x][y].getOccupyingCharacter() != null) {
            ICharacter otherCharacter = this.spaces[x][y].getOccupyingCharacter();
            ((INonPlayableCharacter) otherCharacter).interactWithPlayer(this, character);
            addEvent("BLOCKED");
            return;
        }

        /*
            After the occupant check on purpose: an NPC interacts with a player next to it
            whatever the two of them are standing on, so the player bumping into one across the
            foot of a mountain interacts as well. Only the step itself needs the elevation.
         */
        if(!canStep(spaceOf(character), this.spaces[x][y])) {
            addEvent("BLOCKED");
            return;
        }

        moveCharacterAfterCheck(character,x,y);
    }

    /**
     * Puts every character on the space it is standing on. A map coming from JSON has its
     * characters deserialized field by field, so until this runs no space knows who occupies it.
     */
    public void placeCharacters() {
        for(ICharacter character:this.characters) {
            Pair<Number, Number> p = character.getCoordinates();
            this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(character);
            character.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
            System.out.println(character+" is occuping  "+character.getCurrentSpace());
        }

        Pair<Number, Number> p = thePlayer.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(thePlayer);
        thePlayer.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
        System.out.println(thePlayer+" is occuping  "+thePlayer.getCurrentSpace());

        restartPlayerTrail();
    }

    public void run() {
//        try {
//            Thread.sleep(10*1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        placeCharacters();

        this.loopRunning = true;
        int numberOfRounds = 0;
        try {
            while (running && !turnBased) {
                try {
                    makeARound();
//                    Thread.sleep(5*10);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
                ++numberOfRounds;
            }
        } finally {
            this.loopRunning = false;
        }

        System.out.println("Game over in " + numberOfRounds);
    }

    /**
     * Stops the background thread and hands the rounds over to the caller. Blocks until the loop
     * has actually left makeARound(), so that a step can never run in parallel with it.
     */
    public void enterTurnBasedMode() {
        if(turnBased)
            return;

        this.turnBased = true;

        long deadline = System.currentTimeMillis() + 2000;
        while (loopRunning && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // the loop may have been switched off before it ever ran, so make sure the map is set up
        placeCharacters();
    }

    /**
     * A round driven by the background thread: it waits for an action to be buffered by
     * /player/up and friends. Returns without touching the world when the loop is shutting down.
     */
    public void makeARound() throws Exception {
        PlayerActions action = thePlayer.awaitBufferedAction(this);
        if(action == null)
            return;

        makeARound(action);
    }

    /**
     * A full environment transition: the player acts, then every NPC acts, then the round is over.
     * Everything that happened on the way is collected in getEvents().
     */
    public void makeARound(PlayerActions action) throws Exception {
        clearEvents();
        ++this.round;

        thePlayer.performAction(this, action);

        if(this.done)
            return;

        for(ICharacter ch:characters) {
//            System.out.println(ch+" is making a move in world  "+this.hashCode());
            ch.interactWithWorld(this, this.lookupForCharacter(ch));
        }
//        System.out.println("TICK");

        // an episode with a round limit has to end by itself, otherwise an agent that never finds
        // the gate keeps playing forever and every client has to invent its own timeout
        if(this.maxRounds > 0 && this.round >= this.maxRounds) {
            addEvent("OUT_OF_TIME");
            endGame(false);
        }
    }

    public List<String> getEvents() {
        if(this.events == null)
            this.events = new ArrayList<>();
        return this.events;
    }

    public void addEvent(String event) {
        getEvents().add(event);
    }

    public void clearEvents() {
        getEvents().clear();
    }

    /**
     * The map as a matrix of symbols, with the characters drawn over the spaces they stand on.
     */
    public String[][] getMatrix() {
        String[][] matrix = new String[this.spaces.length][this.spaces[0].length];

        for(int i = 0; i < this.spaces.length; i++) {
            for(int j = 0; j < this.spaces[i].length; j++) {
                matrix[i][j] = String.valueOf(this.spaces[i][j].getSpaceSymbol());
            }
        }

        for(ICharacter c:this.characters) {
            matrix[c.getCoordinates().getX1()][c.getCoordinates().getX2()] = String.valueOf(c.getCharacterSymbol());
        }

        matrix[thePlayer.getCoordinates().getX1()][thePlayer.getCoordinates().getX2()]
                = String.valueOf(thePlayer.getCharacterSymbol());

        return matrix;
    }

    /**
     * What the player can see from where it stands: a (2*radius+1) square of symbols centered on
     * it, with water wherever the square hangs over the edge of the island. A radius of 0 is the
     * single cell the player is on.
     * <p>
     * It is a crop of getMatrix() and not of the spaces, so the local view and the full one agree
     * cell by cell and an agent can be moved from one to the other without relearning the symbols.
     */
    public String[][] getViewMatrix(int radius) {
        if(radius < 0)
            throw new IllegalArgumentException("Radius must not be negative, got " + radius);

        String[][] full = getMatrix();
        String water = String.valueOf(outboundWater().getSpaceSymbol());
        int rows = full.length, columns = full[0].length;

        Pair<Number, Number> p = thePlayer.getCoordinates();
        int x = p.getX1(), y = p.getX2();

        int size = 2*radius + 1;
        String[][] view = new String[size][size];

        for(int i = 0; i < size; i++) {
            for(int j = 0; j < size; j++) {
                int row = x - radius + i, column = y - radius + j;
                view[i][j] = (row < 0 || row >= rows || column < 0 || column >= columns)
                        ? water : full[row][column];
            }
        }

        return view;
    }

    /**
     * The amount of gold the player needs to leave the island, or -1 when the map has no gate.
     */
    public int getGatePrice() {
        for(ASpace[] row:this.spaces) {
            for(ASpace space:row) {
                if(space instanceof Gate gate)
                    return gate.getPriceOfEntrance();
            }
        }
        return -1;
    }

    /**
     * The episode settings of this level. Never null: a map that says nothing about them gets an
     * empty config, which resolves to the values the game had before it was configurable.
     */
    public EpisodeConfig getConfig() {
        if(this.config == null)
            this.config = new EpisodeConfig();
        return this.config;
    }

    /**
     * Makes the world match its config: takes the switched off NPCs off the map, gives the merchant
     * the budget and the refill rhythm of this level, sets the price of the gate and remembers the
     * round limit. Called on every episode start, so it also puts back what the last one spent.
     */
    public void applyConfig() {
        EpisodeConfig episodeConfig = getConfig();

        if(this.characters != null) {
            List<ICharacter> remaining = new ArrayList<>();
            for(ICharacter character:this.characters) {
                if(character instanceof Barbarian && !episodeConfig.isBarbarianEnabled()
                        || character instanceof Villager && !episodeConfig.isVillagerEnabled()) {
                    removeFromItsSpace(character);
                    continue;
                }
                remaining.add(character);
            }
            this.characters = remaining;

            for(ICharacter character:this.characters) {
                if(character instanceof Merchant merchant) {
                    if(episodeConfig.getMerchantBudget() != null)
                        merchant.setFullAmountOfGold(episodeConfig.getMerchantBudget());
                    merchant.setCurrentAmountOfGold(merchant.getFullAmountOfGold());
                    merchant.setRefillRounds(episodeConfig.refillRoundsOrDefault());
                    merchant.setRoundsSinceRefill(0);
                }
            }
        }

        if(episodeConfig.getGatePrice() != null && this.spaces != null) {
            for(ASpace[] row:this.spaces) {
                for(ASpace space:row) {
                    if(space instanceof Gate gate)
                        gate.setPriceOfEntrance(episodeConfig.getGatePrice());
                }
            }
        }

        this.maxRounds = episodeConfig.maxRoundsOrUnlimited();
    }

    /**
     * Frees the space a character was standing on. A map that is restarted in place has its
     * characters already placed, so an NPC that the config switches off would otherwise leave a
     * body behind: a space nobody can walk onto anymore.
     */
    private void removeFromItsSpace(ICharacter character) {
        ASpace space = character.getCurrentSpace();
        if(space == null) {
            Pair<Number, Number> p = character.getCoordinates();
            if(this.spaces == null || p.getX1() < 0 || p.getX1() >= this.spaces.length
                    || p.getX2() < 0 || p.getX2() >= this.spaces[0].length)
                return;
            space = this.spaces[p.getX1()][p.getX2()];
        }

        if(space.getOccupyingCharacter() == character)
            space.setOccupyingCharacter(null);
        character.setCurrentSpace(null);
    }

    /**
     * The configuration of the running episode as the world actually is, for the API to report.
     */
    public EpisodeConfigView describeConfig() {
        boolean barbarian = false, villager = false;
        int merchantRefillRounds = getConfig().refillRoundsOrDefault();

        for(ICharacter character:this.characters) {
            if(character instanceof Barbarian)
                barbarian = true;
            else if(character instanceof Villager)
                villager = true;
            else if(character instanceof Merchant merchant)
                merchantRefillRounds = merchant.getRefillRounds();
        }

        return new EpisodeConfigView(getGatePrice(), getMerchantBudget(), merchantRefillRounds,
                barbarian, villager, this.maxRounds);
    }

    /**
     * The gold the merchant starts every refill with, or -1 when the map has no merchant.
     */
    public int getMerchantBudget() {
        for(ICharacter character:this.characters) {
            if(character instanceof Merchant merchant)
                return merchant.getFullAmountOfGold();
        }
        return -1;
    }

    /**
     * The seed this episode was started with, the one to write into a training log to replay it.
     */
    public long getGameSeed() {
        return randomizer().getSeed();
    }

    /**
     * The gold the merchant can still spend this round, or -1 when the map has no merchant.
     */
    public int getMerchantGold() {
        for(ICharacter character:this.characters) {
            if(character instanceof Merchant merchant)
                return merchant.getCurrentAmountOfGold();
        }
        return -1;
    }

    @Override
    public String toString() {
        return "Map{" +
                "spaces=" + spaces +
                ", characters=" + characters +
                '}';
    }

    public String getMapString() {

        StringBuilder sb = new StringBuilder();
        for(ASpace[] arr:this.spaces) {
            sb.append(Arrays.stream(arr).map(ASpace::getSpaceSymbol)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString());
            sb.append("\n");
        }
        for(ICharacter character:characters) {
            Pair<Number, Number> pair = character.getCoordinates();
            sb.setCharAt((pair.getX1()*(width+1)) + pair.getX2(),character.getCharacterSymbol());
        }

        Pair<Number, Number> pair = thePlayer.getCoordinates();
        sb.setCharAt((pair.getX1()*(width+1)) + pair.getX2(),thePlayer.getCharacterSymbol());

        return sb.toString();
    }

    public void setCharacters(List<ICharacter> characters) {

        this.characters = characters;
        for(ICharacter character : this.characters) {
            System.out.println(character.toString());
            Pair<Number, Number> p = character.getCoordinates();
            this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(character);
        }
    }

    /**
     * Starts an episode on a seed nobody asked for. The seed is still drawn and reported, so an
     * episode that turns out to be interesting can be replayed afterwards.
     */
    public void resetEpisode() {
        resetEpisode(new Random().nextLong());
    }

    /**
     * Puts the episode counters back to the start of a game and reseeds the randomness of the
     * whole world, so that the same seed plays out the same way. A map loaded from JSON comes
     * with the counters already at zero, this is here for the maps that are restarted in place.
     */
    public void resetEpisode(long seed) {
        this.seed = seed;
        randomizer().reseed(seed);
        this.round = 0;
        this.done = false;
        this.won = false;
        this.turnBased = false;
        this.running = true;
        clearEvents();
        restartPlayerTrail();
        applyConfig();
    }

    public void endGame() {
        endGame(false);
    }

    public void endGame(boolean won) {
        this.won = won;
        this.done = true;
        this.running = false;
    }

}
