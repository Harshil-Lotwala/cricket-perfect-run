package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.service.CricsheetParserService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Exposes data-derived metadata (available years and teams per year) so the frontend never has to
 * hardcode the season/team catalogue.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final CricsheetParserService parserService;

    public MetaController(CricsheetParserService parserService) {
        this.parserService = parserService;
    }

    @GetMapping("/{mode}/teams-by-year")
    public ResponseEntity<Map<Integer, List<String>>> teamsByYear(@PathVariable String mode) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(parserService.getTeamsByYear(mode));
    }

    @GetMapping("/{mode}/years")
    public ResponseEntity<List<Integer>> years(@PathVariable String mode) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(parserService.getAvailableYears(mode));
    }
}
