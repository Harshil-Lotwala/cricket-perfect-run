package com.cricketperfectrun.backend.model;

public record PlayerSeasonStats(
        String mode,
        int year,
        String team,
        String playerName,
        int matches,
        int wins,
        int losses,
        int runs,
        int ballsFaced,
        int dismissals,
        int wickets,
        int ballsBowled,
        int runsConceded,
        int catches,
        int stumpings,
        int playerOfMatchAwards
) {}