package com.cricketperfectrun.backend.model;

public record TeamSeasonCard(
        String mode,
        int year,
        String team,
        int playerCount
) {
    public String key() {
        return mode + "-" + year + "-" + team;
    }
}
