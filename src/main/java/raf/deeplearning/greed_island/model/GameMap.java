package raf.deeplearning.greed_island.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import raf.deeplearning.greed_island.dto.EpisodeConfigView;
import raf.deeplearning.greed_island.model.characters.*;
import raf.deeplearning.greed_island.model.exception.UnknownCharacter;
import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Gate;
import raf.deeplearning.greed_island.model.spaces.Water;
import raf.deeplearning.greed_island.model.spaces.factory.*;
import raf.deeplearning.greed_island.model.utils.Pair;
import raf.deeplearning.greed_island.model.utils.Randomizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Getter
@Setter
public class GameMap implements Runnable{

    private int width, height;
    private ASpace[][] spaces;
    private List<ICharacter> characters;
    //        System.out.println("Getting the player>>>> "+thePlayer+ " <<<< in world  "+this.hashCode());
    private Player thePlayer;

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

    private transient static GameMap currentGameMap;
    private transient final Water outboundWater;

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
        Randomizer.getInstance().reseed(seed);

        this.width = width;
        this.height = height;
        this.spaces = new ASpace[height][width];
        this.outboundWater = new Water(-1,-1,-1);
        System.out.println(width + " " + height);
        System.out.println(this.spaces.length + " " + this.spaces[0].length);

        this.thePlayer = new Player(height/2-1,width/2-1);
        System.out.print("Here here");
        System.out.println("------------");

        Randomizer randomizer = Randomizer.getInstance();

        this.characters = new ArrayList<>();
        this.characters.add(new Barbarian(randomizer.randomInt(height),randomizer.randomInt(width)));
        this.characters.add(new Villager(randomizer.randomInt(height),randomizer.randomInt(width)));
        this.characters.add(new Merchant(randomizer.randomInt(height),randomizer.randomInt(width),50));
//        this.characters.add(thePlayer);

        ISpaceFactory[] all_available_spaces = new ISpaceFactory[]{new ElevationFactory(),new MountainFactory(),new PastureFactory(),new WaterFactory(),new WoodFactory(),};
        Random r = new Random();
        for(int i=0;i<height;i++) {
            for(int j=0;j<width;j++) {
//                this.spaces[i][j] = all_available_spaces[r.nextInt(5)].crateSpace(i,j,1);
                if (i == 0 || j == 0 || i == height-1 || j == width-1)
                    this.spaces[i][j] = this.outboundWater;
                else
                    this.spaces[i][j] = all_available_spaces[2].crateSpace(i,j,1);
            }
        }

        this.spaces[height/2][width/2] = new Gate(height/2,width/2,1, 105);

        for(ICharacter character : characters) {
            Pair p = character.getCoordinates();
            this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(character);
            character.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
        }

        Pair p = thePlayer.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(thePlayer);
        thePlayer.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);

    }

    public static GameMap getInstance() {
        if (currentGameMap == null) {
//            currentGameMap = new GameMap(r.nextInt(20)+5,r.nextInt(20)+5);
            currentGameMap = new GameMap(10,10);
        }

        return currentGameMap;
    }


    public ASpace[][] lookupForCharacter(ICharacter character) throws Exception{
        int subMatrixSize = 3;

        int x = 0,y = 0;

        if(!this.characters.contains(character) && !character.equals(this.thePlayer)) {
            throw new UnknownCharacter(character);
        }

        Pair p = character.getCoordinates();

        x = p.getX1();
        y = p.getX2();

        ASpace[][] subMatrix = new ASpace[subMatrixSize][subMatrixSize];

        for (int i = x-1; i < x+2; i++) {
            for (int j = y-1; j < y+2; j++) {
                if(i<0 || i >= height || j<0 || j >= width)
                    subMatrix[i-x+1][j-y+1] = this.outboundWater;
                else {
                    ASpace space = this.spaces[i][j];
                    subMatrix[i - x + 1][j - y + 1] = space;
                }
            }
        }

        return subMatrix;
    }

    private void moveCharacterAfterCheck(ICharacter character,int x,int y) {
        this.spaces[x][y].setOccupyingCharacter(character);
        Pair p = character.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(null);
        p.setX1(x);
        p.setX2(y);
        character.setCoordinates(p);

        character.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
    }

    public void moveCharacter(ICharacter character,int x,int y){
        if(x<0 || x >= height || y<0 || y >= width) {
            return;
        }
        if(!this.spaces[x][y].isReachable() || this.spaces[x][y].getOccupyingCharacter() != null)
            return;

        moveCharacterAfterCheck(character,x,y);
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
            if(otherCharacter instanceof INonPlayableCharacter)
                ((INonPlayableCharacter) otherCharacter).interactWithPlayer(character);
            else
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
            Pair p = character.getCoordinates();
            this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(character);
            character.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
            System.out.println(character+" is occuping  "+character.getCurrentSpace());
        }

        Pair p = thePlayer.getCoordinates();
        this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(thePlayer);
        thePlayer.setCurrentSpace(this.spaces[p.getX1()][p.getX2()]);
        System.out.println(thePlayer+" is occuping  "+thePlayer.getCurrentSpace());
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
        PlayerActions action = thePlayer.awaitBufferedAction();
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

        thePlayer.performAction(action);

        if(this.done)
            return;

        for(ICharacter ch:characters) {
//            System.out.println(ch+" is making a move in world  "+this.hashCode());
            ch.interactWithWorld(this.lookupForCharacter(ch));
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
            Pair p = character.getCoordinates();
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
     * The seed the current episode was started with. A map loaded from JSON never runs a
     * constructor, so the seed lives in the Randomizer singleton and not in a field of the map.
     */
    public long getGameSeed() {
        return Randomizer.getInstance().getSeed();
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

    public static GameMap createMap(int width, int height) {
        if(currentGameMap !=null) {
            System.err.println("Map already exists:"+ currentGameMap);
            return currentGameMap;
        }

        currentGameMap = new GameMap(width,height);
        return currentGameMap;
    }

    public String getMapString() {

        StringBuilder sb = new StringBuilder();
        for(ASpace[] arr:this.spaces) {
            sb.append(Arrays.stream(arr).map(ASpace::getSpaceSymbol)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString());
            sb.append("\n");
        }
        for(ICharacter character:characters) {
            Pair pair = character.getCoordinates();
            sb.setCharAt((pair.getX1()*(width+1)) + pair.getX2(),character.getCharacterSymbol());
        }

        Pair pair = thePlayer.getCoordinates();
        sb.setCharAt((pair.getX1()*(width+1)) + pair.getX2(),thePlayer.getCharacterSymbol());

        return sb.toString();
    }

    public void setCharacters(List<ICharacter> characters) {

        this.characters = characters;
        for(ICharacter character : this.characters) {
            System.out.println(character.toString());
            Pair p = character.getCoordinates();
            this.spaces[p.getX1()][p.getX2()].setOccupyingCharacter(character);
        }
    }

    public static void tryToSetCurrentMap(GameMap gameMap) {
        currentGameMap = gameMap;
//        if(currentGameMap == null)
//            currentGameMap = gameMap;
//        else System.err.println("Map already exists:"+ currentGameMap);
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
        Randomizer.getInstance().reseed(seed);
        this.round = 0;
        this.done = false;
        this.won = false;
        this.turnBased = false;
        this.running = true;
        clearEvents();
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
