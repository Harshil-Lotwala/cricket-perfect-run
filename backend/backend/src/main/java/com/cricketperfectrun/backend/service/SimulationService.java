package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.dto.PlayerDTO;
import com.cricketperfectrun.backend.dto.SimulationRequest;
import com.cricketperfectrun.backend.model.SimulationModels.*;
import com.cricketperfectrun.backend.service.ModeConfigService.ModeConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic, seeded season simulator. Given the same request (including seed) it reproduces the
 * same result, so navigating to results/scorecards or refreshing never changes anything. Match
 * outcomes derive from real team strength (batting, bowling, all-round depth, keeping) plus captain
 * and keeper impact, against opponent strength, with calibrated randomness that makes titles
 * uncommon and undefeated runs rare.
 */
@Service
public class SimulationService {

    private static final String TEAM_NAME = "Your XI";

    private final SquadBuilder squadBuilder;
    private final StrengthCalculator strengthCalculator;
    private final OpponentService opponentService;
    private final ModeConfigService modeConfigService;

    public SimulationService(
            SquadBuilder squadBuilder,
            StrengthCalculator strengthCalculator,
            OpponentService opponentService,
            ModeConfigService modeConfigService
    ) {
        this.squadBuilder = squadBuilder;
        this.strengthCalculator = strengthCalculator;
        this.opponentService = opponentService;
        this.modeConfigService = modeConfigService;
    }

    public SeasonResult simulate(SimulationRequest request) {
        ModeConfig config = modeConfigService.config(request.getMode());
        String mode = config.id();
        String opponentType = request.getOpponentType() == null ? OpponentService.HISTORICAL : request.getOpponentType();
        boolean hardMode = request.isHardMode();
        long seed = request.getSeed() != null ? request.getSeed() : System.nanoTime();

        List<PlayerDTO> dtoTeam = request.getTeam() == null ? List.of() : request.getTeam();
        List<SquadMember> members = dtoTeam.stream().map(squadBuilder::fromDto).toList();

        SquadMember captain = findById(members, request.getCaptainId());
        SquadMember keeper = findById(members, request.getKeeperId());
        double captainImpact = captain == null ? -3.0 : captain.leadershipScore();
        double keeperImpact = keeper == null ? -3.0 : (keeper.keeperEligible() ? Math.max(0.5, keeper.keepingScore()) : -3.0);

        double teamStrength = strengthCalculator.teamStrength(members, captainImpact, keeperImpact);
        TeamRatingBreakdown breakdown = strengthCalculator.breakdown(members, captainImpact, keeperImpact);

        Random rng = new Random(seed * 1099511628211L + 0x9E3779B9L);

        // ---- League stage ----
        List<Squad> opponents = opponentService.selectOpponents(mode, opponentType, config.leagueMatches(), seed);
        List<MatchScorecard> matches = new ArrayList<>();
        Map<String, int[]> agg = new HashMap<>(); // name -> [runs, wickets, pom]

        int wins = 0;
        int losses = 0;
        int runsFor = 0;
        int runsAgainst = 0;

        for (int i = 0; i < opponents.size(); i++) {
            Squad opp = opponents.get(i);
            MatchScorecard card = simulateMatch(config, "League", i + 1, members, teamStrength, opp, hardMode, rng, agg);
            matches.add(card);
            if (card.won()) {
                wins++;
            } else {
                losses++;
            }
            runsFor += card.teamInnings().runs();
            runsAgainst += card.opponentInnings().runs();
        }

        double netRunRate = config.leagueMatches() == 0 ? 0
                : round((runsFor - runsAgainst) / (double) (config.leagueMatches() * config.oversPerInnings()));

        // ---- Standings ----
        List<StandingRow> standings = buildStandings(config, opponents, wins, losses, netRunRate, seed);
        int seedPosition = positionOf(standings);

        // ---- Playoffs ----
        boolean qualified = seedPosition <= config.playoffTeams();
        boolean champion = false;
        boolean lostKnockout = false;
        String stage;

        if (qualified) {
            int knockoutGames = seedPosition <= 2 ? config.knockoutMatches() : config.knockoutMatches() + 1;
            List<Squad> knockoutOpponents = opponentService.selectOpponents(mode, opponentType, knockoutGames, seed * 7 + 3);
            int matchNo = config.leagueMatches();
            for (int g = 0; g < knockoutGames; g++) {
                matchNo++;
                String knockoutStage = knockoutStageName(mode, g, knockoutGames);
                Squad opp = knockoutOpponents.get(g);
                MatchScorecard card = simulateMatch(config, knockoutStage, matchNo, members, teamStrength, opp, hardMode, rng, agg);
                matches.add(card);
                if (card.won()) {
                    wins++;
                } else {
                    losses++;
                    lostKnockout = true;
                    break;
                }
            }
            champion = !lostKnockout;
        }

        if (champion) {
            stage = "Champion";
        } else if (qualified && lostKnockout) {
            stage = wins >= config.leagueMatches() + config.knockoutMatches() - 1 ? "Runner-up" : "Playoffs";
        } else if (qualified) {
            stage = "Playoffs";
        } else {
            stage = "League Stage Exit";
        }

        boolean perfect = champion && losses == 0 && wins == config.perfectTarget();

        List<Award> awards = buildAwards(agg, members);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("teamStrength", round(teamStrength));
        meta.put("seedPosition", seedPosition);
        meta.put("runsFor", runsFor);
        meta.put("runsAgainst", runsAgainst);
        meta.put("opponentType", opponentType);

        String summary = buildSummary(stage, wins, losses, perfect, config, opponentType);

        return new SeasonResult(
                mode, opponentType, hardMode, seed, stage, champion, perfect,
                wins, losses, config.perfectTarget(), summary, breakdown,
                standings, matches, awards, meta
        );
    }

