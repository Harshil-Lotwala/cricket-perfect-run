package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.model.Player;
import com.cricketperfectrun.backend.model.PlayerSeasonStats;
import com.cricketperfectrun.backend.model.SimulationModels.Squad;
import com.cricketperfectrun.backend.model.SimulationModels.SquadMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a "Legacy XI" for each franchise: the prime (peak-rated) season of every player who ever
 * represented that franchise, assembled into the strongest legal XI. Entirely data-derived from the
 * historical dataset \u2014 no manual lists, no hardcoded primes.
 */
@Service
public class LegacyXiService {

    private static final int OVERSEAS_LIMIT = 4;
    private static final int XI = 11;

    private final CricsheetParserService parserService;
    private final PlayerFactory playerFactory;
    private final SquadBuilder squadBuilder;
    private final StrengthCalculator strengthCalculator;

    private final Map<String, List<Squad>> cache = new ConcurrentHashMap<>();

    public LegacyXiService(
            CricsheetParserService parserService,
            PlayerFactory playerFactory,
            SquadBuilder squadBuilder,
            StrengthCalculator strengthCalculator
    ) {
        this.parserService = parserService;
        this.playerFactory = playerFactory;
        this.squadBuilder = squadBuilder;
        this.strengthCalculator = strengthCalculator;
    }

    public List<Squad> legacySquads(String mode) {
        return cache.computeIfAbsent(mode, this::buildAll);
    }

    private List<Squad> buildAll(String mode) {
        // franchise -> (playerName -> peak Player)
        Map<String, Map<String, Player>> peakByTeam = new HashMap<>();

        for (PlayerSeasonStats stats : parserService.getPlayerStats(mode)) {
            Player candidate = playerFactory.fromStats(stats);
            Map<String, Player> teamPeaks = peakByTeam.computeIfAbsent(stats.team(), ignored -> new HashMap<>());
            Player existing = teamPeaks.get(candidate.name());
            if (existing == null || candidate.rating() > existing.rating()) {
                teamPeaks.put(candidate.name(), candidate);
            }
        }

        List<Squad> squads = new ArrayList<>();
        for (Map.Entry<String, Map<String, Player>> entry : peakByTeam.entrySet()) {
            String team = entry.getKey();
            List<Player> primes = new ArrayList<>(entry.getValue().values());
            if (primes.size() < XI) {
                continue;
            }
            squads.add(assemble(team, primes));
        }

        squads.sort(Comparator.comparing(Squad::name));
        return List.copyOf(squads);
    }

    private Squad assemble(String team, List<Player> primes) {
        primes.sort(Comparator.comparingInt(Player::rating).reversed());

        List<Player> selected = new ArrayList<>();
        int overseas = 0;

        // Greedy by rating respecting the overseas cap.
        for (Player p : primes) {
            if (selected.size() >= XI) {
                break;
            }
            if (p.overseas() && overseas >= OVERSEAS_LIMIT) {
                continue;
            }
            selected.add(p);
            if (p.overseas()) {
                overseas++;
            }
        }

        ensureKeeper(selected, primes);

        List<SquadMember> members = selected.stream().map(squadBuilder::fromPlayer).toList();
        int strength = strengthCalculator.squadStrength(members);
        return new Squad(team, team + " Legacy XI", strength, members);
    }

    private void ensureKeeper(List<Player> selected, List<Player> primes) {
        boolean hasKeeper = selected.stream().anyMatch(Player::keeperEligible);
        if (hasKeeper) {
            return;
        }
        Player bestKeeper = primes.stream()
                .filter(Player::keeperEligible)
                .max(Comparator.comparingInt(Player::rating))
                .orElse(null);
        if (bestKeeper == null) {
            return;
        }
        // Replace the weakest non-keeper, respecting that swapping does not break the overseas cap.
        Player weakest = selected.stream()
                .filter(p -> !p.keeperEligible())
                .min(Comparator.comparingInt(Player::rating))
                .orElse(null);
        if (weakest != null) {
            long overseasAfter = selected.stream().filter(Player::overseas).count()
                    - (weakest.overseas() ? 1 : 0)
                    + (bestKeeper.overseas() ? 1 : 0);
            if (overseasAfter <= OVERSEAS_LIMIT) {
                selected.remove(weakest);
                selected.add(bestKeeper);
            }
        }
    }
}
