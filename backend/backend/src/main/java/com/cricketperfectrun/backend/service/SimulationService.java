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

        // Leadership and keeping are strategic choices, not cosmetic labels. Fold both selections
        // into the deterministic match seed so the same XI/season seed produces a different—but
        // still reproducible—run when either role changes.
        long captainChoice = request.getCaptainId() == null ? 0L : Integer.toUnsignedLong(request.getCaptainId());
        long keeperChoice = request.getKeeperId() == null ? 0L : Integer.toUnsignedLong(request.getKeeperId());
        long simulationSeed = seed * 1099511628211L
                ^ captainChoice * 0x9E3779B97F4A7C15L
                ^ keeperChoice * 0xC2B2AE3D27D4EB4FL;
        Random rng = new Random(simulationSeed);

        // ---- League stage ----
        Integer maxSeason = request.getMaxSeason();
        List<Squad> opponents = opponentService.selectOpponents(mode, opponentType, config.leagueMatches(), seed, maxSeason);
        List<MatchScorecard> matches = new ArrayList<>();
        Map<String, int[]> agg = new HashMap<>(); // name -> [runs, wickets, pom]

        int wins = 0;
        int draws = 0;
        int losses = 0;
        int runsFor = 0;
        int runsAgainst = 0;

        for (int i = 0; i < opponents.size(); i++) {
            Squad opp = opponents.get(i);
            MatchScorecard card = simulateMatch(config, "League", i + 1, members, teamStrength, opp, hardMode, rng, agg);
            matches.add(card);
            if (card.won()) {
                wins++;
            } else if (card.drawn()) {
                draws++;
            } else {
                losses++;
            }
            runsFor += card.innings().stream().filter(inn -> TEAM_NAME.equals(inn.team())).mapToInt(InningsCard::runs).sum();
            runsAgainst += card.innings().stream().filter(inn -> !TEAM_NAME.equals(inn.team())).mapToInt(InningsCard::runs).sum();
        }

        double netRunRate = config.leagueMatches() == 0 ? 0
                : round((runsFor - runsAgainst) / (double) (config.leagueMatches() * config.oversPerInnings()));

        // ---- Standings ----
        List<StandingRow> standings = buildStandings(config, opponents, wins, draws, losses, netRunRate, seed);
        int seedPosition = positionOf(standings);

        // ---- Playoffs ----
        boolean qualified = seedPosition <= config.playoffTeams();
        boolean champion = false;
        boolean lostKnockout = false;
        String stage;

        if (qualified) {
            if ("ipl".equals(mode)) {
                PlayoffRun playoff = simulateIplPlayoffs(
                        config, standings, opponents, seedPosition, members, teamStrength, hardMode, rng, agg
                );
                matches.addAll(playoff.matches());
                wins += playoff.wins();
                losses += playoff.losses();
                champion = playoff.champion();
                lostKnockout = !champion;
            } else {
                List<Squad> knockoutOpponents = seededKnockoutOpponents(config, standings, opponents, seedPosition, rng);
                int matchNo = config.leagueMatches();
                for (int g = 0; g < knockoutOpponents.size(); g++) {
                    matchNo++;
                    String knockoutStage = g == knockoutOpponents.size() - 1 ? "Final" : "Semi-Final";
                    Squad opp = knockoutOpponents.get(g);
                    MatchScorecard card = simulateMatch(config, knockoutStage, matchNo, members, teamStrength, opp, hardMode, rng, agg);
                    matches.add(card);
                    if (card.won()) {
                        wins++;
                    } else {
                        if (card.drawn()) draws++; else losses++;
                        lostKnockout = true;
                        break;
                    }
                }
                champion = !lostKnockout && !knockoutOpponents.isEmpty();
            }
        }

        if (champion) {
            stage = "Champion";
        } else if (qualified && lostKnockout) {
            MatchScorecard lastMatch = matches.isEmpty() ? null : matches.get(matches.size() - 1);
            stage = lastMatch != null && "Final".equals(lastMatch.stage()) ? "Runner-up" : "Playoffs";
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
        meta.put("captainId", request.getCaptainId());
        meta.put("keeperId", request.getKeeperId());

        String summary = buildSummary(stage, wins, draws, losses, perfect, config, opponentType);

        return new SeasonResult(
                mode, opponentType, hardMode, seed, stage, champion, perfect,
                wins, draws, losses, config.perfectTarget(), summary, breakdown,
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
        if ("TEST".equals(config.format())) {
            return simulateTestMatch(config, stage, matchNumber, team, teamStrength, opponent, hardMode, rng, agg);
        }
        double p = winProbability(teamStrength, opponent.strength(), hardMode);
        boolean won = rng.nextDouble() < p;

        double teamBat = strengthCalculator.battingStrength(team);
        double teamBowl = strengthCalculator.bowlingStrength(team);
        double oppBat = avgBatting(opponent.members());
        double oppBowl = avgBowling(opponent.members());

        boolean userWonToss = rng.nextBoolean();
        boolean tossChoseBat = rng.nextBoolean();
        boolean userBatsFirst = userWonToss == tossChoseBat;
        String tossWinner = userWonToss ? TEAM_NAME : opponent.name();
        String tossDecision = tossChoseBat ? "bat" : "bowl";

        int firstRuns = userBatsFirst
                ? inningsTotal(config, teamBat, oppBowl, rng)
                : inningsTotal(config, oppBat, teamBowl, rng);
        boolean chasingSideWon = userBatsFirst ? !won : won;
        int secondRuns = chasingSideWon
                ? firstRuns + 1
                : Math.max((int) Math.round(config.runsBaseline() * 0.4), firstRuns - 1 - rng.nextInt(31));

        int teamRuns = userBatsFirst ? firstRuns : secondRuns;
        int oppRuns = userBatsFirst ? secondRuns : firstRuns;
        int teamWickets = wicketsLost(rng, won);
        int oppWickets = wicketsLost(rng, !won);
        int firstWickets = userBatsFirst ? teamWickets : oppWickets;
        int secondWickets = userBatsFirst ? oppWickets : teamWickets;
        int firstBalls = limitedOversBalls(config, firstRuns, firstWickets);
        int secondBalls = chasingSideWon
                ? chaseBalls(config, firstRuns + 1, secondWickets, rng)
                : limitedOversBalls(config, secondRuns, secondWickets);

        int teamBalls = userBatsFirst ? firstBalls : secondBalls;
        int oppBalls = userBatsFirst ? secondBalls : firstBalls;

        InningsCard teamInnings = buildInnings(config, TEAM_NAME, teamRuns, teamWickets,
                team, opponent.members(), rng, agg, true, teamBalls);
        InningsCard oppInnings = buildInnings(config, opponent.name(), oppRuns, oppWickets,
                toMembers(opponent), team, rng, null, false, oppBalls);

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

        String margin;
        if (won) {
            margin = userBatsFirst
                    ? "Won by " + (teamRuns - oppRuns) + " runs"
                    : "Won by " + (10 - teamWickets) + " wickets";
        } else {
            margin = userBatsFirst
                    ? "Lost by " + (10 - oppWickets) + " wickets"
                    : "Lost by " + (oppRuns - teamRuns) + " runs";
        }
        List<InningsCard> inningsOrder = userBatsFirst
                ? List.of(teamInnings, oppInnings)
                : List.of(oppInnings, teamInnings);

        return new MatchScorecard(
                stage + "-" + matchNumber,
                stage, matchNumber,
                opponent.name(), opponent.label(),
                won, false, margin, tossWinner, tossDecision, pom, topScorer, bestBowler,
                inningsOrder,
                teamInnings, oppInnings
        );
    }

    private MatchScorecard simulateTestMatch(
            ModeConfig config, String stage, int matchNumber,
            List<SquadMember> team, double teamStrength,
            Squad opponent, boolean hardMode, Random rng,
            Map<String, int[]> agg
    ) {
        double p = winProbability(teamStrength, opponent.strength(), hardMode);
        double drawChance = 0.18;
        double resultRoll = rng.nextDouble();
        boolean drawn = resultRoll < drawChance;
        boolean won = !drawn && resultRoll < drawChance + (1.0 - drawChance) * p;

        double teamBat = strengthCalculator.battingStrength(team);
        double teamBowl = strengthCalculator.bowlingStrength(team);
        double oppBat = avgBatting(opponent.members());
        double oppBowl = avgBowling(opponent.members());

        boolean userWonToss = rng.nextBoolean();
        boolean tossChoseBat = rng.nextBoolean();
        boolean userBatsFirst = userWonToss == tossChoseBat;
        String tossWinner = userWonToss ? TEAM_NAME : opponent.name();
        String tossDecision = tossChoseBat ? "bat" : "bowl";
        if (!userBatsFirst) {
            return simulateTestMatchChasing(config, stage, matchNumber, team, opponent, rng, agg,
                    won, drawn, teamBat, teamBowl, oppBat, oppBowl, tossWinner, tossDecision);
        }

        int teamFirst = testInningsTotal(teamBat, oppBowl, rng);
        int oppFirst = testInningsTotal(oppBat, teamBowl, rng);
        int teamSecond = testInningsTotal(teamBat, oppBowl, rng);
        int target = Math.max(1, teamFirst + teamSecond - oppFirst + 1);
        int oppSecond;
        int oppSecondWickets;
        String margin;

        if (won) {
            oppSecond = Math.max(40, target - 1 - (15 + rng.nextInt(90)));
            oppSecondWickets = 10;
            margin = "Won by " + Math.max(1, target - 1 - oppSecond) + " runs";
        } else if (!drawn) {
            // The chase ends the moment the winning run is scored.
            oppSecond = target;
            oppSecondWickets = 2 + rng.nextInt(7);
            margin = "Lost by " + (10 - oppSecondWickets) + " wickets";
        } else {
            oppSecond = 0;
            oppSecondWickets = 3 + rng.nextInt(7);
            margin = "Match drawn";
        }

        int teamFirstWickets = 8 + rng.nextInt(3);
        int oppFirstWickets = 8 + rng.nextInt(3);
        int teamSecondWickets = won || drawn ? 7 + rng.nextInt(4) : 10;
        int firstBalls = testInningsBalls(teamFirst, teamFirstWickets, rng);
        int secondBalls = testInningsBalls(oppFirst, oppFirstWickets, rng);
        int thirdBalls = testInningsBalls(teamSecond, teamSecondWickets, rng);
        int remainingBalls = Math.max(6, 2700 - firstBalls - secondBalls - thirdBalls);
        int requiredFourthBalls = drawn ? remainingBalls
                : testInningsBalls(oppSecond, oppSecondWickets, rng);
        int fourthBalls = Math.min(requiredFourthBalls, remainingBalls);
        if (drawn || requiredFourthBalls > remainingBalls) {
            // Five days expired before a result could be completed.
            drawn = true;
            won = false;
            double fourthRunRate = 2.5 + rng.nextDouble() * 1.1;
            oppSecond = Math.max(20, Math.min(target - 1,
                    (int) Math.round(fourthBalls / 6.0 * fourthRunRate)));
            oppSecondWickets = Math.min(9, Math.max(3, oppSecondWickets));
            margin = "Match drawn";
        }

        InningsCard t1 = buildInnings(config, TEAM_NAME, teamFirst, teamFirstWickets,
                team, opponent.members(), rng, agg, true, firstBalls);
        InningsCard o1 = buildInnings(config, opponent.name(), oppFirst, oppFirstWickets,
                opponent.members(), team, rng, null, false, secondBalls);
        InningsCard t2 = buildInnings(config, TEAM_NAME, teamSecond, teamSecondWickets,
                team, opponent.members(), rng, agg, true, thirdBalls);
        InningsCard o2 = buildInnings(config, opponent.name(), oppSecond, oppSecondWickets,
                opponent.members(), team, rng, null, false, fourthBalls);

        if (agg != null) {
            for (InningsCard innings : List.of(o1, o2)) {
                for (BowlingLine line : innings.bowling()) {
                    agg.computeIfAbsent(line.name(), k -> new int[3])[1] += line.wickets();
                }
            }
        }

        String topScorer = List.of(t1, t2).stream().flatMap(i -> i.batting().stream())
                .max(Comparator.comparingInt(BattingLine::runs)).map(BattingLine::name).orElse("");
        String bestBowler = List.of(o1, o2).stream().flatMap(i -> i.bowling().stream())
                .max(Comparator.comparingInt(BowlingLine::wickets)).map(BowlingLine::name).orElse("");
        String pom = won ? (!bestBowler.isBlank() ? bestBowler : topScorer)
                : o1.batting().stream().max(Comparator.comparingInt(BattingLine::runs))
                        .map(BattingLine::name).orElse(topScorer);

        return new MatchScorecard(
                stage + "-" + matchNumber, stage, matchNumber,
                opponent.name(), opponent.label(), won, drawn, margin, tossWinner, tossDecision, pom, topScorer, bestBowler,
                List.of(t1, o1, t2, o2), t1, o1
        );
    }

    private MatchScorecard simulateTestMatchChasing(
            ModeConfig config, String stage, int matchNumber,
            List<SquadMember> team, Squad opponent, Random rng, Map<String, int[]> agg,
            boolean won, boolean drawn, double teamBat, double teamBowl, double oppBat, double oppBowl,
            String tossWinner, String tossDecision
    ) {
        int oppFirst = testInningsTotal(oppBat, teamBowl, rng);
        int teamFirst = testInningsTotal(teamBat, oppBowl, rng);
        int oppSecond = testInningsTotal(oppBat, teamBowl, rng);
        int target = Math.max(1, oppFirst + oppSecond - teamFirst + 1);
        int teamSecond;
        int teamSecondWickets;
        String margin;

        if (won) {
            teamSecond = target;
            teamSecondWickets = 2 + rng.nextInt(7);
            margin = "Won by " + (10 - teamSecondWickets) + " wickets";
        } else if (!drawn) {
            teamSecond = Math.max(40, target - 1 - (15 + rng.nextInt(90)));
            teamSecondWickets = 10;
            margin = "Lost by " + Math.max(1, target - 1 - teamSecond) + " runs";
        } else {
            teamSecond = 0;
            teamSecondWickets = 3 + rng.nextInt(7);
            margin = "Match drawn";
        }

        int oppFirstWickets = 8 + rng.nextInt(3);
        int teamFirstWickets = 8 + rng.nextInt(3);
        int oppSecondWickets = won || drawn ? 7 + rng.nextInt(4) : 10;
        int firstBalls = testInningsBalls(oppFirst, oppFirstWickets, rng);
        int secondBalls = testInningsBalls(teamFirst, teamFirstWickets, rng);
        int thirdBalls = testInningsBalls(oppSecond, oppSecondWickets, rng);
        int remainingBalls = Math.max(6, 2700 - firstBalls - secondBalls - thirdBalls);
        int requiredFourthBalls = drawn ? remainingBalls : testInningsBalls(teamSecond, teamSecondWickets, rng);
        int fourthBalls = Math.min(requiredFourthBalls, remainingBalls);
        if (drawn || requiredFourthBalls > remainingBalls) {
            drawn = true;
            won = false;
            double fourthRunRate = 2.5 + rng.nextDouble() * 1.1;
            teamSecond = Math.max(20, Math.min(target - 1,
                    (int) Math.round(fourthBalls / 6.0 * fourthRunRate)));
            teamSecondWickets = Math.min(9, Math.max(3, teamSecondWickets));
            margin = "Match drawn";
        }

        InningsCard o1 = buildInnings(config, opponent.name(), oppFirst, oppFirstWickets,
                opponent.members(), team, rng, null, false, firstBalls);
        InningsCard t1 = buildInnings(config, TEAM_NAME, teamFirst, teamFirstWickets,
                team, opponent.members(), rng, agg, true, secondBalls);
        InningsCard o2 = buildInnings(config, opponent.name(), oppSecond, oppSecondWickets,
                opponent.members(), team, rng, null, false, thirdBalls);
        InningsCard t2 = buildInnings(config, TEAM_NAME, teamSecond, teamSecondWickets,
                team, opponent.members(), rng, agg, true, fourthBalls);

        if (agg != null) {
            for (InningsCard innings : List.of(o1, o2)) {
                for (BowlingLine line : innings.bowling()) {
                    agg.computeIfAbsent(line.name(), key -> new int[3])[1] += line.wickets();
                }
            }
        }

        String topScorer = List.of(t1, t2).stream().flatMap(innings -> innings.batting().stream())
                .max(Comparator.comparingInt(BattingLine::runs)).map(BattingLine::name).orElse("");
        String bestBowler = List.of(o1, o2).stream().flatMap(innings -> innings.bowling().stream())
                .max(Comparator.comparingInt(BowlingLine::wickets)).map(BowlingLine::name).orElse("");
        String pom = won ? (!topScorer.isBlank() ? topScorer : bestBowler)
                : o1.batting().stream().max(Comparator.comparingInt(BattingLine::runs))
                        .map(BattingLine::name).orElse(topScorer);

        return new MatchScorecard(
                stage + "-" + matchNumber, stage, matchNumber,
                opponent.name(), opponent.label(), won, drawn, margin, tossWinner, tossDecision,
                pom, topScorer, bestBowler, List.of(o1, t1, o2, t2), t1, o1
        );
    }

    private int testInningsTotal(double batting, double bowling, Random rng) {
        double total = 315 + (batting - 0.5) * 190 - (bowling - 0.5) * 160
                + (rng.nextDouble() - 0.5) * 170;
        return Math.max(110, Math.min(600, (int) Math.round(total)));
    }

    private int testInningsBalls(int runs, int wickets, Random rng) {
        double runRate = 2.7 + rng.nextDouble() * 1.25;
        double overs = runs / runRate;
        if (wickets < 10) overs *= 0.88;
        return (int) Math.round(Math.max(32, Math.min(125, overs)) * 6);
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

    private int limitedOversBalls(ModeConfig config, int runs, int wickets) {
        int cap = config.oversPerInnings() * 6;
        if (wickets < 10) return cap;
        double expectedRunRate = "ODI".equals(config.format()) ? 5.4 : 8.2;
        return Math.max(36, Math.min(cap, (int) Math.round(runs / expectedRunRate * 6)));
    }

    private int chaseBalls(ModeConfig config, int target, int wickets, Random rng) {
        int cap = config.oversPerInnings() * 6;
        double expectedRate = "ODI".equals(config.format()) ? 5.0 + rng.nextDouble() * 1.8
                : 7.3 + rng.nextDouble() * 2.8;
        int required = (int) Math.ceil(target / expectedRate * 6.0);
        // A successful chase ends on the winning delivery, never after the innings limit.
        return Math.max(12, Math.min(cap - 1, required + Math.max(0, wickets - 3) * 2));
    }

    private InningsCard buildInnings(
            ModeConfig config, String teamName, int totalRuns, int wicketsLost,
            List<SquadMember> batting, List<SquadMember> bowlingOpponents,
            Random rng, Map<String, int[]> agg, boolean aggregate
    ) {
        return buildInnings(config, teamName, totalRuns, wicketsLost, batting, bowlingOpponents,
                rng, agg, aggregate, config.oversPerInnings() * 6);
    }

    private InningsCard buildInnings(
            ModeConfig config, String teamName, int totalRuns, int wicketsLost,
            List<SquadMember> batting, List<SquadMember> bowlingOpponents,
            Random rng, Map<String, int[]> agg, boolean aggregate, int inningsBalls
    ) {
        List<SquadMember> order = new ArrayList<>(batting);
        order.sort(Comparator.comparingDouble(SquadMember::battingScore).reversed());

        List<BattingLine> battingLines = distributeBatting(config, order, totalRuns, wicketsLost,
                inningsBalls, rng, agg, aggregate);
        List<BowlingLine> bowlingLines = distributeBowling(config, bowlingOpponents, wicketsLost,
                totalRuns, inningsBalls, rng);

        return new InningsCard(teamName, totalRuns, wicketsLost, cricketOvers(inningsBalls), battingLines, bowlingLines);
    }

    private List<BattingLine> distributeBatting(
            ModeConfig config, List<SquadMember> order, int totalRuns, int wicketsLost, int inningsBalls,
            Random rng, Map<String, int[]> agg, boolean aggregate
    ) {
        int n = order.size();
        // Cricket-legal innings shape: exactly `wicketsLost` batters are dismissed; the pair left
        // at the crease are not out (just 1 if the side is bowled out); everyone after them
        // did not bat. So the number of batters who actually batted is:
        int battedCount = wicketsLost >= 9 ? n : Math.min(n, wicketsLost + 2);
        int notOutCount = battedCount - wicketsLost; // 1 (all out) or 2 (pair at crease)

        // Weight only the batters who actually batted.
        double[] weights = new double[battedCount];
        double sum = 0;
        for (int i = 0; i < battedCount; i++) {
            double positionFactor = Math.max(0.2, 1.0 - i * 0.08); // top order weighted higher
            double w = (0.2 + order.get(i).battingScore()) * positionFactor * (0.7 + rng.nextDouble() * 0.6);
            weights[i] = w;
            sum += w;
        }

        List<BattingLine> batted = new ArrayList<>();
        List<BattingLine> didNotBat = new ArrayList<>();
        int[] runsByBatter = new int[battedCount];
        int runsAllocated = 0;
        for (int i = 0; i < battedCount; i++) {
            int runs = sum == 0 ? 0 : (int) Math.round(totalRuns * weights[i] / sum);
            if (i == battedCount - 1) runs = Math.max(0, totalRuns - runsAllocated);
            runsByBatter[i] = runs;
            runsAllocated += runs;
        }

        double[] ballWeights = new double[battedCount];
        double ballWeightSum = 0;
        for (int i = 0; i < battedCount; i++) {
            double runsPerBall = "TEST".equals(config.format())
                    ? 0.38 + order.get(i).battingScore() * 0.42
                    : 0.9 + order.get(i).battingScore() * 0.7;
            ballWeights[i] = Math.max(1.0, runsByBatter[i] / Math.max(0.25, runsPerBall));
            ballWeightSum += ballWeights[i];
        }

        int ballsAllocated = 0;
        for (int i = 0; i < n; i++) {
            String name = order.get(i).name();
            if (i >= battedCount) {
                didNotBat.add(new BattingLine(name, 0, 0, false, false, "did not bat"));
                continue;
            }
            int runs = runsByBatter[i];
            int balls = i == battedCount - 1
                    ? Math.max(1, inningsBalls - ballsAllocated)
                    : Math.max(1, (int) Math.round(inningsBalls * ballWeights[i] / ballWeightSum));
            int ballsLeftForOthers = battedCount - i - 1;
            balls = Math.min(balls, inningsBalls - ballsAllocated - ballsLeftForOthers);
            ballsAllocated += balls;
            // The last `notOutCount` batters in the order are the not-out pair at the crease.
            boolean out = i < (battedCount - notOutCount);
            batted.add(new BattingLine(name, runs, balls, out, true, out ? "out" : "not out"));

            if (aggregate && agg != null) {
                agg.computeIfAbsent(name, k -> new int[3])[0] += runs;
            }
        }

        batted.sort(Comparator.comparingInt(BattingLine::runs).reversed());
        List<BattingLine> lines = new ArrayList<>(batted);
        lines.addAll(didNotBat);
        return lines;
    }

    private List<BowlingLine> distributeBowling(
            ModeConfig config, List<SquadMember> bowlers, int wicketsToTake,
            int runsConceded, int inningsBalls, Random rng
    ) {
        List<SquadMember> order = new ArrayList<>(bowlers);
        order.sort(Comparator.comparingDouble(SquadMember::bowlingScore).reversed());
        int used = Math.min(5, order.size());
        if (used == 0) {
            return List.of();
        }

        // Distribute wickets weighted by bowling score.
        double sum = 0;
        double[] weights = new double[used];
        for (int i = 0; i < used; i++) {
            weights[i] = 0.2 + order.get(i).bowlingScore() + rng.nextDouble() * 0.4;
            sum += weights[i];
        }

        int[] ballsByBowler = allocateBowlingBalls(config, inningsBalls, weights, rng);
        int[] wicketsByBowler = allocateExact(wicketsToTake, weights);
        double[] runWeights = new double[used];
        for (int i = 0; i < used; i++) {
            // Better bowlers tend to concede fewer runs per ball, while still allowing match variance.
            runWeights[i] = ballsByBowler[i] * (1.35 - Math.min(0.9, order.get(i).bowlingScore()))
                    * (0.8 + rng.nextDouble() * 0.4);
        }
        int[] runsByBowler = allocateExact(runsConceded, runWeights);

        List<BowlingLine> lines = new ArrayList<>();
        for (int i = 0; i < used; i++) {
            lines.add(new BowlingLine(order.get(i).name(), cricketOvers(ballsByBowler[i]),
                    runsByBowler[i], wicketsByBowler[i]));
        }
        return lines;
    }

    /**
     * Allocates the innings over by over. This guarantees that every bowler except, at most, the
     * bowler delivering the unfinished final over has a whole-over figure. T20/ODI spell limits
     * are also enforced (4 and 10 overs respectively); Tests have no per-bowler limit.
     */
    int[] allocateBowlingBalls(ModeConfig config, int inningsBalls, double[] weights, Random rng) {
        int used = weights.length;
        int[] allocation = new int[used];
        int completedOvers = inningsBalls / 6;
        int finalBalls = inningsBalls % 6;
        int maxOvers = "TEST".equals(config.format()) ? Integer.MAX_VALUE : config.oversPerInnings() / 5;
        int previous = -1;

        for (int over = 0; over < completedOvers; over++) {
            int selected = weightedEligibleBowler(weights, allocation, maxOvers, previous, rng);
            allocation[selected] += 6;
            previous = selected;
        }

        if (finalBalls > 0) {
            int selected = weightedEligibleBowler(weights, allocation, maxOvers, previous, rng);
            allocation[selected] += finalBalls;
        }
        return allocation;
    }

    private int weightedEligibleBowler(
            double[] weights, int[] allocation, int maxOvers, int previous, Random rng
    ) {
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            if (i != previous && allocation[i] / 6 < maxOvers) total += Math.max(0.01, weights[i]);
        }
        // A one-bowler attack is not expected, but this fallback keeps the allocator total-safe.
        if (total == 0) {
            for (int i = 0; i < weights.length; i++) {
                if (allocation[i] / 6 < maxOvers) total += Math.max(0.01, weights[i]);
            }
            previous = -1;
        }

        double pick = rng.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            if (i == previous || allocation[i] / 6 >= maxOvers) continue;
            pick -= Math.max(0.01, weights[i]);
            if (pick <= 0) return i;
        }
        for (int i = 0; i < weights.length; i++) {
            if (i != previous && allocation[i] / 6 < maxOvers) return i;
        }
        throw new IllegalStateException("No legal bowler available for the next over");
    }

    private int[] allocateExact(int total, double[] weights) {
        int[] allocation = new int[weights.length];
        double weightTotal = 0;
        for (double weight : weights) weightTotal += Math.max(0, weight);
        if (total <= 0 || weights.length == 0) return allocation;
        if (weightTotal == 0) {
            allocation[0] = total;
            return allocation;
        }

        double[] remainders = new double[weights.length];
        int allocated = 0;
        for (int i = 0; i < weights.length; i++) {
            double exact = total * Math.max(0, weights[i]) / weightTotal;
            allocation[i] = (int) Math.floor(exact);
            remainders[i] = exact - allocation[i];
            allocated += allocation[i];
        }
        while (allocated < total) {
            int next = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[next]) next = i;
            }
            allocation[next]++;
            remainders[next] = -1;
            allocated++;
        }
        return allocation;
    }

    private double cricketOvers(int balls) {
        return (balls / 6) + (balls % 6) / 10.0;
    }

    // ---------------------------------------------------------------------
    // Standings / awards / helpers
    // ---------------------------------------------------------------------

    private List<StandingRow> buildStandings(
            ModeConfig config, List<Squad> opponents, int wins, int draws, int losses, double nrr, long seed
    ) {
        List<StandingRow> rows = new ArrayList<>();
        rows.add(new StandingRow(TEAM_NAME, config.leagueMatches(), wins, draws, losses, wins * 2 + draws, nrr));

        Random standRng = new Random(seed * 131 + 17);
        double avgField = opponents.stream().mapToInt(Squad::strength).average().orElse(70);
        Map<String, Squad> uniqueOpponents = new LinkedHashMap<>();
        for (Squad opponent : opponents) uniqueOpponents.putIfAbsent(opponent.label(), opponent);
        for (Squad opp : uniqueOpponents.values()) {
            double q = 1.0 / (1.0 + Math.exp(-((opp.strength() - avgField)) / 8.0));
            int oppWins = 0;
            int oppDraws = 0;
            for (int m = 0; m < config.leagueMatches(); m++) {
                double roll = standRng.nextDouble();
                if ("TEST".equals(config.format()) && roll < 0.18) {
                    oppDraws++;
                } else if (roll < ("TEST".equals(config.format()) ? 0.18 + q * 0.82 : q)) {
                    oppWins++;
                }
            }
            int oppLosses = config.leagueMatches() - oppWins - oppDraws;
            double oppNrr = round((oppWins - oppLosses) / (double) config.leagueMatches() * 0.2);
            rows.add(new StandingRow(opp.label(), config.leagueMatches(), oppWins, oppDraws, oppLosses,
                    oppWins * 2 + oppDraws, oppNrr));
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

    /**
     * Builds a real seeded national-team bracket from the league table. In a four-team playoff,
     * 1 plays 4 and 2 plays 3; the final opponent is the simulated winner of the other semi-final.
     * In the WTC's two-team playoff, first and second meet directly in the final.
     */
    private List<Squad> seededKnockoutOpponents(
            ModeConfig config, List<StandingRow> standings, List<Squad> opponents,
            int userSeed, Random rng
    ) {
        Map<String, Squad> byLabel = new HashMap<>();
        for (Squad opponent : opponents) {
            byLabel.put(opponent.label(), opponent);
        }

        if (config.playoffTeams() == 2) {
            int opponentSeed = userSeed == 1 ? 2 : 1;
            Squad finalist = squadAtSeed(standings, byLabel, opponentSeed);
            return finalist == null ? List.of() : List.of(finalist);
        }

        int semiOpponentSeed = 5 - userSeed;
        Squad semiOpponent = squadAtSeed(standings, byLabel, semiOpponentSeed);
        int firstOtherSeed = (userSeed == 1 || userSeed == 4) ? 2 : 1;
        int secondOtherSeed = 5 - firstOtherSeed;
        Squad firstOther = squadAtSeed(standings, byLabel, firstOtherSeed);
        Squad secondOther = squadAtSeed(standings, byLabel, secondOtherSeed);
        Squad projectedFinalist = neutralWinner(firstOther, secondOther, rng);

        List<Squad> bracket = new ArrayList<>();
        if (semiOpponent != null) bracket.add(semiOpponent);
        if (projectedFinalist != null) bracket.add(projectedFinalist);
        return bracket;
    }

    private Squad squadAtSeed(List<StandingRow> standings, Map<String, Squad> byLabel, int seed) {
        if (seed < 1 || seed > standings.size()) {
            return null;
        }
        return byLabel.get(standings.get(seed - 1).team());
    }

    private Squad neutralWinner(Squad first, Squad second, Random rng) {
        if (first == null) return second;
        if (second == null) return first;
        double probability = 1.0 / (1.0 + Math.exp(-(first.strength() - second.strength()) / 8.0));
        return rng.nextDouble() < probability ? first : second;
    }

    private record PlayoffRun(List<MatchScorecard> matches, int wins, int losses, boolean champion) {}

    /** IPL Page playoff: Q1 gives the top two a second chance; third and fourth play Eliminator. */
    private PlayoffRun simulateIplPlayoffs(
            ModeConfig config, List<StandingRow> standings, List<Squad> opponents, int userSeed,
            List<SquadMember> members, double teamStrength, boolean hardMode, Random rng,
            Map<String, int[]> agg
    ) {
        Map<String, Squad> byLabel = new HashMap<>();
        for (Squad opponent : opponents) byLabel.put(opponent.label(), opponent);
        Squad first = squadAtSeed(standings, byLabel, 1);
        Squad second = squadAtSeed(standings, byLabel, 2);
        Squad third = squadAtSeed(standings, byLabel, 3);
        Squad fourth = squadAtSeed(standings, byLabel, 4);

        List<MatchScorecard> cards = new ArrayList<>();
        int wins = 0;
        int losses = 0;
        int matchNo = config.leagueMatches();

        if (userSeed <= 2) {
            Squad qualifierOneOpponent = userSeed == 1 ? second : first;
            MatchScorecard q1 = simulateMatch(config, "Qualifier 1", ++matchNo, members, teamStrength,
                    qualifierOneOpponent, hardMode, rng, agg);
            cards.add(q1);
            if (q1.won()) {
                wins++;
                Squad eliminatorWinner = neutralWinner(third, fourth, rng);
                Squad qualifierTwoWinner = neutralWinner(qualifierOneOpponent, eliminatorWinner, rng);
                MatchScorecard finalCard = simulateMatch(config, "Final", ++matchNo, members, teamStrength,
                        qualifierTwoWinner, hardMode, rng, agg);
                cards.add(finalCard);
                if (finalCard.won()) return new PlayoffRun(cards, wins + 1, losses, true);
                return new PlayoffRun(cards, wins, losses + 1, false);
            }

            losses++;
            Squad eliminatorWinner = neutralWinner(third, fourth, rng);
            MatchScorecard q2 = simulateMatch(config, "Qualifier 2", ++matchNo, members, teamStrength,
                    eliminatorWinner, hardMode, rng, agg);
            cards.add(q2);
            if (!q2.won()) return new PlayoffRun(cards, wins, losses + 1, false);
            wins++;
            MatchScorecard finalCard = simulateMatch(config, "Final", ++matchNo, members, teamStrength,
                    qualifierOneOpponent, hardMode, rng, agg);
            cards.add(finalCard);
            if (finalCard.won()) return new PlayoffRun(cards, wins + 1, losses, true);
            return new PlayoffRun(cards, wins, losses + 1, false);
        }

        Squad eliminatorOpponent = userSeed == 3 ? fourth : third;
        MatchScorecard eliminator = simulateMatch(config, "Eliminator", ++matchNo, members, teamStrength,
                eliminatorOpponent, hardMode, rng, agg);
        cards.add(eliminator);
        if (!eliminator.won()) return new PlayoffRun(cards, wins, losses + 1, false);
        wins++;

        Squad qualifierOneWinner = neutralWinner(first, second, rng);
        Squad qualifierOneLoser = qualifierOneWinner == first ? second : first;
        MatchScorecard q2 = simulateMatch(config, "Qualifier 2", ++matchNo, members, teamStrength,
                qualifierOneLoser, hardMode, rng, agg);
        cards.add(q2);
        if (!q2.won()) return new PlayoffRun(cards, wins, losses + 1, false);
        wins++;

        MatchScorecard finalCard = simulateMatch(config, "Final", ++matchNo, members, teamStrength,
                qualifierOneWinner, hardMode, rng, agg);
        cards.add(finalCard);
        if (finalCard.won()) return new PlayoffRun(cards, wins + 1, losses, true);
        return new PlayoffRun(cards, wins, losses + 1, false);
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

    private String buildSummary(String stage, int wins, int draws, int losses, boolean perfect, ModeConfig config, String opponentType) {
        StringBuilder sb = new StringBuilder();
        if (perfect) {
            sb.append("PERFECT RUN! Undefeated ").append(wins).append("-0 ");
        } else {
            sb.append(stage).append(" \u2014 ").append(wins).append("W ");
            if (draws > 0) sb.append(draws).append("D ");
            sb.append(losses).append("L. ");
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
