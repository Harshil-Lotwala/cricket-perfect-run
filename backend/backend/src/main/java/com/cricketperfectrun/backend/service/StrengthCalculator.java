package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.SimulationModels.SquadMember;
import com.cricketperfectrun.backend.model.SimulationModels.TeamRatingBreakdown;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Turns a squad into batting / bowling / all-round / keeping strengths and an overall team rating,
 * all derived from the members' real performance scores. Captain and keeper impacts are applied on
 * top for the player's own XI.
 */
@Service
public class StrengthCalculator {

    /** Overall strength (~40..95) for an opponent squad, with no captain/keeper user choice. */
    public int squadStrength(List<SquadMember> members) {
        double batting = battingStrength(members);
        double bowling = bowlingStrength(members);
        double arDepth = allRounderDepth(members);
        double s = 50 + 40 * (0.45 * batting + 0.45 * bowling + 0.10 * arDepth);
        return (int) Math.round(clamp(s, 35, 97));
    }

    /** Overall strength for the player's XI, including captain and keeper effects. */
    public double teamStrength(List<SquadMember> members, double captainImpact, double keeperImpact) {
        double batting = battingStrength(members);
        double bowling = bowlingStrength(members);
        double arDepth = allRounderDepth(members);
        double base = 50 + 40 * (0.45 * batting + 0.45 * bowling + 0.10 * arDepth);
        return clamp(base + captainImpact + keeperImpact, 30, 99);
    }

    public TeamRatingBreakdown breakdown(List<SquadMember> members, double captainImpact, double keeperImpact) {
        int batting = (int) Math.round(battingStrength(members) * 99);
        int bowling = (int) Math.round(bowlingStrength(members) * 99);
        int arDepth = (int) Math.round(allRounderDepth(members) * 99);
        int keeping = (int) Math.round(keepingStrength(members) * 99);
        int overall = (int) Math.round(teamStrength(members, captainImpact, keeperImpact));
        return new TeamRatingBreakdown(overall, batting, bowling, arDepth, keeping,
                round(captainImpact), round(keeperImpact));
    }

    /** 0..1 from the top 7 batting scores. */
    public double battingStrength(List<SquadMember> members) {
        return topAverage(members.stream().map(SquadMember::battingScore).sorted(Comparator.reverseOrder()).toList(), 7);
    }

    /** 0..1 from the top 5 bowling scores. */
    public double bowlingStrength(List<SquadMember> members) {
        return topAverage(members.stream().map(SquadMember::bowlingScore).sorted(Comparator.reverseOrder()).toList(), 5);
    }

    public double allRounderDepth(List<SquadMember> members) {
        return members.stream()
                .filter(m -> "AR".equals(m.role()))
                .mapToDouble(m -> Math.min(m.battingScore(), m.bowlingScore()))
                .sorted()
                .limit(3)
                .average()
                .orElse(0.0);
    }

    public double keepingStrength(List<SquadMember> members) {
        return members.stream()
                .filter(SquadMember::keeperEligible)
                .mapToDouble(m -> Math.max(0.4, m.battingScore()))
                .max()
                .orElse(0.0);
    }

    private double topAverage(List<Double> sortedDesc, int n) {
        if (sortedDesc.isEmpty()) {
            return 0;
        }
        int count = Math.min(n, sortedDesc.size());
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += sortedDesc.get(i);
        }
        return sum / count;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