    // ---------------------------------------------------------------------
    // Match simulation
    // ---------------------------------------------------------------------

    private MatchScorecard simulateMatch(
            ModeConfig config, String stage, int matchNumber,
            List<SquadMember> team, double teamStrength,
            Squad opponent, boolean hardMode, Random rng,
            Map<String, int[]> agg
    ) {
        double p = winProbability(teamStrength, opponent.strength(), hardMode);
        boolean won = rng.nextDouble() < p;

        double teamBat = strengthCalculator.battingStrength(team);
        double teamBowl = strengthCalculator.bowlingStrength(team);
        double oppBat = avgBatting(opponent.members());
        double oppBowl = avgBowling(opponent.members());

        int teamRuns = inningsTotal(config, teamBat, oppBowl, rng);
        int oppRuns = inningsTotal(config, oppBat, teamBowl, rng);

        // Make the scoreline consistent with the decided result.
        if (won && teamRuns <= oppRuns) {
            int tmp = Math.max(teamRuns, oppRuns) + 1 + rng.nextInt(20);
            teamRuns = tmp;
        } else if (!won && oppRuns <= teamRuns) {
            oppRuns = Math.max(teamRuns, oppRuns) + 1 + rng.nextInt(20);
        }

        int teamWickets = wicketsLost(rng, won);
        int oppWickets = wicketsLost(rng, !won);

        InningsCard teamInnings = buildInnings(config, TEAM_NAME, teamRuns, teamWickets, team, opponent.members(), rng, agg, true);
        InningsCard oppInnings = buildInnings(config, opponent.name(), oppRuns, oppWickets, toMembers(opponent), team, rng, null, false);

        // Our bowlers appear in the opponent's innings bowling lines; record their wickets for awards.
        if (agg != null) {
            for (BowlingLine line : oppInnings.bowling()) {
                agg.computeIfAbsent(line.name(), k -> new int[3])[1] += line.wickets();
            }
        }

        String topScorer = teamInnings.batting().isEmpty() ? "" : teamInnings.batting().get(0).name();
        String bestBowler = oppInnings.bowling().isEmpty() ? "" : oppInnings.bowling().stream()
                .max(Comparator.comparingInt(BowlingLine::wickets)).map(BowlingLine::name).orElse("");
        String pom = won ? topScorer : (oppInnings.batting().isEmpty() ? topScorer : oppInnings.batting().get(0).name());
        if (won && agg != null && !pom.isBlank()) {
            agg.computeIfAbsent(pom, k -> new int[3])[2]++;
        }

        String margin = marginText(won, teamRuns, oppRuns, teamWickets, oppWickets);

        return new MatchScorecard(
                stage + "-" + matchNumber,
                stage, matchNumber,
                opponent.name(), opponent.label(),
                won, margin, pom, topScorer, bestBowler,
                teamInnings, oppInnings
        );
    }

    private double winProbability(double teamStrength, double oppStrength, boolean hardMode) {
        double offset = hardMode ? 6.0 : 4.5;
        double p = 1.0 / (1.0 + Math.exp(-((teamStrength - oppStrength) - offset) / 6.0));
        return Math.max(0.05, Math.min(0.95, p));
    }

    private int inningsTotal(ModeConfig config, double batting, double oppBowling, Random rng) {
        double baseline = config.runsBaseline();
        double swing = baseline * 0.35;
        double total = baseline + (batting - 0.5) * swing - (oppBowling - 0.5) * swing * 0.8;
        total += (rng.nextDouble() - 0.5) * baseline * 0.25;
        return Math.max((int) Math.round(baseline * 0.45), (int) Math.round(total));
    }

