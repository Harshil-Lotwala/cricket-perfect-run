package com.cricketperfectrun.backend;

import com.cricketperfectrun.backend.model.SimulationModels.Squad;
import com.cricketperfectrun.backend.service.OpponentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private OpponentService opponentService;

	@Test
	void contextLoads() {
	}

	@Test
	void historicalOpponentVersionsVaryAcrossRunSeeds() {
		List<String> first = labelsForSeed(101L);
		boolean foundDifferentVersion = List.of(202L, 303L, 404L, 505L).stream()
				.map(this::labelsForSeed)
				.anyMatch(labels -> !labels.equals(first));

		assertTrue(foundDifferentVersion,
				"At least one opponent season should change when a new run seed is generated");
	}

	private List<String> labelsForSeed(long seed) {
		return opponentService.selectOpponents("ipl", OpponentService.HISTORICAL, 14, seed, 2026)
				.stream()
				.map(Squad::label)
				.toList();
	}

}
