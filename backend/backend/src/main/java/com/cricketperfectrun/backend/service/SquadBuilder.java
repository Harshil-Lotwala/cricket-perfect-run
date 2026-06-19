package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.dto.PlayerDTO;
import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import com.cricketperfectrun.backend.model.SimulationModels.SquadMember;
import org.springframework.stereotype.Service;

/**
 * Normalises enriched {@link Player} objects and inbound {@link PlayerDTO}s into engine-ready
 * {@link SquadMember}s with 0..1 batting / bowling scores derived consistently from real counting
 * stats.
 */
@Service
public class SquadBuilder {

    private final RatingService ratingService;

    public SquadBuilder(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    public SquadMember fromPlayer(Player p) {
        PlayerSeasonStats stats = toStats(p.mode(), p.year(), p.team(), p.name(),
                p.runs(), p.ballsFaced(), p.dismissals(), p.wickets(), p.ballsBowled(), p.runsConceded());

        return new SquadMember(
                p.id(),
                p.name(),
                p.role(),
                p.overseas(),
                p.keeperEligible(),
                p.rating(),
                ratingService.battingScore(stats),
                ratingService.bowlingScore(stats),
                p.leadershipScore(),
                p.keepingScore()
        );
    }

    public SquadMember fromDto(PlayerDTO d) {
        PlayerSeasonStats stats = toStats(d.getMode(), d.getYear(), d.getTeam(), d.getName(),
                d.getRuns(), d.getBallsFaced(), d.getDismissals(), d.getWickets(), d.getBallsBowled(), d.getRunsConceded());

        return new SquadMember(
                d.getId(),
                d.getName(),
                d.getRole() == null ? "BAT" : d.getRole(),
                d.isOverseas(),
                d.isKeeperEligible(),
                d.getRating(),
                ratingService.battingScore(stats),
                ratingService.bowlingScore(stats),
                d.getLeadershipScore(),
                d.getKeepingScore()
        );
    }

    private PlayerSeasonStats toStats(
            String mode, int year, String team, String name,
            int runs, int ballsFaced, int dismissals, int wickets, int ballsBowled, int runsConceded
    ) {
        return new PlayerSeasonStats(
                mode == null ? "ipl" : mode,
                year,
                team == null ? "" : team,
                name == null ? "" : name,
                0, 0, 0,
                runs, ballsFaced, dismissals,
                wickets, ballsBowled, runsConceded,
                0, 0, 0
        );
    }
}
