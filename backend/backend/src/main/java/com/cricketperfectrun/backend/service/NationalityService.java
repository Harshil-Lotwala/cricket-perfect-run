package com.cricketperfectrun.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Derives each player's nationality purely from the imported international datasets.
 *
 * <p>International Cricsheet files key their rosters by national team name (e.g. "India",
 * "Australia"). By scanning every ODI / T20I / Test match we can map a player to the national
 * team they most frequently represented. A player whose most-represented country is not India is
 * treated as overseas for IPL purposes. Players who never appear in any international match are
 * assumed Indian (uncapped IPL players are overwhelmingly domestic Indians). No nationality is
 * hardcoded anywhere.
 */
@Service
public class NationalityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NationalityService.class);

    private static final String INDIA = "India";

    // International modes whose rosters are keyed by national team.
    private static final String[] INTERNATIONAL_MODES = {"odi-world-cup", "t20-world-cup", "wtc"};

    private final CricsheetParserService parserService;
    private final ObjectMapper mapper = new ObjectMapper();

    // Lazily computed: player name -> resolved country.
    private final AtomicReference<Map<String, String>> countryByPlayer = new AtomicReference<>();

    public NationalityService(CricsheetParserService parserService) {
        this.parserService = parserService;
    }

    public String country(String playerName) {
        return ensureLoaded().getOrDefault(playerName, INDIA);
    }

    public boolean isOverseas(String playerName) {
        String country = ensureLoaded().get(playerName);
        // Unknown players default to Indian (domestic) and are therefore not overseas.
        return country != null && !INDIA.equalsIgnoreCase(country);
    }

    public boolean hasInternationalRecord(String playerName) {
        return ensureLoaded().containsKey(playerName);
    }

    private Map<String, String> ensureLoaded() {
        Map<String, String> existing = countryByPlayer.get();
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (countryByPlayer.get() == null) {
                countryByPlayer.set(buildCountryMap());
            }
        }

        return countryByPlayer.get();
    }

    private Map<String, String> buildCountryMap() {
        // player -> (country -> appearances)
        Map<String, Map<String, Integer>> counts = new HashMap<>();

        for (String mode : INTERNATIONAL_MODES) {
            Path folder;
            try {
                folder = parserService.modeFolder(mode);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping nationality scan for mode {}: {}", mode, e.getMessage());
                continue;
            }

            Path archive = folder.resolveSibling(folder.getFileName() + ".zip");
            if (Files.isRegularFile(archive)) {
                scanArchive(archive, counts);
            } else try (Stream<Path> files = Files.list(folder)) {
                files.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> scanFile(path, counts));
            } catch (Exception e) {
                LOGGER.warn("Failed scanning nationality data in {}", folder, e);
            }
        }

        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : counts.entrySet()) {
            String best = null;
            int bestCount = -1;
            for (Map.Entry<String, Integer> teamCount : entry.getValue().entrySet()) {
                if (teamCount.getValue() > bestCount) {
                    bestCount = teamCount.getValue();
                    best = teamCount.getKey();
                }
            }
            if (best != null) {
                resolved.put(entry.getKey(), best);
            }
        }

        LOGGER.info("Resolved nationality for {} players from international datasets", resolved.size());
        return resolved;
    }

    private void scanFile(Path path, Map<String, Map<String, Integer>> counts) {
        // Stop after the small info.players object instead of parsing every ball in the match.
        try (InputStream input = Files.newInputStream(path)) {
            scanStream(input, counts);
        } catch (Exception e) {
            // Ignore unreadable files; nationality is best-effort.
        }
    }

    private void scanArchive(Path archive, Map<String, Map<String, Integer>> counts) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    scanStream(input, counts);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed scanning nationality archive {}", archive, e);
        }
    }

    private void scanStream(InputStream input, Map<String, Map<String, Integer>> counts) {
        try (JsonParser parser = mapper.getFactory().createParser(input)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "players".equals(parser.currentName())) {
                    parser.nextToken();
                    JsonNode players = mapper.readTree(parser);
                    if (!players.isObject()) {
                        return;
                    }

                    Iterator<String> teams = players.fieldNames();
                    while (teams.hasNext()) {
                        String team = teams.next();
                        for (JsonNode playerNode : players.path(team)) {
                            String name = playerNode.asText("");
                            if (name.isBlank()) {
                                continue;
                            }
                            counts.computeIfAbsent(name, ignored -> new HashMap<>())
                                    .merge(team, 1, Integer::sum);
                        }
                    }
                    return;
                }
            }
        } catch (Exception e) {
            // Nationality is best-effort; ignore an unreadable match entry.
        }
    }
}
