package com.enjoytrip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prometheus 지표 노출 범위: 수집에 필요한 지표만 열리고 다른 관리 엔드포인트는 닫혀 있어야 합니다. */
@SpringBootTest(properties = {"app.seed-demo=false", "app.tour-source=api", "app.tour-key=", "app.kakao-key=", "DEPLOY_COLOR=blue"})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class MetricsEndpointTest extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void prometheusExposesHttpJvmAndPoolMetricsWithColorTag() throws Exception {
        mvc.perform(get("/api/posts").param("size", "1")).andExpect(status().isOk());
        var body = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(body)
            .contains("http_server_requests_seconds_bucket")
            .contains("uri=\"/api/posts\"")
            .contains("application=\"enjoytrip\"")
            .contains("color=\"blue\"")
            .contains("jvm_memory_used_bytes")
            .contains("hikaricp_connections_active");
        // tomcat_threads_* 는 실제 Tomcat 이 뜰 때만 나오므로 MockMvc 테스트에서는 확인하지 않습니다.
    }

    @Test void otherManagementEndpointsStayClosed() throws Exception {
        for (var path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics"})
            assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isIn(404);
    }
}
