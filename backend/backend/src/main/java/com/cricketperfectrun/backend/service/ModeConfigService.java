package com.cricketperfectrun.backend.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Per-mode competition structure. Each mode has its own league length and knockout depth, and the
 * perfect-run target is league + knockouts:
 * <ul>
 *   <li>IPL: 14 league + 2 knockouts = 16-0 (the 16-0 reference)</li>
 *   <li>ODI World Cup: 9 league + 2 knockouts = 11-0</li>
 *   <li>T20 World Cup: 6 group/Super 8 + 2 knockouts = 8-0</li>
 *   <li>WTC: 13 tests + 1 final = 14-0</li>
 * </ul>
 */
@Service
public class ModeConfigService {

    public record ModeConfig(
            String id,
            String format,        // T20 | ODI | TEST
            int leagueMatches,
            int knockoutMatches,
            int playoffTeams,
            int oversPerInnings,
            int runsBaseline
    ) {
        public int perfectTarget() {
            return leagueMatches + knockoutMatches;
        }
    }

    public ModeConfig config(String mode) {
        String canonical = canonical(mode);
        return switch (canonical) {
            case "odi-world-cup" -> new ModeConfig(canonical, "ODI", 9, 2, 4, 50, 270);
            case "t20-world-cup" -> new ModeConfig(canonical, "T20", 6, 2, 4, 20, 160);
            case "wtc" -> new ModeConfig(canonical, "TEST", 13, 1, 2, 90, 320);
            default -> new ModeConfig("ipl", "T20", 14, 2, 4, 20, 165);
        };
    }

    private String canonical(String mode) {
        if (mode == null) {
            return "ipl";
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "odi-world-cup", "odi" -> "odi-world-cup";
            case "t20-world-cup", "t20" -> "t20-world-cup";
            case "wtc", "test", "tests" -> "wtc";
            default -> "ipl";
        };
    }
}
