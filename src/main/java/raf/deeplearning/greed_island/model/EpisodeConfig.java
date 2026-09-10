package raf.deeplearning.greed_island.model;

import lombok.Getter;
import lombok.Setter;

/**
 * The knobs of an episode that used to be hard coded: how expensive the gate is, how much gold the
 * merchant has and how often it gets it back, which of the NPCs are on the map at all and how long
 * an episode may run. A map JSON carries it as an optional "config" object and the query parameters
 * of /map/restart override it field by field, so a curriculum is a matter of changing an URL.
 * <p>
 * Every field is boxed on purpose: Gson leaves a field that the JSON does not mention as null, and
 * null is what tells "the map did not say anything about this" apart from a value somebody chose.
 * A null always means today's behaviour - see the resolving getters below.
 */
@Getter
@Setter
public class EpisodeConfig {

    /** Rounds between two merchant refills when the config does not say otherwise. */
    public static final int DEFAULT_MERCHANT_REFILL_ROUNDS = 4;

    /** The gold needed to open the gate. Null keeps the price written in the map's Gate space. */
    private Integer gatePrice;

    /** The gold the merchant trades with. Null keeps the amount written in the map JSON. */
    private Integer merchantBudget;

    /** Rounds between two refills of the merchant's gold. Null means every 4 rounds, as before. */
    private Integer merchantRefillRounds;

    /** Whether the barbarians of the map are in the game. Null means yes, as before. */
    private Boolean barbarianEnabled;

    /** Whether the villagers of the map are in the game. Null means yes, as before. */
    private Boolean villagerEnabled;

    /** Rounds after which the episode ends unwon. Null - and anything below 1 - means no limit. */
    private Integer maxRounds;

    public EpisodeConfig() {
    }

    /**
     * The config of a map with the fields somebody asked for on top of it. Both sides may be null,
     * and a field the override does not mention keeps the value the map came with.
     */
    public static EpisodeConfig merge(EpisodeConfig base, EpisodeConfig override) {
        EpisodeConfig merged = new EpisodeConfig();

        if (base != null) {
            merged.gatePrice = base.gatePrice;
            merged.merchantBudget = base.merchantBudget;
            merged.merchantRefillRounds = base.merchantRefillRounds;
            merged.barbarianEnabled = base.barbarianEnabled;
            merged.villagerEnabled = base.villagerEnabled;
            merged.maxRounds = base.maxRounds;
        }

        if (override != null) {
            if (override.gatePrice != null) merged.gatePrice = override.gatePrice;
            if (override.merchantBudget != null) merged.merchantBudget = override.merchantBudget;
            if (override.merchantRefillRounds != null) merged.merchantRefillRounds = override.merchantRefillRounds;
            if (override.barbarianEnabled != null) merged.barbarianEnabled = override.barbarianEnabled;
            if (override.villagerEnabled != null) merged.villagerEnabled = override.villagerEnabled;
            if (override.maxRounds != null) merged.maxRounds = override.maxRounds;
        }

        return merged;
    }

    public int refillRoundsOrDefault() {
        if (merchantRefillRounds == null || merchantRefillRounds < 1)
            return DEFAULT_MERCHANT_REFILL_ROUNDS;
        return merchantRefillRounds;
    }

    public boolean isBarbarianEnabled() {
        return barbarianEnabled == null || barbarianEnabled;
    }

    public boolean isVillagerEnabled() {
        return villagerEnabled == null || villagerEnabled;
    }

    /** The round limit, or 0 when the episode may run forever - which is what it did until now. */
    public int maxRoundsOrUnlimited() {
        if (maxRounds == null || maxRounds < 1)
            return 0;
        return maxRounds;
    }

    @Override
    public String toString() {
        return "EpisodeConfig{gatePrice=" + gatePrice
                + ", merchantBudget=" + merchantBudget
                + ", merchantRefillRounds=" + merchantRefillRounds
                + ", barbarianEnabled=" + barbarianEnabled
                + ", villagerEnabled=" + villagerEnabled
                + ", maxRounds=" + maxRounds + '}';
    }
}
