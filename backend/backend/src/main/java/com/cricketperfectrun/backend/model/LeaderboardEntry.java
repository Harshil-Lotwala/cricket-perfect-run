package com.cricketperfectrun.backend.model;

import java.time.Instant;
import java.util.List;

public record LeaderboardEntry(
        String id,
        String displayName,
        String mode,
        String opponentType,
        boolean hardMode,
        long seed,
        int wins,
        int draws,
        int losses,
        int perfectTarget,
        boolean champion,
        boolean perfect,
        int overallRating,
        String captain,
        String keeper,
        List<LeaderboardPlayer> team,
        Instant submittedAt
) {
    public record LeaderboardPlayer(String name, String role, int rating, int year, String sourceTeam) {}
}
