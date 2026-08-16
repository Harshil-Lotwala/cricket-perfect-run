package com.cricketperfectrun.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationRequest {

    private String mode;
    private List<PlayerDTO> team;
    private Integer captainId;
    private Integer keeperId;
    private String opponentType; // "historical" | "legacy"
    private boolean hardMode;
    private Long seed;
    private Integer maxSeason; // Null = random editions; otherwise draft cutoff and exact opponent edition.

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setTeam(List<PlayerDTO> team) {
        this.team = team;
    }

    public void setCaptainId(Integer captainId) {
        this.captainId = captainId;
    }

    public void setKeeperId(Integer keeperId) {
        this.keeperId = keeperId;
    }

    public void setOpponentType(String opponentType) {
        this.opponentType = opponentType;
    }

    public void setHardMode(boolean hardMode) {
        this.hardMode = hardMode;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public void setMaxSeason(Integer maxSeason) {
        this.maxSeason = maxSeason;
    }

    public List<PlayerDTO> getTeam() {
        return team;
    }

    public Integer getCaptainId() {
        return captainId;
    }

    public Integer getKeeperId() {
        return keeperId;
    }

    public String getOpponentType() {
        return opponentType;
    }

    public boolean isHardMode() {
        return hardMode;
    }

    public Long getSeed() {
        return seed;
    }

    public Integer getMaxSeason() {
        return maxSeason;
    }
}
