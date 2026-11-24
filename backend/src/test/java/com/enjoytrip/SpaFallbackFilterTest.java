package com.enjoytrip;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class SpaFallbackFilterTest {
    @Test void screenRoutesFallBackToIndexButApiAndFilesDoNot() {
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/")).isTrue();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/posts/12")).isTrue();
        assertThat(SpaFallbackFilter.isSpaRoute("HEAD", "/map")).isTrue();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/api/posts")).isFalse();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/api")).isFalse();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/actuator/health")).isFalse();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/assets/index-abc.js")).isFalse();
        assertThat(SpaFallbackFilter.isSpaRoute("GET", "/favicon.ico")).isFalse();
        assertThat(SpaFallbackFilter.isSpaRoute("POST", "/posts/12")).isFalse();
    }

    @Test void forwardsScreenRouteWithNoCacheAndLeavesApiAlone() throws Exception {
        var filter = new SpaFallbackFilter();
        var request = new MockHttpServletRequest("GET", "/posts/12");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getForwardedUrl()).isEqualTo("/index.html");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");

        var api = new MockHttpServletRequest("GET", "/api/posts");
        var apiResponse = new MockHttpServletResponse();
        filter.doFilter(api, apiResponse, new MockFilterChain());
        assertThat(apiResponse.getForwardedUrl()).isNull();

        var asset = new MockHttpServletRequest("GET", "/assets/app-1a2b.js");
        var assetResponse = new MockHttpServletResponse();
        filter.doFilter(asset, assetResponse, new MockFilterChain());
        assertThat(assetResponse.getForwardedUrl()).isNull();
        assertThat(assetResponse.getHeader("Cache-Control")).contains("immutable");
    }
}
