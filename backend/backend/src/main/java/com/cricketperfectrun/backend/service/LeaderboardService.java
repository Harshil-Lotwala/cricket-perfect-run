package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.dto.LeaderboardSubmission;
import com.cricketperfectrun.backend.dto.PlayerDTO;
import com.cricketperfectrun.backend.dto.SimulationRequest;
import com.cricketperfectrun.backend.model.LeaderboardEntry;
import com.cricketperfectrun.backend.model.LeaderboardEntry.LeaderboardPlayer;
import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.SimulationModels.SeasonResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LeaderboardService {

    private static final int MAX_ENTRIES_PER_MODE = 100;
    private static final Set<String> MODES = Set.of("ipl", "odi-world-cup", "t20-world-cup", "wtc");

    private final ObjectMapper objectMapper;
    private final SimulationService simulationService;
    private final CricsheetParserService parserService;
    private final PlayerFactory playerFactory;
    private final Path storagePath;
    private final List<LeaderboardEntry> entries = new ArrayList<>();

    public LeaderboardService(
            ObjectMapper objectMapper,
            SimulationService simulationService,
            CricsheetParserService parserService,
            PlayerFactory playerFactory,
            @Value("${leaderboard.file:../../data/leaderboard.json}") String storageFile
    ) {
        this.objectMapper = objectMapper;
        this.simulationService = simulationService;
        this.parserService = parserService;
        this.playerFactory = playerFactory;
        this.storagePath = Path.of(storageFile).toAbsolutePath().normalize();
        load();
    }

    public synchronized List<LeaderboardEntry> list(String mode) {
        String canonical = canonicalMode(mode);
        return entries.stream()
                .filter(entry -> canonical.equals(entry.mode()))
                .filter(LeaderboardEntry::hardMode)
                .filter(entry -> entry.losses() == 0)
                .sorted(ranking())
                .limit(MAX_ENTRIES_PER_MODE)
                .toList();
    }

    public synchronized LeaderboardEntry submit(LeaderboardSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("A leaderboard submission is required.");
        }
        String mode = canonicalMode(submission.mode());
        String displayName = cleanDisplayName(submission.displayName());
        if (!submission.publishConsent()) {
            throw new IllegalArgumentException("Publishing requires explicit user approval.");
        }
        validateTeam(mode, submission.team(), submission.captainId(), submission.keeperId());

        SimulationRequest request = new SimulationRequest();
        request.setMode(mode);
        request.setTeam(submission.team());
        request.setCaptainId(submission.captainId());
        request.setKeeperId(submission.keeperId());
        request.setOpponentType(submission.opponentType());
        request.setHardMode(submission.hardMode());
        request.setSeed(submission.seed());
        request.setMaxSeason(submission.maxSeason());
        SeasonResult verified = simulationService.simulate(request);

        if (!verified.hardMode()) {
            throw new IllegalArgumentException("Only Hard Mode runs are eligible for the leaderboard.");
        }
        if (verified.losses() != 0) {
            throw new IllegalArgumentException("Only unbeaten runs can enter the leaderboard.");
        }

        if (verified.wins() != submission.claimedWins()
                || verified.draws() != submission.claimedDraws()
                || verified.losses() != submission.claimedLosses()) {
            throw new IllegalArgumentException("Submitted record does not match the verified simulation.");
        }

        PlayerDTO captain = findPlayer(submission.team(), submission.captainId());
        PlayerDTO keeper = findPlayer(submission.team(), submission.keeperId());
        List<LeaderboardPlayer> team = submission.team().stream()
                .map(player -> new LeaderboardPlayer(player.getName(), player.getRole(), player.getRating(),
                        player.getYear(), player.getTeam()))
                .toList();
        LeaderboardEntry entry = new LeaderboardEntry(
                UUID.randomUUID().toString().substring(0, 8), displayName, mode,
                verified.opponentType(), verified.hardMode(), verified.seed(), verified.wins(),
                verified.draws(), verified.losses(), verified.perfectTarget(), verified.champion(),
                verified.perfect(), verified.ratingBreakdown().overall(), captain.getName(), keeper.getName(),
                team, Instant.now()
        );

        // The same player/run can be shared once; resubmitting replaces its earlier copy.
        entries.removeIf(existing -> existing.mode().equals(mode)
                && existing.seed() == entry.seed()
                && existing.displayName().equalsIgnoreCase(displayName));
        entries.add(entry);
        trimMode(mode);
        persist();
        return entry;
    }

    private void validateTeam(String mode, List<PlayerDTO> team, Integer captainId, Integer keeperId) {
        if (team == null || team.size() != 11) {
            throw new IllegalArgumentException("A leaderboard XI must contain exactly 11 players.");
        }
        Set<String> names = new HashSet<>();
        for (PlayerDTO submitted : team) {
            if (submitted == null || submitted.getName() == null || submitted.getName().isBlank()
                    || !names.add(submitted.getName().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The XI contains a missing or duplicate player.");
            }
            Player verified = parserService.getPlayerStatsByYearAndTeam(mode, submitted.getYear(), submitted.getTeam())
                    .stream().map(playerFactory::fromStats)
                    .filter(player -> player.id() == submitted.getId() && player.name().equals(submitted.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Player data could not be verified: " + submitted.getName()));
            if (verified.rating() != submitted.getRating() || verified.keeperEligible() != submitted.isKeeperEligible()) {
                throw new IllegalArgumentException("Player data was modified: " + submitted.getName());
            }
        }
        PlayerDTO captain = findPlayer(team, captainId);
        PlayerDTO keeper = findPlayer(team, keeperId);
        if (!keeper.isKeeperEligible()) {
            throw new IllegalArgumentException("Selected keeper is not wicketkeeper-eligible.");
        }
        if (captain == null) {
            throw new IllegalArgumentException("Select a valid captain.");
        }
        if ("ipl".equals(mode) && team.stream().filter(PlayerDTO::isOverseas).count() > 4) {
            throw new IllegalArgumentException("IPL leaderboard teams may contain at most four overseas players.");
        }
    }

    private PlayerDTO findPlayer(List<PlayerDTO> team, Integer id) {
        if (id == null) throw new IllegalArgumentException("Captain and keeper are required.");
        return team.stream().filter(player -> player.getId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Captain or keeper is not part of the XI."));
    }

    private Comparator<LeaderboardEntry> ranking() {
        return Comparator.comparing(LeaderboardEntry::perfect, Comparator.reverseOrder())
                .thenComparing(LeaderboardEntry::champion, Comparator.reverseOrder())
                .thenComparing(LeaderboardEntry::wins, Comparator.reverseOrder())
                .thenComparing(LeaderboardEntry::draws, Comparator.reverseOrder())
                .thenComparingInt(LeaderboardEntry::losses)
                .thenComparing(LeaderboardEntry::overallRating, Comparator.reverseOrder())
                .thenComparing(LeaderboardEntry::submittedAt);
    }

    private void trimMode(String mode) {
        List<LeaderboardEntry> ranked = list(mode);
        Set<String> retained = ranked.stream().map(LeaderboardEntry::id).collect(java.util.stream.Collectors.toSet());
        entries.removeIf(entry -> entry.mode().equals(mode) && !retained.contains(entry.id()));
    }

    private String canonicalMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(value)) throw new IllegalArgumentException("Unknown leaderboard mode.");
        return value;
    }

    private String cleanDisplayName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (value.length() < 2 || value.length() > 24 || !value.matches("[A-Za-z0-9 _.-]+")) {
            throw new IllegalArgumentException("Display name must be 2-24 letters, numbers, spaces, dots, dashes or underscores.");
        }
        return value;
    }

    private void load() {
        if (!Files.isRegularFile(storagePath)) return;
        try {
            entries.addAll(objectMapper.readValue(storagePath.toFile(), new TypeReference<List<LeaderboardEntry>>() {}));
        } catch (IOException ignored) {
            // A malformed optional leaderboard file should not prevent the game from starting.
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storagePath.getParent());
            Path temporaryFile = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), entries);
            try {
                Files.move(temporaryFile, storagePath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save the leaderboard.", error);
        }
    }
}
