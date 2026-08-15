package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.dto.LeaderboardSubmission;
import com.cricketperfectrun.backend.model.LeaderboardEntry;
import com.cricketperfectrun.backend.service.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/{mode}")
    public List<LeaderboardEntry> list(@PathVariable String mode) {
        return leaderboardService.list(mode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaderboardEntry submit(@RequestBody LeaderboardSubmission submission) {
        return leaderboardService.submit(submission);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidSubmission(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }
}
