package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.TeamSeasonCard;
import com.cricketperfectrun.backend.model.SimulationModels.Squad;
import com.cricketperfectrun.backend.model.SimulationModels.SquadMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Builds the opponent pool per mode and selects a varied, seeded subset for each run. Like the 16-0
 * design, the player does NOT face every franchise: a different seed produces a different schedule,
 * so two runs of the same drafted XI yield different opponents.
 */
@Service
public class OpponentService {

    public static final String HISTORICAL = "historical";
    public static final String LEGACY = "legacy";

    private static final int XI = 11;

    private final CricsheetParserService parserService;
    private final PlayerFactory playerFactory;
    private final SquadBuilder squadBuilder;
    private final StrengthCalculator strengthCalculator;
    private final LegacyXiService legacyXiService;

    public OpponentService(
            CricsheetParserService parserService,
            PlayerFactory playerFactory,
            SquadBuilder squadBuilder,
            StrengthCalculator strengthCalculator,
            LegacyXiService legacyXiService
    ) {
        this.parserService = parserService;
        this.playerFactory = playerFactory;
        this.squadBuilder = squadBuilder;
        this.strengthCalculator = strengthCalculator;
        this.legacyXiService = legacyXiService;
    }

    public List<Squad> selectOpponents(String mode, String opponentType, int count, long seed) {
        Random random = new Random(seed);
        if (LEGACY.equalsIgnoreCase(opponentType)) {
            return pickLegacy(mode, count, random);
        }
        return pickHistorical(mode, count, random);
    }

    private List<Squad> pickHistorical(String mode, int count, Random random) {
        List<TeamSeasonCard> pool = new ArrayList<>(parserService.getTeamSeasonCards(mode).stream()
                .filter(card -> card.playerCount() >= XI)
                .toList());
        Collections.shuffle(pool, random);

        List<Squad> opponents = new ArrayList<>();
        for (TeamSeasonCard card : pool) {
            if (opponents.size() >= count) {
                break;
            }
            Squad squad = historicalSquad(mode, card);
            if (squad != null) {
                opponents.add(squad);
            }
        }
        // If the pool is smaller than required, cycle through it again for additional fixtures.
        int i = 0;
        while (opponents.size() < count && !pool.isEmpty()) {
            Squad squad = historicalSquad(mode, pool.get(i % pool.size()));
            if (squad != null) {
                opponents.add(squad);
            }
            i++;
        }
        return opponents;
    }

    private Squad historicalSquad(String mode, TeamSeasonCard card) {
        List<Player> players = parserService.getPlayerStatsByYearAndTeam(mode, card.year(), card.team())
                .stream()
                .map(playerFactory::fromStats)
                .sorted(Comparator.comparingInt(Player::rating).reversed())
                .limit(XI)
                .toList();
        if (players.size() < XI) {
            return null;
        }
        List<SquadMember> members = players.stream().map(squadBuilder::fromPlayer).toList();
        int strength = strengthCalculator.squadStrength(members);
        return new Squad(card.team(), card.year() + " " + card.team(), strength, members);
    }

    private List<Squad> pickLegacy(String mode, int count, Random random) {
        List<Squad> pool = new ArrayList<>(legacyXiService.legacySquads(mode));
        Collections.shuffle(pool, random);
        List<Squad> opponents = new ArrayList<>();
        int i = 0;
        while (opponents.size() < count && !pool.isEmpty()) {
            opponents.add(pool.get(i % pool.size()));
            i++;
        }
        return opponents;
    }
}
