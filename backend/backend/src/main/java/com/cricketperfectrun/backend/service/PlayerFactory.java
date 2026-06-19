package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import org.springframework.stereotype.Service;

/**
 * Builds fully-enriched {@link Player} objects from raw {@link PlayerSeasonStats}, applying
 * data-derived role, nationality, keeper eligibility, ratings and leadership scores. Centralised so
 * the player API, Legacy XI builder and opponent pools all produce consistent player data.
 */
@Service
public class PlayerFactory {

    private final RatingService ratingService;
    private final PlayerMetadataService metadataService;
    private final LeadershipRatingService leadershipRatingService;

    public PlayerFactory(
            RatingService ratingService,
            PlayerMetadataService metadataService,
            LeadershipRatingService leadershipRatingService
    ) {
        this.ratingService = ratingService;
        this.metadataService = metadataService;
        this.leadershipRatingService = leadershipRatingService;
    }

    public Player fromStats(PlayerSeasonStats stats) {
        String baseRole = ratingService.detectRole(stats);
        String role = metadataService.resolveRole(stats.mode(), baseRole, stats.playerName());
        int rating = ratingService.rating(stats, role);

        double battingAverage = ratingService.battingAverage(stats);
        double strikeRate = ratingService.strikeRate(stats);
        double bowlingAverage = ratingService.bowlingAverage(stats);
        double economy = ratingService.economy(stats);

        boolean overseas = metadataService.isOverseas(stats.playerName());
        boolean keeperEligible = metadataService.isKeeperEligible(stats.mode(), stats.playerName());
        String country = metadataService.country(stats.playerName());

        double leadershipScore = leadershipRatingService.captainImpact(stats.mode(), stats.playerName());
        double keepingScore = keeperEligible
                ? leadershipRatingService.keeperImpact(stats.mode(), stats.playerName())
                : 0.0;

        return new Player(
                stableId(stats),
                stats.playerName(),
                stats.team(),
                stats.year(),
                stats.mode(),
                role,
                country,
                overseas,
                keeperEligible,
                rating,
                stats.matches(),
                stats.wins(),
                stats.losses(),
                stats.runs(),
                stats.ballsFaced(),
                stats.dismissals(),
                stats.wickets(),
                stats.ballsBowled(),
                stats.runsConceded(),
                stats.catches(),
                stats.stumpings(),
                stats.playerOfMatchAwards(),
                battingAverage,
                strikeRate,
                bowlingAverage,
                economy,
                leadershipScore,
                keepingScore,
                statsSummary(role, stats, battingAverage, strikeRate, bowlingAverage, economy)
        );
    }

    public int stableId(PlayerSeasonStats stats) {
        String key = stats.mode() + "|" + stats.year() + "|" + stats.team() + "|" + stats.playerName();
        return Math.abs(key.hashCode());
    }

    /**
     * Role-specific stat line. BAT/WK -> Runs, Avg, SR. BOWL -> Wickets, Avg, Econ.
     * AR -> Wickets, Bat Avg, Econ. Missing values render as N/A.
     */
    private String statsSummary(
            String role,
            PlayerSeasonStats stats,
            double battingAverage,
            double strikeRate,
            double bowlingAverage,
            double economy
    ) {
        return switch (role) {
            case RatingService.BOWL -> stats.wickets() + " wkts | Avg " + fmt(bowlingAverage)
                    + " | Econ " + fmt(economy);
            case RatingService.AR -> stats.wickets() + " wkts | Bat Avg " + fmt(battingAverage)
                    + " | Econ " + fmt(economy);
            default -> stats.runs() + " runs | Avg " + fmt(battingAverage)
                    + " | SR " + fmt(strikeRate);
        };
    }

    private String fmt(double value) {
        return value < 0 ? "N/A" : String.valueOf(value);
    }
}