    private int wicketsLost(Random rng, boolean batSideWon) {
        // Winners tend to lose fewer wickets.
        int base = batSideWon ? 3 + rng.nextInt(5) : 6 + rng.nextInt(5);
        return Math.min(10, base);
    }

    private InningsCard buildInnings(
            ModeConfig config, String teamName, int totalRuns, int wicketsLost,
            List<SquadMember> batting, List<SquadMember> bowlingOpponents,
            Random rng, Map<String, int[]> agg, boolean aggregate
    ) {
        List<SquadMember> order = new ArrayList<>(batting);
        order.sort(Comparator.comparingDouble(SquadMember::battingScore).reversed());

        List<BattingLine> battingLines = distributeBatting(config, order, totalRuns, wicketsLost, rng, agg, aggregate);
        List<BowlingLine> bowlingLines = distributeBowling(config, bowlingOpponents, wicketsLost, totalRuns, rng);

        double overs = config.oversPerInnings();
        return new InningsCard(teamName, totalRuns, wicketsLost, overs, battingLines, bowlingLines);
    }

    private List<BattingLine> distributeBatting(
            ModeConfig config, List<SquadMember> order, int totalRuns, int wicketsLost,
            Random rng, Map<String, int[]> agg, boolean aggregate
    ) {
        int n = order.size();
        double[] weights = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double positionFactor = Math.max(0.15, 1.0 - i * 0.09); // top order weighted higher
            double w = (0.2 + order.get(i).battingScore()) * positionFactor * (0.7 + rng.nextDouble() * 0.6);
            weights[i] = w;
            sum += w;
        }

