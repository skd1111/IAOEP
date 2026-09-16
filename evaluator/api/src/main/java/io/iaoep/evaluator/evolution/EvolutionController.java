package io.iaoep.evaluator.evolution;

import io.iaoep.evaluator.evolution.dto.EvolutionSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/suggestions")
@RequiredArgsConstructor
public class EvolutionController {

    private final EvolutionService service;

    @GetMapping
    public List<EvolutionSuggestionResponse> list(@PathVariable UUID projectId,
                                                   @RequestParam(required = false) String status) {
        List<EvolutionSuggestion> list = (status == null)
                ? service.list(projectId)
                : service.listByStatus(projectId, EvolutionSuggestion.Status.valueOf(status.toUpperCase()));
        return list.stream().map(EvolutionSuggestionResponse::from).toList();
    }

    @GetMapping("/{sid}")
    public EvolutionSuggestionResponse get(@PathVariable UUID projectId,
                                          @PathVariable UUID sid) {
        return EvolutionSuggestionResponse.from(service.get(sid));
    }

    @PostMapping("/{sid}/approve")
    public EvolutionSuggestionResponse approve(@PathVariable UUID projectId,
                                              @PathVariable UUID sid,
                                              @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return EvolutionSuggestionResponse.from(service.approve(sid, userId));
    }

    @PostMapping("/{sid}/reject")
    public EvolutionSuggestionResponse reject(@PathVariable UUID projectId,
                                             @PathVariable UUID sid,
                                             @RequestParam(required = false) String reason,
                                             @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return EvolutionSuggestionResponse.from(service.reject(sid, userId, reason));
    }
}
