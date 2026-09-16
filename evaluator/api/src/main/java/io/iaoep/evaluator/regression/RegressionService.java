package io.iaoep.evaluator.regression;

import io.iaoep.evaluator.evaluation.EvaluationJob;
import io.iaoep.evaluator.evaluation.EvaluationJobRepository;
import io.iaoep.evaluator.regression.dto.CreateRegressionRequest;
import io.iaoep.evaluator.regression.dto.RegressionReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegressionService {

    private final EvaluationJobRepository jobRepo;
    private final RegressionReportRepository reportRepo;

    @Transactional(readOnly = true)
    public List<RegressionReportResponse> list(UUID projectId) {
        return reportRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(RegressionReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RegressionReportResponse get(UUID id) {
        return RegressionReportResponse.from(
                reportRepo.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("report not found: " + id)));
    }

    /**
     * 对比两次评测任务的结果, 生成 regression report.
     *
     * <p>对比维度:
     * <ul>
     *   <li>{@code overall}: 加权总分差异</li>
     *   <li>{@code per_dimension}: 每个维度的差异 (从 result.dimensions 提取)</li>
     *   <li>{@code per_case}: 每个 case 的 pass/fail 变化 (新增失败 / 修复失败)</li>
     * </ul>
     *
     * <p>判定:
     * <ul>
     *   <li>任一维度下降 > {@code threshold} → {@code FAILED}</li>
     *   <li>否则 → {@code PASSED}</li>
     * </ul>
     */
    @Transactional
    public RegressionReportResponse compare(UUID projectId, CreateRegressionRequest req, String userId) {
        EvaluationJob baseline = jobRepo.findById(req.getBaselineJobId())
                .orElseThrow(() -> new IllegalArgumentException("baseline job not found: " + req.getBaselineJobId()));
        EvaluationJob candidate = jobRepo.findById(req.getCandidateJobId())
                .orElseThrow(() -> new IllegalArgumentException("candidate job not found: " + req.getCandidateJobId()));

        if (baseline.getStatus() != EvaluationJob.Status.COMPLETED
                || candidate.getStatus() != EvaluationJob.Status.COMPLETED) {
            throw new IllegalStateException(
                    "both jobs must be COMPLETED, baseline=" + baseline.getStatus()
                            + " candidate=" + candidate.getStatus());
        }

        double threshold = req.getThreshold() != null ? req.getThreshold() : 0.05;
        double overallDelta = candidate.getResult() == null || baseline.getResult() == null ? 0.0
                : asDouble(candidate.getResult().get("overall")) - asDouble(baseline.getResult().get("overall"));

        // 维度对比 (假设 baseline.result / candidate.result 包含 dimensions 数组)
        Map<String, Object> baselineResult = baseline.getResult() != null ? baseline.getResult() : Map.of();
        Map<String, Object> candidateResult = candidate.getResult() != null ? candidate.getResult() : Map.of();

        List<Map<String, Object>> baselineDims = asDimList(baselineResult.get("judge_dimensions"));
        List<Map<String, Object>> candidateDims = asDimList(candidateResult.get("judge_dimensions"));

        Map<String, Double> baselineByName = dimToMap(baselineDims);
        Map<String, Double> candidateByName = dimToMap(candidateDims);

        List<Map<String, Object>> dimDeltas = new ArrayList<>();
        boolean blocked = false;
        Set<String> allDims = new TreeSet<>();
        allDims.addAll(baselineByName.keySet());
        allDims.addAll(candidateByName.keySet());

        for (String name : allDims) {
            double b = baselineByName.getOrDefault(name, 0.0);
            double c = candidateByName.getOrDefault(name, 0.0);
            double delta = c - b;
            if (delta < -threshold) blocked = true;
            dimDeltas.add(Map.of(
                    "name", name,
                    "baseline", b,
                    "candidate", c,
                    "delta", delta,
                    "blocked", delta < -threshold
            ));
        }

        // Case 级对比 (新增失败 / 修复失败)
        Map<String, Boolean> baselineCases = casePassedMap(asCaseList(baselineResult.get("case_results")));
        Map<String, Boolean> candidateCases = casePassedMap(asCaseList(candidateResult.get("case_results")));

        List<String> newFailures = new ArrayList<>();
        List<String> newFixes = new ArrayList<>();
        for (String key : candidateCases.keySet()) {
            boolean cPass = candidateCases.get(key);
            Boolean bPass = baselineCases.get(key);
            if (bPass == null) continue;     // 新增 case
            if (cPass != bPass) {
                if (cPass) newFixes.add(key);
                else newFailures.add(key);
            }
        }

        // 阻断判定 (含 overallDelta)
        if (overallDelta < -threshold) blocked = true;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall_delta", overallDelta);
        result.put("threshold", threshold);
        result.put("baseline_overall", asDouble(baselineResult.get("overall")));
        result.put("candidate_overall", asDouble(candidateResult.get("overall")));
        result.put("dimension_deltas", dimDeltas);
        result.put("new_failures", newFailures);
        result.put("new_fixes", newFixes);
        result.put("blocked_dimensions", dimDeltas.stream()
                .filter(d -> Boolean.TRUE.equals(d.get("blocked")))
                .map(d -> d.get("name"))
                .toList());

        RegressionReport.Status status = blocked
                ? RegressionReport.Status.FAILED
                : RegressionReport.Status.PASSED;

        RegressionReport report = RegressionReport.builder()
                .projectId(projectId)
                .baselineJobId(baseline.getId())
                .candidateJobId(candidate.getId())
                .baselineVersion(baseline.getAgentVersion())
                .candidateVersion(candidate.getAgentVersion())
                .status(status)
                .overallDelta(overallDelta)
                .threshold(threshold)
                .result(result)
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
        report = reportRepo.save(report);

        log.info("Regression report: baseline={} candidate={} delta={} status={}",
                baseline.getAgentVersion(), candidate.getAgentVersion(), overallDelta, status);

        return RegressionReportResponse.from(report);
    }

    // ============================================================
    // 工具
    // ============================================================

    private double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asDimList(Object o) {
        if (o instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asCaseList(Object o) {
        if (o instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Map<String, Double> dimToMap(List<Map<String, Object>> dims) {
        Map<String, Double> map = new HashMap<>();
        for (Map<String, Object> d : dims) {
            Object name = d.get("name");
            Object score = d.get("score");
            if (name != null && score instanceof Number n) {
                map.put(name.toString(), n.doubleValue());
            }
        }
        return map;
    }

    private Map<String, Boolean> casePassedMap(List<Map<String, Object>> cases) {
        Map<String, Boolean> map = new HashMap<>();
        for (Map<String, Object> c : cases) {
            Object input = c.get("input");
            if (input != null) {
                map.put(input.toString(), Boolean.TRUE.equals(c.get("passed")));
            }
        }
        return map;
    }
}