        List<BattingLine> lines = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < n; i++) {
            int runs = sum == 0 ? 0 : (int) Math.round(totalRuns * weights[i] / sum);
            if (i == n - 1) {
                runs = Math.max(0, totalRuns - allocated);
            }
            allocated += runs;
            double sr = 0.9 + order.get(i).battingScore() * 0.7; // better batters score faster
            int balls = Math.max(runs == 0 ? 0 : 1, (int) Math.round(runs / Math.max(0.6, sr)));
            boolean out = i < wicketsLost;
            lines.add(new BattingLine(order.get(i).name(), runs, balls, out, out ? "out" : "not out"));

            if (aggregate && agg != null) {
                agg.computeIfAbsent(order.get(i).name(), k -> new int[3])[0] += runs;
            }
        }
        lines.sort(Comparator.comparingInt(BattingLine::runs).reversed());
        return lines;
    }

    private List<BowlingLine> distributeBowling(
            ModeConfig config, List<SquadMember> bowlers, int wicketsToTake, int runsConceded, Random rng
    ) {
        List<SquadMember> order = new ArrayList<>(bowlers);
        order.sort(Comparator.comparingDouble(SquadMember::bowlingScore).reversed());
        int used = Math.min(5, order.size());
        if (used == 0) {
            return List.of();
        }

        double totalOvers = config.oversPerInnings();
        double oversEach = round(totalOvers / used);

        // Distribute wickets weighted by bowling score.
        double sum = 0;
        double[] weights = new double[used];
        for (int i = 0; i < used; i++) {
            weights[i] = 0.2 + order.get(i).bowlingScore() + rng.nextDouble() * 0.4;
            sum += weights[i];
        }

        List<BowlingLine> lines = new ArrayList<>();
        int wicketsAllocated = 0;
        int runsAllocated = 0;
        for (int i = 0; i < used; i++) {
            int wkts = sum == 0 ? 0 : (int) Math.round(wicketsToTake * weights[i] / sum);
            int runs = (int) Math.round(runsConceded * (1.0 / used) * (0.7 + rng.nextDouble() * 0.6));
            if (i == used - 1) {
                wkts = Math.max(0, wicketsToTake - wicketsAllocated);
                runs = Math.max(0, runsConceded - runsAllocated);
            }
            wkts = Math.min(wkts, 10);
            wicketsAllocated += wkts;
            runsAllocated += runs;
            lines.add(new BowlingLine(order.get(i).name(), oversEach, runs, wkts));
        }
        return lines;
    }

    // ---------------------------------------------------------------------
    // Standings / awards / helpers
    // ---------------------------------------------------------------------

    private List<StandingRow> buildStandings(
            ModeConfig config, List<Squad> opponents, int wins, int losses, double nrr, long seed
    ) {
        List<StandingRow> rows = new ArrayList<>();
        rows.add(new StandingRow(TEAM_NAME, config.leagueMatches(), wins, losses, wins * 2, nrr));

        Random standRng = new Random(seed * 131 + 17);
        double avgField = opponents.stream().mapToInt(Squad::strength).average().orElse(70);
        for (Squad opp : opponents) {
            double q = 1.0 / (1.0 + Math.exp(-((opp.strength() - avgField)) / 8.0));
            int oppWins = 0;
            for (int m = 0; m < config.leagueMatches(); m++) {
                if (standRng.nextDouble() < q) {
                    oppWins++;
                }
            }
            int oppLosses = config.leagueMatches() - oppWins;
            double oppNrr = round((oppWins - oppLosses) / (double) config.leagueMatches() * 0.2);
            rows.add(new StandingRow(opp.label(), config.leagueMatches(), oppWins, oppLosses, oppWins * 2, oppNrr));
        }

        rows.sort(Comparator.comparingInt(StandingRow::points).reversed()
                .thenComparing(Comparator.comparingDouble(StandingRow::netRunRate).reversed()));
        return rows;
    }

    private int positionOf(List<StandingRow> standings) {
        for (int i = 0; i < standings.size(); i++) {
            if (TEAM_NAME.equals(standings.get(i).team())) {
                return i + 1;
            }
        }
        return standings.size();
    }

    private List<Award> buildAwards(Map<String, int[]> agg, List<SquadMember> members) {
        List<Award> awards = new ArrayList<>();
        if (agg.isEmpty()) {
            return awards;
        }

        String orangeCap = agg.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue()[0]))
                .map(Map.Entry::getKey).orElse("");
        int orangeRuns = orangeCap.isBlank() ? 0 : agg.get(orangeCap)[0];

        String purpleCap = agg.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue()[1]))
                .map(Map.Entry::getKey).orElse("");
        int purpleWkts = purpleCap.isBlank() ? 0 : agg.get(purpleCap)[1];

        String mvp = agg.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue()[0] + e.getValue()[1] * 20 + e.getValue()[2] * 25))
                .map(Map.Entry::getKey).orElse(orangeCap);

        Map<String, String> roleByName = new HashMap<>();
        for (SquadMember m : members) {
            roleByName.put(m.name(), m.role());
        }
        String bestAr = agg.entrySet().stream()
                .filter(e -> "AR".equals(roleByName.get(e.getKey())))
                .max(Comparator.comparingInt(e -> e.getValue()[0] + e.getValue()[1] * 20))
                .map(Map.Entry::getKey).orElse("");

        awards.add(new Award("MVP", mvp, "Most valuable player of the season"));
        awards.add(new Award("Orange Cap", orangeCap, orangeRuns + " runs"));
        awards.add(new Award("Purple Cap", purpleCap, purpleWkts + " wickets"));
        awards.add(new Award("Best Batter", orangeCap, orangeRuns + " runs"));
        awards.add(new Award("Best Bowler", purpleCap, purpleWkts + " wickets"));
        if (!bestAr.isBlank()) {
            int[] s = agg.get(bestAr);
            awards.add(new Award("Best All-Rounder", bestAr, s[0] + " runs & " + s[1] + " wickets"));
        }
        return awards;
    }

    private String knockoutStageName(String mode, int gameIndex, int totalGames) {
        boolean last = gameIndex == totalGames - 1;
        if (last) {
            return "Final";
        }
        if ("ipl".equals(mode)) {
            return totalGames == 2 ? "Qualifier 1" : (gameIndex == 0 ? "Eliminator" : "Qualifier 2");
        }
        return "Semi-Final";
    }

    private String marginText(boolean won, int teamRuns, int oppRuns, int teamWickets, int oppWickets) {
        int diff = Math.abs(teamRuns - oppRuns);
        if (won) {
            return "Won by " + diff + " runs";
        }
        return "Lost by " + diff + " runs";
    }

    private String buildSummary(String stage, int wins, int losses, boolean perfect, ModeConfig config, String opponentType) {
        StringBuilder sb = new StringBuilder();
        if (perfect) {
            sb.append("PERFECT RUN! Undefeated ").append(wins).append("-0 ");
        } else {
            sb.append(stage).append(" \u2014 ").append(wins).append("W ").append(losses).append("L. ");
        }
        sb.append("Opponents: ").append(OpponentService.LEGACY.equalsIgnoreCase(opponentType) ? "Legacy XI (boss)" : "Historical squads");
        sb.append(". Perfect target: ").append(config.perfectTarget()).append("-0.");
        return sb.toString();
    }

    private List<SquadMember> toMembers(Squad squad) {
        return squad.members();
    }

    private double avgBatting(List<SquadMember> members) {
        return strengthCalculator.battingStrength(members);
    }

    private double avgBowling(List<SquadMember> members) {
        return strengthCalculator.bowlingStrength(members);
    }

    private SquadMember findById(List<SquadMember> members, Integer id) {
        if (id == null) {
            return null;
        }
        return members.stream().filter(m -> m.id() == id).findFirst().orElse(null);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
