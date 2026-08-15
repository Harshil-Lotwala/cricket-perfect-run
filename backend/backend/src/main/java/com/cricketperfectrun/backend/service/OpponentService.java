package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.TeamSeasonCard;
import com.cricketperfectrun.backend.model.SimulationModels.Squad;
import com.cricketperfectrun.backend.model.SimulationModels.SquadMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    private static final Set<String> WTC_COUNTRIES = Set.of(
            "Australia", "Bangladesh", "England", "India", "New Zealand",
            "Pakistan", "South Africa", "Sri Lanka", "West Indies"
    );

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

    public List<Squad> selectOpponents(String mode, String opponentType, int count, long seed, Integer maxSeason) {
        if (LEGACY.equalsIgnoreCase(opponentType)) {
            return pickLegacy(mode, count, maxSeason);
        }
        return pickHistorical(mode, count, seed, maxSeason);
    }

    private List<Squad> pickHistorical(String mode, int count, long seed, Integer selectedSeason) {
        boolean exactTournamentEdition = isWorldCup(mode) && selectedSeason != null;
        List<TeamSeasonCard> pool = new ArrayList<>(parserService.getTeamSeasonCards(mode).stream()
                .filter(card -> card.playerCount() >= XI)
                .filter(card -> !isWtc(mode) || WTC_COUNTRIES.contains(card.team()))
                .filter(card -> selectedSeason == null
                        || (exactTournamentEdition ? card.year() == selectedSeason : card.year() <= selectedSeason))
                .toList());
        if (pool.isEmpty()) {
            // Invalid/stale season: fall back to the full pool so simulation can still complete.
            pool = new ArrayList<>(parserService.getTeamSeasonCards(mode).stream()
                    .filter(card -> card.playerCount() >= XI)
                    .filter(card -> !isWtc(mode) || WTC_COUNTRIES.contains(card.team()))
                    .toList());
        }

        // Pick the strongest qualifying sides, rather than randomly dropping a contender.
        List<Squad> ranked = pool.stream()
                .map(card -> historicalSquad(mode, card))
                .filter(squad -> squad != null)
                .sorted(Comparator.comparingInt(Squad::strength).reversed()
                        .thenComparing(Squad::name))
                .toList();
        // Ranking still decides which distinct team identities deserve a place when the pool is
        // larger than the competition. The historical version of each chosen identity, however,
        // is selected from all eligible seasons by the run seed—not permanently fixed to its peak.
        Map<String, List<Squad>> versionsByTeam = new LinkedHashMap<>();
        for (Squad squad : ranked) {
            versionsByTeam.computeIfAbsent(teamIdentity(mode, squad.name()), ignored -> new ArrayList<>())
                    .add(squad);
        }

        List<Squad> opponents = new ArrayList<>();
        for (Map.Entry<String, List<Squad>> entry : versionsByTeam.entrySet()) {
            List<Squad> versions = entry.getValue();
            long teamSeed = seed
                    ^ ((long) entry.getKey().hashCode() << 32)
                    ^ 0x5DEECE66DL;
            Random versionRandom = new Random(teamSeed);
            opponents.add(versions.get(versionRandom.nextInt(versions.size())));
            if (opponents.size() == count) {
                break;
            }
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

    private List<Squad> pickLegacy(String mode, int count, Integer selectedSeason) {
        Set<String> eligibleTeams = new HashSet<>();
        if (isWorldCup(mode) && selectedSeason != null) {
            parserService.getTeamSeasonCards(mode).stream()
                    .filter(card -> card.year() == selectedSeason && card.playerCount() >= XI)
                    .map(TeamSeasonCard::team)
                    .forEach(eligibleTeams::add);
        }
        List<Squad> pool = legacyXiService.legacySquads(mode).stream()
                .filter(squad -> eligibleTeams.isEmpty() || eligibleTeams.contains(squad.name()))
                .filter(squad -> !isWtc(mode) || WTC_COUNTRIES.contains(squad.name()))
                .sorted(Comparator.comparingInt(Squad::strength).reversed()
                        .thenComparing(Squad::name))
                .toList();
        List<Squad> opponents = new ArrayList<>();
        Set<String> selectedTeams = new HashSet<>();
        for (Squad squad : pool) {
            if (!selectedTeams.add(teamIdentity(mode, squad.name()))) {
                continue;
            }
            opponents.add(squad);
            if (opponents.size() == count) {
                break;
            }
        }
        return opponents;
    }

    private boolean isWorldCup(String mode) {
        return mode != null && mode.endsWith("world-cup");
    }

    private boolean isWtc(String mode) {
        return "wtc".equalsIgnoreCase(mode);
    }

    private String teamIdentity(String mode, String team) {
        if (!"ipl".equalsIgnoreCase(mode) || team == null) {
            return team == null ? "" : team.trim().toLowerCase();
        }
        return switch (team.trim().toLowerCase()) {
            case "delhi daredevils", "delhi capitals" -> "delhi capitals";
            case "kings xi punjab", "punjab kings" -> "punjab kings";
            case "royal challengers bangalore", "royal challengers bengaluru" -> "royal challengers bengaluru";
            case "rising pune supergiant", "rising pune supergiants" -> "rising pune supergiant";
            default -> team.trim().toLowerCase();
        };
    }
}
