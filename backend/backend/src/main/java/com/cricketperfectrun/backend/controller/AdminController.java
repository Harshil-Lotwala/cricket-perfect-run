package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import com.cricketperfectrun.backend.service.CricsheetParserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CricsheetParserService parserService;

    public AdminController(CricsheetParserService parserService) {
        this.parserService = parserService;
    }

    @GetMapping("/cricsheet/stats/{mode}")
    public List<PlayerSeasonStats> statsByMode(@PathVariable String mode) throws Exception {
        return parserService.getPlayerStats(mode);
    }

    @GetMapping("/cricsheet/stats/{mode}/{year}/{team}")
    public List<PlayerSeasonStats> statsByYearAndTeam(
            @PathVariable String mode,
            @PathVariable int year,
            @PathVariable String team
    ) throws Exception {
        return parserService.getPlayerStatsByYearAndTeam(mode, year, team);
    }
}
