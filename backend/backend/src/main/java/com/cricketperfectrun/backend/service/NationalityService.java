package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives each player's nationality purely from the imported international datasets.
 *
 * <p>International Cricsheet files key their rosters by national team name (e.g. "India",
 * "Australia"). By scanning every ODI / T20I / Test match we can map a player to the national
 * team they most frequently represented. A player whose most-represented country is not India is
 * treated as overseas for IPL purposes. Players who never appear in any international match are
 * assumed Indian (uncapped IPL players are overwhelmingly domestic Indians). No nationality is
 * hardcoded anywhere.
 */
@Service
public class NationalityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NationalityService.class);

    private static final String INDIA = "India";

    // International modes whose rosters are keyed by national team.
    private static final String[] INTERNATIONAL_MODES = {"odi-world-cup", "t20-world-cup", "wtc"};

    private final CricsheetParserService parserService;
    // Lazily computed: player name -> resolved country.
    private final AtomicReference<Map<String, String>> countryByPlayer = new AtomicReference<>();

    public NationalityService(CricsheetParserService parserService) {
        this.parserService = parserService;
    }

    public String country(String playerName) {
        return ensureLoaded().getOrDefault(playerName, INDIA);
    }

    public boolean isOverseas(String playerName) {
        String country = ensureLoaded().get(playerName);
        // Unknown players default to Indian (domestic) and are therefore not overseas.
        return country != null && !INDIA.equalsIgnoreCase(country);
    }

    public boolean hasInternationalRecord(String playerName) {
        return ensureLoaded().containsKey(playerName);
    }

    private Map<String, String> ensureLoaded() {
        Map<String, String> existing = countryByPlayer.get();
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (countryByPlayer.get() == null) {
                countryByPlayer.set(buildCountryMap());
            }
        }

        return countryByPlayer.get();
    }

    private Map<String, String> buildCountryMap() {
        // player -> (country -> appearances)
        Map<String, Map<String, Integer>> counts = new HashMap<>();

        for (String mode : INTERNATIONAL_MODES) {
            try {
                for (PlayerSeasonStats stats : parserService.getPlayerStats(mode)) {
                    if (stats.playerName() == null || stats.playerName().isBlank()
                            || stats.team() == null || stats.team().isBlank()) {
                        continue;
                    }
                    counts.computeIfAbsent(stats.playerName(), ignored -> new HashMap<>())
                            .merge(stats.team(), Math.max(1, stats.matches()), Integer::sum);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping nationality data for mode {}: {}", mode, e.getMessage());
            }
        }

        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : counts.entrySet()) {
            String best = null;
            int bestCount = -1;
            for (Map.Entry<String, Integer> teamCount : entry.getValue().entrySet()) {
                if (teamCount.getValue() > bestCount) {
                    bestCount = teamCount.getValue();
                    best = teamCount.getKey();
                }
            }
            if (best != null) {
                resolved.put(entry.getKey(), best);
            }
        }

        LOGGER.info("Resolved nationality for {} players from international datasets", resolved.size());
        return resolved;
    }

}
