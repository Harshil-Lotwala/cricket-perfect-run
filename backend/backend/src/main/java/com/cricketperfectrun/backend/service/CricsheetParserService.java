package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import com.cricketperfectrun.backend.model.TeamSeasonCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class CricsheetParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CricsheetParserService.class);

    private static final String MODE_IPL = "ipl";
    private static final String MODE_ODI = "odi-world-cup";
    private static final String MODE_T20 = "t20-world-cup";
    private static final String MODE_WTC = "wtc";
    private static final String CACHE_VERSION = "stats-v4";

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
    private final ConcurrentMap<String, Map<String, List<PlayerSeasonStats>>> teamStatsIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<Integer, List<String>>> teamsByYearCache = new ConcurrentHashMap<>();

    public List<PlayerSeasonStats> getPlayerStats(String mode) {
        String canonicalMode = canonicalMode(mode);
        return modeStatsCache.computeIfAbsent(canonicalMode, this::loadStatsForMode);
    }

    public List<PlayerSeasonStats> getPlayerStatsByYearAndTeam(String mode, int year, String team) {
        String canonicalMode = canonicalMode(mode);
        String normalizedTeam = normalizeTeamName(team);
        Map<String, List<PlayerSeasonStats>> index = teamStatsIndex.computeIfAbsent(
                canonicalMode,
                ignored -> buildTeamStatsIndex(canonicalMode)
        );
        return index.getOrDefault(year + "|" + normalizedTeam.toLowerCase(Locale.ROOT), List.of());
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
        String canonicalMode = canonicalMode(mode);
        return teamsByYearCache.computeIfAbsent(canonicalMode, ignored -> buildTeamsByYear(canonicalMode));
    }

    private Map<Integer, List<String>> buildTeamsByYear(String mode) {
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
        teamStatsIndex.clear();
        teamsByYearCache.clear();
    }

    private List<PlayerSeasonStats> loadStatsForMode(String mode) {
        Path folder = resolveModeFolder(mode);
        List<PlayerSeasonStats> cached = readDiskCache(mode, folder);
        if (cached != null) {
            return cached;
        }
        Map<String, MutableStats> statsByPlayerSeason = new HashMap<>();

        try {
            List<Map<String, MutableStats>> perMatchStats;
            Path archive = folder.resolveSibling(folder.getFileName() + ".zip");
            if (Files.isRegularFile(archive)) {
                perMatchStats = parseArchive(archive, mode);
            } else {
                try (Stream<Path> fileStream = Files.list(folder)) {
                    List<Path> matchFiles = fileStream
                            .filter(path -> path.toString().endsWith(".json"))
                            .sorted()
                            .toList();
                    perMatchStats = matchFiles.parallelStream()
                            .map(path -> {
                                Map<String, MutableStats> local = new HashMap<>();
                                parseMatch(path, mode, local);
                                return local;
                            })
                            .toList();
                }
            }

            for (Map<String, MutableStats> matchStats : perMatchStats) {
                matchStats.forEach((key, value) -> statsByPlayerSeason
                        .computeIfAbsent(key, ignored -> new MutableStats(
                                value.mode, value.year, value.team, value.playerName
                        ))
                        .merge(value));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list Cricsheet files for mode: " + mode, e);
        }

        List<PlayerSeasonStats> stats = statsByPlayerSeason.values()
                .stream()
                .map(MutableStats::toRecord)
                .sorted(
                        Comparator.comparingInt(PlayerSeasonStats::year)
                                .thenComparing(PlayerSeasonStats::team)
                                .thenComparing(PlayerSeasonStats::playerName)
                )
                .toList();
        writeDiskCache(mode, folder, stats);
        return stats;
    }

    private List<Map<String, MutableStats>> parseArchive(Path archive, String mode) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries()).stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".json"))
                    .toList();
            LOGGER.info("Cold-indexing {} from {} compressed match files", mode, entries.size());
            return entries.parallelStream().map(entry -> {
                Map<String, MutableStats> local = new HashMap<>();
                try (InputStream input = zip.getInputStream(entry)) {
                    parseMatch(input, archive + "!" + entry.getName(), mode, local);
                } catch (IOException exception) {
                    LOGGER.warn("Skipping unreadable Cricsheet entry: {}", entry.getName(), exception);
                }
                return local;
            }).toList();
        }
    }

    private Map<String, List<PlayerSeasonStats>> buildTeamStatsIndex(String mode) {
        Map<String, List<PlayerSeasonStats>> grouped = new HashMap<>();
        for (PlayerSeasonStats stats : getPlayerStats(mode)) {
            String key = stats.year() + "|" + normalizeTeamName(stats.team()).toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(stats);
        }
        grouped.replaceAll((key, players) -> players.stream()
                .sorted(Comparator.comparingInt(PlayerSeasonStats::matches).reversed())
                .toList());
        return Map.copyOf(grouped);
    }

    private List<PlayerSeasonStats> readDiskCache(String mode, Path folder) {
        Path cacheFile = cacheFile(mode, folder);
        try {
            if (!Files.isRegularFile(cacheFile)) {
                return null;
            }
            CachedStats cached = mapper.readValue(cacheFile.toFile(), CachedStats.class);
            if (!fingerprint(folder).equals(cached.fingerprint())) {
                return null;
            }
            LOGGER.info("Loaded {} cached {} player-season records", cached.stats().size(), mode);
            return List.copyOf(cached.stats());
        } catch (Exception e) {
            LOGGER.warn("Ignoring unreadable stats cache for {}: {}", mode, e.getMessage());
            return null;
        }
    }

    private void writeDiskCache(String mode, Path folder, List<PlayerSeasonStats> stats) {
        Path cacheFile = cacheFile(mode, folder);
        Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(cacheFile.getParent());
            mapper.writeValue(temporary.toFile(), new CachedStats(fingerprint(folder), stats));
            Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Cached {} {} player-season records", stats.size(), mode);
        } catch (Exception e) {
            LOGGER.warn("Could not write stats cache for {}: {}", mode, e.getMessage());
        }
    }

    private Path cacheFile(String mode, Path folder) {
        return folder.getParent().resolve(".perfect-run-cache").resolve(CACHE_VERSION + "-" + mode + ".json");
    }

    private String fingerprint(Path folder) throws IOException {
        long count;
        try (Stream<Path> files = Files.list(folder)) {
            count = files.filter(p -> p.toString().endsWith(".json")).count();
        }
        // A directory's modification time changes when match files are added, removed or replaced.
        // Avoid stat-ing every file: on synced macOS folders that was slower than parsing the data.
        long directoryModified = Files.getLastModifiedTime(folder).toMillis();
        return CACHE_VERSION + ":" + count + ":" + directoryModified;
    }

    private record CachedStats(String fingerprint, List<PlayerSeasonStats> stats) {}

    private void parseMatch(Path path, String mode, Map<String, MutableStats> statsMap) {
        try (InputStream input = Files.newInputStream(path)) {
            parseMatch(input, path.toString(), mode, statsMap);
        } catch (Exception exception) {
            LOGGER.warn("Skipping unreadable Cricsheet file: {}", path, exception);
        }
    }

    private void parseMatch(InputStream input, String source, String mode, Map<String, MutableStats> statsMap) {
        try {
            JsonNode root = mapper.readTree(input);
            JsonNode info = root.path("info");

            String date = info.path("dates").isArray() && info.path("dates").size() > 0
                    ? info.path("dates").get(0).asText()
                    : "";
            if (date.length() < 4) {
                return;
            }

            int year = Integer.parseInt(date.substring(0, 4));
            if (!isEligibleMatch(mode, info, year)) {
                return;
            }
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
            LOGGER.warn("Skipping unreadable Cricsheet file: {}", source, exception);
        }
    }

    /**
     * Restricts which matches contribute to a mode. World Cup modes only include actual
     * men's World Cup tournament matches (so e.g. a T20 World Cup never shows 2006, when no
     * tournament was played); WTC includes men's Tests from the WTC era (2019+). IPL keeps all.
     */
    private boolean isEligibleMatch(String mode, JsonNode info, int year) {
        if (MODE_IPL.equals(mode)) {
            return true;
        }

        // International modes are men's only.
        String gender = info.path("gender").asText("male");
        if (!"male".equalsIgnoreCase(gender)) {
            return false;
        }

        String event = info.path("event").path("name").asText("").toLowerCase(Locale.ROOT);
        String matchType = info.path("match_type").asText("").toUpperCase(Locale.ROOT);
        boolean odiWorldCup = event.equals("icc world cup")
                || event.equals("world cup")
                || event.equals("icc cricket world cup")
                || event.equals("icc men's cricket world cup");
        boolean t20WorldCup = event.equals("icc world twenty20")
                || event.equals("icc men's t20 world cup")
                || event.equals("icc t20 world cup");

        return switch (mode) {
            case MODE_ODI -> "ODI".equals(matchType) && odiWorldCup;
            case MODE_T20 -> "T20".equals(matchType) && t20WorldCup;
            case MODE_WTC -> year >= 2019; // World Test Championship era
            default -> true;
        };
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

        void merge(MutableStats other) {
            matches += other.matches;
            wins += other.wins;
            losses += other.losses;
            runs += other.runs;
            ballsFaced += other.ballsFaced;
            dismissals += other.dismissals;
            wickets += other.wickets;
            ballsBowled += other.ballsBowled;
            runsConceded += other.runsConceded;
            catches += other.catches;
            stumpings += other.stumpings;
            playerOfMatchAwards += other.playerOfMatchAwards;
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
