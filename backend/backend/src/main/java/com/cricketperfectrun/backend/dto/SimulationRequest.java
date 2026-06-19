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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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
}
