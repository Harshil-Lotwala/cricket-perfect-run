package com.cricketperfectrun.backend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives captain and keeper impact from real career data instead of hardcoded name maps.
 *
 * <p>Captaincy impact rewards genuine success and experience: high career win-rate, longevity
 * (matches played) and player-of-match recognition all raise the boost, while players with little
 * top-level experience receive a penalty. Keeper impact depends on whether the chosen keeper is
 * genuinely keeper-eligible.
 */
@Service
public class LeadershipRatingService {

    private static final int EXPERIENCE_THRESHOLD = 20;

    private final CricsheetParserService parserService;
    private final PlayerMetadataService metadataService;

    // mode -> (player -> [matches, wins, pom])
    private final Map<String, Map<String, int[]>> careerByMode = new ConcurrentHashMap<>();

    public LeadershipRatingService(CricsheetParserService parserService, PlayerMetadataService metadataService) {
        this.parserService = parserService;
        this.metadataService = metadataService;
    }

    /**
     * Captaincy impact in rating points. Experienced, successful leaders earn up to ~+7; players
     * with little experience are penalised.
     */
    public double captainImpact(String mode, String playerName) {
        int[] career = career(mode).get(playerName);
        if (career == null || career[0] == 0) {
            return -3.0;
        }

        int matches = career[0];
        int wins = career[1];
        int pom = career[2];

        if (matches < EXPERIENCE_THRESHOLD) {
            // Inexperienced captain: penalty easing from -3 (debutant) toward -1.
            return round(-3.0 + (matches / (double) EXPERIENCE_THRESHOLD) * 2.0);
        }

        double winRate = wins / (double) matches;
        double winComponent = (winRate - 0.5) * 12.0;       // ~ -6 .. +6
        double pomComponent = Math.min(2.0, pom * 0.1);      // sustained match-winning
        double experienceComponent = Math.min(2.0, matches / 100.0);

        return round(clamp(winComponent + pomComponent + experienceComponent, -2.0, 7.0));
    }

    /**
     * Keeper impact in rating points. A genuinely keeper-eligible choice is a positive; using a
     * non-keeper behind the stumps is penalised.
     */
    public double keeperImpact(String mode, String playerName) {
        if (playerName == null) {
            return -3.0;
        }
        if (!metadataService.isKeeperEligible(mode, playerName)) {
            return -3.0;
        }

        int[] career = career(mode).get(playerName);
        double experience = career == null ? 0 : Math.min(2.0, career[0] / 100.0);
        return round(clamp(2.0 + experience, 0.0, 4.0));
    }

    private Map<String, int[]> career(String mode) {
        return careerByMode.computeIfAbsent(mode, parserService::getCareerLeadership);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
