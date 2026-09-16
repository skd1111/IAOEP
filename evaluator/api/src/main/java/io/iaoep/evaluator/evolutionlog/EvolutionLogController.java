package io.iaoep.evaluator.evolutionlog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/evolution-logs")
@RequiredArgsConstructor
public class EvolutionLogController {

    private final EvolutionLogService service;

    @GetMapping
    public List<EvolutionLog> list(@PathVariable UUID projectId) {
        return service.listByProject(projectId);
    }

    @GetMapping("/{logId}")
    public EvolutionLog get(@PathVariable UUID projectId, @PathVariable UUID logId) {
        return service.get(logId);
    }
}
