package com.cricketperfectrun.backend.model;

public record GameModeConfig(
        String id,
        String title,
        int squadSize,
        Integer overseasLimit,
        int minKeepers,
        int easyRerolls,
        int normalRerolls,
        int hardRerolls,
        int legendRerolls,
        int winsForLeagueWin,
        int pointsForWin,
        int pointsForTieNoResult,
        int playoffTeams
) {}
