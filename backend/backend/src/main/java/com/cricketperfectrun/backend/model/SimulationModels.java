package com.cricketperfectrun.backend.model;

import java.util.List;
import java.util.Map;

/**
 * Container for the rich simulation result model. Nested records keep the related shapes together
 * and serialize cleanly to JSON for the frontend to persist and render (results, standings,
 * scorecards, awards) without re-running the simulation.
 */
public final class SimulationModels {

    private SimulationModels() {
    }

    /** A member of either the drafted XI or an opponent squad, normalised for the engine. */
    public record SquadMember(
            int id,
            String name,
            String role,
            boolean overseas,
            boolean keeperEligible,
            int rating,
            double battingScore,
            double bowlingScore,
            double leadershipScore,
            double keepingScore
    ) {}

    public record Squad(
            String name,
            String label,
            int strength,
            List<SquadMember> members
    ) {}

    public record BattingLine(String name, int runs, int balls, boolean out, boolean batted, String dismissal) {}

    public record BowlingLine(String name, double overs, int runs, int wickets) {}

    public record InningsCard(
            String team,
            int runs,
            int wickets,
            double overs,
            List<BattingLine> batting,
            List<BowlingLine> bowling
    ) {}

    public record MatchScorecard(
            String id,
            String stage,
            int matchNumber,
            String opponentName,
            String opponentLabel,
            boolean won,
            boolean drawn,
            String margin,
            String tossWinner,
            String tossDecision,
            String playerOfMatch,
            String topScorer,
            String bestBowler,
            List<InningsCard> innings,
            InningsCard teamInnings,
            InningsCard opponentInnings
    ) {}

    public record StandingRow(
            String team,
            int played,
            int won,
            int drawn,
            int lost,
            int points,
            double netRunRate
    ) {}

    public record Award(String category, String player, String detail) {}

    public record TeamRatingBreakdown(
            int overall,
            int batting,
            int bowling,
            int allRounderDepth,
            int keeping,
            double captainImpact,
            double keeperImpact
    ) {}

    public record SeasonResult(
            String mode,
            String opponentType,
            boolean hardMode,
            long seed,
            String stage,
            boolean champion,
            boolean perfect,
            int wins,
            int draws,
            int losses,
            int perfectTarget,
            String summary,
            TeamRatingBreakdown ratingBreakdown,
            List<StandingRow> standings,
            List<MatchScorecard> matches,
            List<Award> awards,
            Map<String, Object> meta
    ) {}
}
