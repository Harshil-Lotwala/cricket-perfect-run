package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import com.cricketperfectrun.backend.model.TeamSeasonCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Service
public class CricsheetParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CricsheetParserService.class);

    private static final String MODE_IPL = "ipl";
    private static final String MODE_ODI = "odi-world-cup";
    private static final String MODE_T20 = "t20-world-cup";
    private static final String MODE_WTC = "wtc";

    private static final Set<String> BOWLER_WICKET_EXCLUSIONS = Set.of(
            "run out",
            "retired hurt",
            "retired out",
            "obstructing the field",
            "timed out"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    private final ConcurrentMap<String, List<PlayerSeasonStats>> modeStatsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<TeamSeasonCard>> modeCardsCache = new ConcurrentHashMap<>();

    public List<PlayerSeasonStats> getPlayerStats(String mode) {
        String canonicalMode = canonicalMode(mode);
        return modeStatsCache.computeIfAbsent(canonicalMode, this::loadStatsForMode);
    }

    public List<PlayerSeasonStats> getPlayerStatsByYearAndTeam(String mode, int year, String team) {
        String normalizedTeam = normalizeTeamName(team);

        return getPlayerStats(mode)
                .stream()
                .filter(stats -> stats.year() == year)
                .filter(stats -> sameTeam(stats.team(), normalizedTeam))
                .sorted(Comparator.comparingInt(PlayerSeasonStats::matches).reversed())
                .toList();
    }

    public List<TeamSeasonCard> getTeamSeasonCards(String mode) {
        String canonicalMode = canonicalMode(mode);

        return modeCardsCache.computeIfAbsent(canonicalMode, ignored -> {
            Map<String, Integer> playersByYearTeam = new HashMap<>();

            for (PlayerSeasonStats stats : getPlayerStats(canonicalMode)) {
                String key = stats.year() + "|" + stats.team();
                playersByYearTeam.merge(key, 1, Integer::sum);
            }

            List<TeamSeasonCard> cards = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : playersByYearTeam.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                int year = Integer.parseInt(parts[0]);
                String team = parts[1];
                cards.add(new TeamSeasonCard(canonicalMode, year, team, entry.getValue()));
            }

            cards.sort(
                    Comparator.comparingInt(TeamSeasonCard::year)
                            .thenComparing(TeamSeasonCard::team)
            );

            return List.copyOf(cards);
        });
    }

    public Map<Integer, List<String>> getTeamsByYear(String mode) {
        Map<Integer, Set<String>> teamsByYear = new HashMap<>();

        for (TeamSeasonCard card : getTeamSeasonCards(mode)) {
            teamsByYear.computeIfAbsent(card.year(), ignored -> new HashSet<>()).add(card.team());
        }

        Map<Integer, List<String>> ordered = new LinkedHashMap<>();
        teamsByYear.keySet().stream().sorted().forEach(year -> {
            List<String> teams = teamsByYear.get(year).stream().sorted().toList();
            ordered.put(year, teams);
        });

        return ordered;
    }

    public List<Integer> getAvailableYears(String mode) {
        return getTeamsByYear(mode).keySet().stream().sorted().toList();
    }

    public Set<String> getAvailableModes() {
        return Set.of(MODE_IPL, MODE_ODI, MODE_T20, MODE_WTC);
    }

    /**
     * Resolves the on-disk Cricsheet folder for a mode. Exposed so other services
     * (e.g. nationality scanning) can reuse the same path-resolution logic.
     */
    public Path modeFolder(String mode) {
        return resolveModeFolder(mode);
    }

    /**
     * Set of player names who recorded at least one stumping across the entire
     * dataset for a mode. Used as a data-derived signal of keeper eligibility.
     */
    public Set<String> getCareerKeepers(String mode) {
        Set<String> keepers = new HashSet<>();
        for (PlayerSeasonStats stats : getPlayerStats(mode)) {
            if (stats.stumpings() > 0) {
                keepers.add(stats.playerName());
            }
        }
        return keepers;
    }

    /**
     * Career totals (matches, wins, player-of-match) aggregated by player name for a mode.
     * Used by leadership scoring without any hardcoded name lists.
     */
    public Map<String, int[]> getCareerLeadership(String mode) {
        Map<String, int[]> career = new HashMap<>();
        for (PlayerSeasonStats stats : getPlayerStats(mode)) {
            int[] agg = career.computeIfAbsent(stats.playerName(), ignored -> new int[3]);
            agg[0] += stats.matches();
            agg[1] += stats.wins();
            agg[2] += stats.playerOfMatchAwards();
        }
        return career;
    }

    public void refreshCaches() {
        modeStatsCache.clear();
        modeCardsCache.clear();
    }

    private List<PlayerSeasonStats> loadStatsForMode(String mode) {
        Path folder = resolveModeFolder(mode);
        Map<String, MutableStats> statsByPlayerSeason = new HashMap<>();

        try (Stream<Path> fileStream = Files.list(folder)) {
            fileStream
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> parseMatch(path, mode, statsByPlayerSeason));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list Cricsheet files for mode: " + mode, e);
        }

        return statsByPlayerSeason.values()
                .stream()
                .map(MutableStats::toRecord)
                .sorted(
                        Comparator.comparingInt(PlayerSeasonStats::year)
                                .thenComparing(PlayerSeasonStats::team)
                                .thenComparing(PlayerSeasonStats::playerName)
                )
                .toList();
    }

    private void parseMatch(Path path, String mode, Map<String, MutableStats> statsMap) {
        try {
            JsonNode root = mapper.readTree(path.toFile());
            JsonNode info = root.path("info");

            String date = info.path("dates").isArray() && info.path("dates").size() > 0
                    ? info.path("dates").get(0).asText()
                    : "";
            if (date.length() < 4) {
                return;
            }

            int year = Integer.parseInt(date.substring(0, 4));
            String winner = normalizeTeamName(info.path("outcome").path("winner").asText(""));
            Set<String> pomPlayers = readPlayerOfMatch(info.path("player_of_match"));

            Map<String, String> playerToTeam = buildPlayerTeamMap(info.path("players"));
            Map<String, Set<String>> activePlayersByTeam = new HashMap<>();

            JsonNode innings = root.path("innings");
            for (JsonNode inning : innings) {
                String battingTeam = normalizeTeamName(inning.path("team").asText());

                for (JsonNode over : inning.path("overs")) {
                    for (JsonNode delivery : over.path("deliveries")) {
                        String batter = delivery.path("batter").asText();
                        String bowler = delivery.path("bowler").asText();
                        String nonStriker = delivery.path("non_striker").asText();

                        String bowlingTeam = normalizeTeamName(playerToTeam.getOrDefault(bowler, ""));

                        markActive(activePlayersByTeam, battingTeam, batter);
                        markActive(activePlayersByTeam, battingTeam, nonStriker);
                        markActive(activePlayersByTeam, bowlingTeam, bowler);

                        MutableStats batterStats = getStats(statsMap, mode, year, battingTeam, batter);
                        MutableStats bowlerStats = getStats(statsMap, mode, year, bowlingTeam, bowler);

                        int batterRuns = delivery.path("runs").path("batter").asInt(0);
                        int totalRuns = delivery.path("runs").path("total").asInt(0);

                        batterStats.runs += batterRuns;
                        if (!hasExtra(delivery, "wides")) {
                            batterStats.ballsFaced++;
                        }

                        if (isLegalBall(delivery)) {
                            bowlerStats.ballsBowled++;
                        }

                        bowlerStats.runsConceded += bowlerRuns(delivery, totalRuns);

                        JsonNode wickets = delivery.path("wickets");
                        if (!wickets.isArray()) {
                            continue;
                        }

                        for (JsonNode wicket : wickets) {
                            String kind = wicket.path("kind").asText("");
                            String playerOut = wicket.path("player_out").asText("");

                            if (!playerOut.isBlank() && isCountedDismissal(kind)) {
                                markActive(activePlayersByTeam, battingTeam, playerOut);
                                MutableStats outStats = getStats(statsMap, mode, year, battingTeam, playerOut);
                                outStats.dismissals++;
                            }

                            if (isBowlerWicket(kind)) {
                                bowlerStats.wickets++;
                            }

                            JsonNode fielders = wicket.path("fielders");
                            if (!fielders.isArray()) {
                                continue;
                            }

                            for (JsonNode fielderNode : fielders) {
                                String fielder = fielderNode.path("name").asText("");
                                if (fielder.isBlank()) {
                                    continue;
                                }

                                markActive(activePlayersByTeam, bowlingTeam, fielder);
                                MutableStats fielderStats = getStats(statsMap, mode, year, bowlingTeam, fielder);

                                if ("stumped".equalsIgnoreCase(kind)) {
                                    fielderStats.stumpings++;
                                } else if (kind.toLowerCase(Locale.ROOT).contains("caught")) {
                                    fielderStats.catches++;
                                }
                            }
                        }
                    }
                }
            }

            activePlayersByTeam.forEach((team, players) -> {
                for (String player : players) {
                    MutableStats stats = getStats(statsMap, mode, year, team, player);
                    stats.matches++;

                    if (!winner.isBlank()) {
                        if (sameTeam(team, winner)) {
                            stats.wins++;
                        } else {
                            stats.losses++;
                        }
                    }
                }
            });

            for (String pom : pomPlayers) {
                String pomTeam = normalizeTeamName(playerToTeam.getOrDefault(pom, ""));
                if (!pomTeam.isBlank()) {
                    getStats(statsMap, mode, year, pomTeam, pom).playerOfMatchAwards++;
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Skipping unreadable Cricsheet file: {}", path, exception);
        }
    }

    private Set<String> readPlayerOfMatch(JsonNode playerOfMatchNode) {
        Set<String> players = new HashSet<>();

        if (!playerOfMatchNode.isArray()) {
            return players;
        }

        for (JsonNode playerNode : playerOfMatchNode) {
            String name = playerNode.asText("");
            if (!name.isBlank()) {
                players.add(name);
            }
        }

        return players;
    }

    private Map<String, String> buildPlayerTeamMap(JsonNode playersNode) {
        Map<String, String> playerToTeam = new HashMap<>();

        playersNode.fieldNames().forEachRemaining(team -> {
            String normalizedTeam = normalizeTeamName(team);
            for (JsonNode playerNode : playersNode.path(team)) {
                String player = playerNode.asText("");
                if (!player.isBlank()) {
                    playerToTeam.put(player, normalizedTeam);
                }
            }
        });

        return playerToTeam;
    }

    private MutableStats getStats(
            Map<String, MutableStats> statsMap,
            String mode,
            int year,
            String team,
            String player
    ) {
        String normalizedTeam = normalizeTeamName(team);
        String key = mode + "|" + year + "|" + normalizedTeam + "|" + player;

        return statsMap.computeIfAbsent(
                key,
                ignored -> new MutableStats(mode, year, normalizedTeam, player)
        );
    }

    private void markActive(Map<String, Set<String>> activePlayersByTeam, String team, String player) {
        if (team == null || team.isBlank() || player == null || player.isBlank()) {
            return;
        }

        activePlayersByTeam
                .computeIfAbsent(normalizeTeamName(team), ignored -> new HashSet<>())
                .add(player);
    }

    private boolean hasExtra(JsonNode delivery, String extraName) {
        return delivery.path("extras").has(extraName);
    }

    private boolean isLegalBall(JsonNode delivery) {
        return !hasExtra(delivery, "wides") && !hasExtra(delivery, "noballs");
    }

    private int bowlerRuns(JsonNode delivery, int totalRuns) {
        int byes = delivery.path("extras").path("byes").asInt(0);
        int legByes = delivery.path("extras").path("legbyes").asInt(0);
        int penalty = delivery.path("extras").path("penalty").asInt(0);

        return totalRuns - byes - legByes - penalty;
    }

    private boolean isCountedDismissal(String kind) {
        return !kind.equalsIgnoreCase("retired hurt");
    }

    private boolean isBowlerWicket(String kind) {
        return !BOWLER_WICKET_EXCLUSIONS.contains(kind.toLowerCase(Locale.ROOT));
    }

    private Path resolveModeFolder(String mode) {
        String folderName = switch (canonicalMode(mode)) {
            case MODE_IPL -> "ipl_json";
            case MODE_ODI -> "odis_json";
            case MODE_T20 -> "t20s_json";
            case MODE_WTC -> "tests_json";
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };

        List<Path> candidates = List.of(
                Paths.get("../../data/cricsheet", folderName),
                Paths.get("../data/cricsheet", folderName),
                Paths.get("data/cricsheet", folderName),
                Paths.get("cricsheet", folderName)
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize().toAbsolutePath();
            if (Files.exists(normalized) && Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        throw new IllegalStateException("Could not locate Cricsheet folder for mode " + mode);
    }

    private String canonicalMode(String mode) {
        if (mode == null) {
            return MODE_IPL;
        }

        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "ipl" -> MODE_IPL;
            case "odi-world-cup", "odi", "world-cup-odi" -> MODE_ODI;
            case "t20-world-cup", "t20", "world-cup-t20" -> MODE_T20;
            case "wtc", "test", "tests", "world-test-championship" -> MODE_WTC;
            default -> throw new IllegalArgumentException("Invalid mode: " + mode);
        };
    }

    private boolean sameTeam(String a, String b) {
        return normalizeTeamName(a).equalsIgnoreCase(normalizeTeamName(b));
    }

    private String normalizeTeamName(String team) {
        if (team == null) {
            return "";
        }

        return switch (team.trim()) {
            case "Kings XI Punjab" -> "Punjab Kings";
            case "Delhi Daredevils" -> "Delhi Capitals";
            case "Royal Challengers Bangalore" -> "Royal Challengers Bengaluru";
            case "Rising Pune Supergiants" -> "Rising Pune Supergiant";
            default -> team.trim();
        };
    }

    private static class MutableStats {
        final String mode;
        final int year;
        final String team;
        final String playerName;

        int matches;
        int wins;
        int losses;
        int runs;
        int ballsFaced;
        int dismissals;
        int wickets;
        int ballsBowled;
        int runsConceded;
        int catches;
        int stumpings;
        int playerOfMatchAwards;

        MutableStats(String mode, int year, String team, String playerName) {
            this.mode = mode;
            this.year = year;
            this.team = team;
            this.playerName = playerName;
        }

        PlayerSeasonStats toRecord() {
            return new PlayerSeasonStats(
                    mode,
                    year,
                    team,
                    playerName,
                    matches,
                    wins,
                    losses,
                    runs,
                    ballsFaced,
                    dismissals,
                    wickets,
                    ballsBowled,
                    runsConceded,
                    catches,
                    stumpings,
                    playerOfMatchAwards
            );
        }
    }
}