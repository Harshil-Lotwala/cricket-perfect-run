package com.cricketperfectrun.backend.controller;

import com.cricketperfectrun.backend.dto.SimulationRequest;
import com.cricketperfectrun.backend.model.SimulationModels.SeasonResult;
import com.cricketperfectrun.backend.service.SimulationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping({"/simulate/{mode}", "/play/{mode}"})
    public SeasonResult simulate(@PathVariable String mode, @RequestBody SimulationRequest request) {
        request.setMode(mode);
        return simulationService.simulate(request);
    }
}
