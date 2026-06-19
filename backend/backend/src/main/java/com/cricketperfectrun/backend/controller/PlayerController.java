package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.service.CricsheetParserService;
import com.cricketperfectrun.backend.service.PlayerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@CrossOrigin
public class PlayerController {

    private final CricsheetParserService parserService;
    private final PlayerFactory playerFactory;

    public PlayerController(CricsheetParserService parserService, PlayerFactory playerFactory) {
        this.parserService = parserService;
        this.playerFactory = playerFactory;
    }

    @GetMapping("/{mode}/{year}/{team}")
    public List<Player> getPlayersByModeYearTeam(
            @PathVariable String mode,
            @PathVariable int year,
            @PathVariable String team
    ) {
        return parserService.getPlayerStatsByYearAndTeam(mode, year, team)
                .stream()
                .map(playerFactory::fromStats)
                .toList();
    }
}
