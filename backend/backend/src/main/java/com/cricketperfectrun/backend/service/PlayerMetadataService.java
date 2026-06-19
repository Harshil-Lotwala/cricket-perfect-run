package com.cricketperfectrun.backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves player metadata (nationality, keeper eligibility, role) from data sources only:
 * derived nationality ({@link NationalityService}), data-derived keeper signals (career stumpings),
 * and optional override files shipped as resources ({@code keeper_players.csv},
 * {@code player_metadata.csv}). No player lists are hardcoded in Java.
 */
@Service
public class PlayerMetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerMetadataService.class);

    private final NationalityService nationalityService;
    private final CricsheetParserService parserService;

    // Optional override sources (resource data files, not hardcoded Java collections).
    private final Set<String> keeperOverrides = new HashSet<>();
    private final Map<String, String> countryOverrides = new HashMap<>();
    private final Map<String, String> roleOverrides = new HashMap<>();

    // Cache of career keeper sets per mode (derived from stumpings).
    private final Map<String, Set<String>> careerKeepersByMode = new HashMap<>();

    public PlayerMetadataService(NationalityService nationalityService, CricsheetParserService parserService) {
        this.nationalityService = nationalityService;
        this.parserService = parserService;
    }

    @PostConstruct
    void loadOverrides() {
        loadKeeperCsv();
        loadMetadataCsv();
    }

    public String country(String name) {
        return countryOverrides.getOrDefault(name, nationalityService.country(name));
    }

    public boolean isOverseas(String name) {
        if (countryOverrides.containsKey(name)) {
            return !"India".equalsIgnoreCase(countryOverrides.get(name));
        }
        return nationalityService.isOverseas(name);
    }

    public boolean isKeeperEligible(String mode, String name) {
        if (keeperOverrides.contains(name)) {
            return true;
        }
        return careerKeepers(mode).contains(name);
    }

    /**
     * Layers keeper status onto a detected base role and applies any explicit role override.
     */
    public String resolveRole(String mode, String detectedRole, String name) {
        if (roleOverrides.containsKey(name)) {
            return roleOverrides.get(name);
        }
        if (RatingService.BAT.equals(detectedRole) && isKeeperEligible(mode, name)) {
            return RatingService.WK;
        }
        return detectedRole;
    }

    private Set<String> careerKeepers(String mode) {
        return careerKeepersByMode.computeIfAbsent(mode, parserService::getCareerKeepers);
    }

    private void loadKeeperCsv() {
        try (InputStream in = openResource("keeper_players.csv")) {
            if (in == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (!name.isBlank()) {
                        keeperOverrides.add(name);
                    }
                }
            }
            LOGGER.info("Loaded {} keeper override names", keeperOverrides.size());
        } catch (Exception e) {
            LOGGER.warn("Could not load keeper_players.csv", e);
        }
    }

    private void loadMetadataCsv() {
        try (InputStream in = openResource("player_metadata.csv")) {
            if (in == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String header = reader.readLine(); // name,country,roleOverride,captaincyImpact,keepingImpact
                if (header == null) {
                    return;
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split(",", -1);
                    String name = parts[0].trim();
                    if (name.isBlank()) {
                        continue;
                    }
                    if (parts.length > 1 && !parts[1].trim().isBlank()) {
                        countryOverrides.put(name, parts[1].trim());
                    }
                    if (parts.length > 2 && !parts[2].trim().isBlank()) {
                        roleOverrides.put(name, parts[2].trim().toUpperCase());
                    }
                }
            }
            LOGGER.info("Loaded metadata overrides: {} country, {} role", countryOverrides.size(), roleOverrides.size());
        } catch (Exception e) {
            LOGGER.warn("Could not load player_metadata.csv", e);
        }
    }

    private InputStream openResource(String name) {
        try {
            ClassPathResource resource = new ClassPathResource(name);
            return resource.exists() ? resource.getInputStream() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
