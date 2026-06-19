package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.service.CricsheetParserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes data-derived metadata (available years and teams per year) so the frontend never has to
 * hardcode the season/team catalogue.
 */
@RestController
@RequestMapping("/api/meta")
@CrossOrigin
public class MetaController {

    private final CricsheetParserService parserService;

    public MetaController(CricsheetParserService parserService) {
        this.parserService = parserService;
    }

    @GetMapping("/{mode}/teams-by-year")
    public Map<Integer, List<String>> teamsByYear(@PathVariable String mode) {
        return parserService.getTeamsByYear(mode);
    }

    @GetMapping("/{mode}/years")
    public List<Integer> years(@PathVariable String mode) {
        return parserService.getAvailableYears(mode);
    }
}
