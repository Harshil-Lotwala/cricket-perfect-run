package com.cricketperfectrun.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inbound player payload from the frontend. Mirrors the enriched Player produced by the API so the
 * simulation can recompute strength from real counting stats. Unknown properties are ignored so the
 * frontend can send the full Player object verbatim.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerDTO {

    private int id;
    private String name;
    private String team;
    private int year;
    private String mode;
    private String role;
    private String country;
    private boolean overseas;
    private boolean keeperEligible;
    private int rating;

    private int runs;
    private int ballsFaced;
    private int dismissals;
    private int wickets;
    private int ballsBowled;
    private int runsConceded;

    private double battingAverage;
    private double strikeRate;
    private double bowlingAverage;
    private double economy;
    private double leadershipScore;
    private double keepingScore;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeam() {
        return team;
    }

    public int getYear() {
        return year;
    }

    public String getMode() {
        return mode;
    }

    public String getRole() {
        return role;
    }

    public String getCountry() {
        return country;
    }

    public boolean isOverseas() {
        return overseas;
    }

    public boolean isKeeperEligible() {
        return keeperEligible;
    }

    public int getRating() {
        return rating;
    }

    public int getRuns() {
        return runs;
    }

    public int getBallsFaced() {
        return ballsFaced;
    }

    public int getDismissals() {
        return dismissals;
    }

    public int getWickets() {
        return wickets;
    }

    public int getBallsBowled() {
        return ballsBowled;
    }

    public int getRunsConceded() {
        return runsConceded;
    }

    public double getBattingAverage() {
        return battingAverage;
    }

    public double getStrikeRate() {
        return strikeRate;
    }

    public double getBowlingAverage() {
        return bowlingAverage;
    }

    public double getEconomy() {
        return economy;
    }

    public double getLeadershipScore() {
        return leadershipScore;
    }

    public double getKeepingScore() {
        return keepingScore;
    }
}
