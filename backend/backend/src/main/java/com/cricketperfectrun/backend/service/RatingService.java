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
        FormatBenchmarks b = benchmarks(s.mode());
        double rawAvg = s.dismissals() > 0 ? s.runs() / (double) s.dismissals() : b.battingAverage();
        double rawSr = s.ballsFaced() > 0 ? s.runs() * 100.0 / s.ballsFaced() : b.strikeRate();

        // Regress short samples toward a format baseline. This prevents a player with one or two
        // innings from receiving an elite rating while preserving genuinely sustained output.
        double avg = shrink(rawAvg, b.battingAverage(), s.dismissals(), b.battingDismissals());
        double sr = shrink(rawSr, b.strikeRate(), s.ballsFaced(), b.battingBalls());
        double avgComponent = quality(avg, b.battingAverage() * 0.45, b.eliteBattingAverage());
        double srComponent = quality(sr, b.strikeRate() * 0.70, b.eliteStrikeRate());
        double volume = clamp01(s.runs() / b.eliteRuns());
        double experience = clamp01(s.matches() / 8.0);

        return clamp01((0.48 * avgComponent + 0.27 * srComponent + 0.25 * volume)
                * (0.78 + 0.22 * experience));
    }

    /** 0..1 bowling quality. */
    public double bowlingScore(PlayerSeasonStats s) {
        FormatBenchmarks b = benchmarks(s.mode());
        double rawEcon = s.ballsBowled() > 0
                ? s.runsConceded() * 6.0 / s.ballsBowled() : b.economy();
        double rawAvg = s.wickets() > 0
                ? s.runsConceded() / (double) s.wickets() : b.bowlingAverage();
        double econ = shrink(rawEcon, b.economy(), s.ballsBowled(), b.bowlingBalls());
        double avg = shrink(rawAvg, b.bowlingAverage(), s.wickets(), b.bowlingWickets());

        double econComponent = inverseQuality(econ, b.eliteEconomy(), b.poorEconomy());
        double avgComponent = inverseQuality(avg, b.eliteBowlingAverage(), b.bowlingAverage() * 1.65);
        double wickets = clamp01(s.wickets() / b.eliteWickets());
        double experience = clamp01(s.matches() / 8.0);

        return clamp01((0.34 * econComponent + 0.36 * avgComponent + 0.30 * wickets)
                * (0.78 + 0.22 * experience));
    }

    private FormatBenchmarks benchmarks(String mode) {
        return switch (mode) {
            case "wtc" -> new FormatBenchmarks(32, 52, 55, 72, 650, 10, 400,
                    31, 20, 3.15, 2.35, 4.6, 32);
            case "odi-world-cup" -> new FormatBenchmarks(30, 42, 82, 115, 500, 8, 260,
                    32, 21, 5.25, 3.9, 7.2, 20);
            default -> new FormatBenchmarks(24, 38, 125, 165, 420, 7, 180,
                    28, 18, 8.0, 5.8, 10.5, 18);
        };
    }

    private double shrink(double observed, double baseline, int sample, double priorSample) {
        return (observed * sample + baseline * priorSample) / Math.max(1.0, sample + priorSample);
    }

    private double quality(double value, double floor, double elite) {
        return clamp01((value - floor) / (elite - floor));
    }

    private double inverseQuality(double value, double elite, double poor) {
        return clamp01((poor - value) / (poor - elite));
    }

    private record FormatBenchmarks(
            double battingAverage, double eliteBattingAverage,
            double strikeRate, double eliteStrikeRate, double eliteRuns,
            double battingDismissals, double battingBalls,
            double bowlingAverage, double eliteBowlingAverage,
            double economy, double eliteEconomy, double poorEconomy,
            double bowlingBalls, double bowlingWickets, double eliteWickets
    ) {
        private FormatBenchmarks(
                double battingAverage, double eliteBattingAverage,
                double strikeRate, double eliteStrikeRate, double eliteRuns,
                double battingDismissals, double battingBalls,
                double bowlingAverage, double eliteBowlingAverage,
                double economy, double eliteEconomy, double poorEconomy,
                double eliteWickets
        ) {
            this(battingAverage, eliteBattingAverage, strikeRate, eliteStrikeRate, eliteRuns,
                    battingDismissals, battingBalls, bowlingAverage, eliteBowlingAverage,
                    economy, eliteEconomy, poorEconomy, 240, 8, eliteWickets);
        }
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
