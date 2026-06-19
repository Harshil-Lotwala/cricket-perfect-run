package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import org.springframework.stereotype.Service;

/**
 * Computes derived per-season metrics (averages, strike/economy rates), a base playing role,
 * and an overall rating (OVR) entirely from real Cricsheet performance. No random values and no
 * hardcoded ratings.
 */
@Service
public class RatingService {

    public static final String BAT = "BAT";
    public static final String BOWL = "BOWL";
    public static final String AR = "AR";
    public static final String WK = "WK";

    /** -1 signals "not available" (e.g. never dismissed / never bowled). */
    public static final double NOT_AVAILABLE = -1.0;

    public double battingAverage(PlayerSeasonStats s) {
        if (s.dismissals() > 0) {
            return round(s.runs() / (double) s.dismissals());
        }
        // Never dismissed but scored runs -> average is undefined; expose runs as a floor only if they batted.
        return s.ballsFaced() > 0 ? round(s.runs()) : NOT_AVAILABLE;
    }

    public double strikeRate(PlayerSeasonStats s) {
        return s.ballsFaced() > 0 ? round(s.runs() * 100.0 / s.ballsFaced()) : NOT_AVAILABLE;
    }

    public double bowlingAverage(PlayerSeasonStats s) {
        return s.wickets() > 0 ? round(s.runsConceded() / (double) s.wickets()) : NOT_AVAILABLE;
    }

    public double economy(PlayerSeasonStats s) {
        return s.ballsBowled() > 0 ? round(s.runsConceded() * 6.0 / s.ballsBowled()) : NOT_AVAILABLE;
    }

    /**
     * Detects a base role from balls faced vs balls bowled and output. Keeper status is layered on
     * later by {@link PlayerMetadataService} using data-derived keeper eligibility.
     */
    public String detectRole(PlayerSeasonStats s) {
        boolean bowls = s.ballsBowled() >= 60;   // ~10+ overs in the season
        boolean bats = s.ballsFaced() >= 90;

        if (bowls && bats) {
            if (s.wickets() >= 6 && s.runs() >= 120) {
                return AR;
            }
            return (s.wickets() * 15) > s.runs() ? BOWL : BAT;
        }
        if (bowls) {
            return BOWL;
        }
        return BAT;
    }

    /** Overall rating 40-99 derived from real batting and bowling quality, weighted by role. */
    public int rating(PlayerSeasonStats s, String role) {
        double batScore = battingScore(s);
        double bowlScore = bowlingScore(s);

        double base = switch (role) {
            case BOWL -> bowlScore;
            case AR -> 0.55 * Math.max(batScore, bowlScore) + 0.45 * Math.min(batScore, bowlScore);
            default -> batScore; // BAT / WK
        };

        int rating = (int) Math.round(55 + base * 44);
        return Math.max(40, Math.min(99, rating));
    }

    /** 0..1 batting quality. */
    public double battingScore(PlayerSeasonStats s) {
        double avg = s.dismissals() > 0 ? s.runs() / (double) s.dismissals()
                : (s.ballsFaced() > 0 ? s.runs() : 0);
        double sr = s.ballsFaced() > 0 ? s.runs() * 100.0 / s.ballsFaced() : 0;

        double avgComponent = clamp01(avg / 45.0);
        double srComponent = clamp01((sr - 80.0) / 80.0);
        double volume = clamp01(s.runs() / 600.0);

        return clamp01(0.4 * avgComponent + 0.4 * srComponent + 0.2 * volume);
    }

    /** 0..1 bowling quality. */
    public double bowlingScore(PlayerSeasonStats s) {
        double econ = s.ballsBowled() > 0 ? s.runsConceded() * 6.0 / s.ballsBowled() : 12.0;
        double bowlAvg = s.wickets() > 0 ? s.runsConceded() / (double) s.wickets() : 40.0;

        double econComponent = clamp01((12.0 - econ) / 7.0);
        double wktComponent = clamp01(s.wickets() / 25.0);
        double avgComponent = clamp01((35.0 - bowlAvg) / 25.0);

        return clamp01(0.4 * econComponent + 0.4 * wktComponent + 0.2 * avgComponent);
    }

    private double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0;
        }
        return Math.min(1.0, v);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
