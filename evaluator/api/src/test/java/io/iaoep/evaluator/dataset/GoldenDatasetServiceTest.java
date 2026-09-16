package io.iaoep.evaluator.dataset;

import io.iaoep.evaluator.dataset.dto.CreateDatasetRequest;
import io.iaoep.evaluator.dataset.dto.DatasetResponse;
import io.iaoep.evaluator.dataset.dto.UpdateDatasetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GoldenDatasetService 集成测试 (用 H2 in-memory).
 *
 * 注: 真正的 H2 不支持 JSONB, 这里仅验证非 JSONB 字段逻辑.
 * 完整测试需要 PostgreSQL testcontainer (Phase 2 后续 PR).
 */
@SpringBootTest
@ActiveProfiles("test")
class GoldenDatasetServiceTest {

    @Autowired
    private GoldenDatasetService service;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
    }

    @Test
    void createAndList() {
        DatasetResponse created = service.create(projectId, sampleRequest("test-ds", "v1"), "alice");
        assertNotNull(created.getId());
        assertEquals("test-ds", created.getName());
        assertEquals("v1", created.getVersion());
        assertEquals(2, created.getCaseCount());

        List<DatasetResponse> list = service.list(projectId);
        assertEquals(1, list.size());
        assertEquals(created.getId(), list.get(0).getId());
    }

    @Test
    void createDuplicateShouldFail() {
        service.create(projectId, sampleRequest("dup", "v1"), "alice");
        assertThrows(IllegalArgumentException.class,
                () -> service.create(projectId, sampleRequest("dup", "v1"), "bob"));
    }

    @Test
    void listVersions() {
        service.create(projectId, sampleRequest("multi", "v1"), "alice");
        service.create(projectId, sampleRequest("multi", "v2"), "alice");
        service.create(projectId, sampleRequest("multi", "v3"), "alice");

        List<DatasetResponse> versions = service.listVersions(projectId, "multi");
        assertEquals(3, versions.size());
        assertEquals("v3", versions.get(0).getVersion());  // 最新优先
    }

    @Test
    void updateActivateShouldDeactivateOthers() {
        DatasetResponse v1 = service.create(projectId, sampleRequest("act", "v1"), "alice");
        DatasetResponse v2 = service.create(projectId, sampleRequest("act", "v2"), "alice");

        service.update(v1.getId(), UpdateDatasetRequest.builder().activate(true).build());

        // v1 应该是 active=true, v2 应该是 active=false
        DatasetResponse v1Updated = service.get(v1.getId());
        DatasetResponse v2Updated = service.get(v2.getId());
        assertTrue(v1Updated.getIsActive());
        assertFalse(v2Updated.getIsActive());
    }

    @Test
    void deleteNotFoundShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> service.delete(UUID.randomUUID()));
    }

    private CreateDatasetRequest sampleRequest(String name, String version) {
        return CreateDatasetRequest.builder()
                .name(name)
                .version(version)
                .isActive(true)
                .cases(List.of(
                        Map.of("input", Map.of("query", "test1"), "rubric", "check answer"),
                        Map.of("input", Map.of("query", "test2"), "rubric", "check answer 2")
                ))
                .build();
    }
}
