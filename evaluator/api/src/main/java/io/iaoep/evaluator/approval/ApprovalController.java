package io.iaoep.evaluator.approval;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iaoep/projects/{projectId}/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService service;

    /**
     * 列出项目下所有审批.
     */
    @GetMapping
    public List<Approval> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    /**
     * 单签审批 (低/中风险) — POST body: {"comment": "..."}
     */
    @PostMapping("/{suggestionId}/approve")
    public Approval approve(@PathVariable UUID projectId,
                            @PathVariable UUID suggestionId,
                            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
                            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return service.approve(suggestionId, userId, comment);
    }

    @PostMapping("/{suggestionId}/reject")
    public Approval reject(@PathVariable UUID projectId,
                           @PathVariable UUID suggestionId,
                           @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
                           @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return service.reject(suggestionId, userId, comment);
    }

    /**
     * 高风险双签: 第 1 / 第 2 个签字都调这个端点.
     * 系统会自动检查签字次数和用户唯一性.
     */
    @PostMapping("/{suggestionId}/sign")
    public Approval sign(@PathVariable UUID projectId,
                         @PathVariable UUID suggestionId,
                         @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
                         @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return service.sign(suggestionId, userId, comment);
    }

    /**
     * 列出指定 suggestion 的所有审批记录 (查看双签进度).
     */
    @GetMapping("/{suggestionId}")
    public List<Approval> listBySuggestion(@PathVariable UUID projectId,
                                            @PathVariable UUID suggestionId) {
        return service.listBySuggestion(suggestionId);
    }
}
