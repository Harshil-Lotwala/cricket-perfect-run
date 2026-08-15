package com.cricketperfectrun.backend.dto;

import java.util.List;

public record LeaderboardSubmission(
        String displayName,
        String mode,
        String opponentType,
        boolean hardMode,
        long seed,
        Integer maxSeason,
        Integer captainId,
        Integer keeperId,
        List<PlayerDTO> team,
        boolean publishConsent,
        int claimedWins,
        int claimedDraws,
        int claimedLosses
) {}
