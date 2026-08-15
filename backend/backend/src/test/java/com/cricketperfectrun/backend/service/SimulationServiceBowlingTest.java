package com.cricketperfectrun.backend.service;

import com.cricketperfectrun.backend.service.ModeConfigService.ModeConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceBowlingTest {

    private final SimulationService service = new SimulationService(null, null, null, null);

    @Test
    void testAllocationHasOnlyOnePossiblePartialOverAndReconcilesToInnings() {
        ModeConfig test = new ModeConfig("wtc", "TEST", 9, 1, 2, 90, 320);
        assertLegalAllocation(test, 88 * 6 + 1);
    }

    @Test
    void limitedOversAllocationRespectsBowlerCaps() {
        ModeConfig t20 = new ModeConfig("ipl", "T20", 14, 2, 4, 20, 165);
        int[] allocation = assertLegalAllocation(t20, 20 * 6);
        assertTrue(Arrays.stream(allocation).allMatch(balls -> balls <= 4 * 6));

        ModeConfig odi = new ModeConfig("odi-world-cup", "ODI", 9, 2, 4, 50, 270);
        allocation = assertLegalAllocation(odi, 50 * 6);
        assertTrue(Arrays.stream(allocation).allMatch(balls -> balls <= 10 * 6));
    }

    private int[] assertLegalAllocation(ModeConfig config, int inningsBalls) {
        int[] allocation = service.allocateBowlingBalls(
                config, inningsBalls, new double[]{1.0, 0.9, 0.8, 0.7, 0.6}, new Random(42)
        );
        assertEquals(inningsBalls, Arrays.stream(allocation).sum());
        assertTrue(Arrays.stream(allocation).filter(balls -> balls % 6 != 0).count() <= 1);
        return allocation;
    }
}
