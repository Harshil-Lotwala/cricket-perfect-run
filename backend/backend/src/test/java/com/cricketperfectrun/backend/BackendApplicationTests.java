package com.cricketperfectrun.backend;

import com.cricketperfectrun.backend.model.SimulationModels.Squad;
import com.cricketperfectrun.backend.service.OpponentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private OpponentService opponentService;

	@Test
	void contextLoads() {
	}

	@Test
	void selectedEditionControlsEveryHistoricalOpponentAcrossAllFormats() {
		Map<String, Integer> editions = Map.of(
				"ipl", 2022,
				"odi-world-cup", 2023,
				"t20-world-cup", 2022,
				"wtc", 2022);
		for (Map.Entry<String, Integer> edition : editions.entrySet()) {
			String mode = edition.getKey();
			int year = edition.getValue();
			List<Squad> opponents = opponentService.selectOpponents(
					mode, OpponentService.HISTORICAL, 14, 101L, year);
			assertTrue(!opponents.isEmpty(), mode + " should have a " + year + " opponent field");
			assertTrue(opponents.stream().allMatch(squad -> squad.label().startsWith(year + " ")),
					mode + " must use only the selected " + year + " edition");
		}
	}

	@Test
	void repeatedFixturesStayInsideTheSelectedEdition() {
		List<Squad> schedule = opponentService.selectOpponents(
				"ipl", OpponentService.HISTORICAL, 14, 505L, 2022);
		assertEquals(14, schedule.size());
		assertTrue(schedule.stream().allMatch(squad -> squad.label().startsWith("2022 ")));
		Set<String> identities = schedule.stream().map(Squad::label)
				.collect(java.util.stream.Collectors.toSet());
		assertTrue(identities.size() < schedule.size(),
				"A 14-match IPL schedule should repeat teams from the same edition");
	}

}
