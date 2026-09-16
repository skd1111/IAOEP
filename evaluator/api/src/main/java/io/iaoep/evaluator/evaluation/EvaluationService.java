package io.iaoep.evaluator.evaluation;

import io.iaoep.evaluator.evaluation.dto.CreateEvaluationRequest;
import io.iaoep.evaluator.evaluation.dto.EvaluationJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationJobRepository repo;
    private final EvaluationExecutor executor;

    @Transactional(readOnly = true)
    public List<EvaluationJobResponse> list(UUID projectId) {
        return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(EvaluationJobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationJobResponse get(UUID id) {
        return EvaluationJobResponse.from(
                repo.findById(id).orElseThrow(() -> new IllegalArgumentException("job not found: " + id)));
    }

    /**
     * 创建评测任务并异步触发执行.
     */
    @Transactional
    public EvaluationJobResponse create(UUID projectId, CreateEvaluationRequest req, String userId) {
        EvaluationJob job = EvaluationJob.builder()
                .projectId(projectId)
                .datasetId(req.getDatasetId())
                .agentVersion(req.getAgentVersion())
                .judgeConfig(req.getJudgeConfig())
                .status(EvaluationJob.Status.PENDING)
                .progress(0.0)
                .totalCases(0)
                .completedCases(0)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
        job = repo.save(job);

        // 触发异步执行 (立即返回 job, 后台跑)
        executor.executeAsync(job.getId());

        return EvaluationJobResponse.from(job);
    }

    /**
     * 取消任务 (只允许 PENDING → CANCELLED).
     */
    @Transactional
    public EvaluationJobResponse cancel(UUID id) {
        EvaluationJob job = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("job not found: " + id));
        if (job.getStatus() == EvaluationJob.Status.PENDING) {
            job.setStatus(EvaluationJob.Status.CANCELLED);
            job.setCompletedAt(Instant.now());
            repo.save(job);
        } else if (job.getStatus() == EvaluationJob.Status.RUNNING) {
            // Phase 2 简化: 不支持中途停止 (需要加 interrupt 机制)
            throw new IllegalStateException("cannot cancel running job, please wait or kill pod");
        }
        return EvaluationJobResponse.from(job);
    }
}
