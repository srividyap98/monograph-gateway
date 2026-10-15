package com.monograph.gateway.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Records every HTTP request the gateway sends downstream, as {@code "GET /path?query"}.
 * Boot applies {@link RestClientCustomizer} beans to the {@code RestClient.Builder} each
 * {@code Http*Client} is built from, so this sees all outbound traffic. Import with
 * {@code @Import(DownstreamCallRecorder.class)}; call {@link #reset()} before each test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DownstreamCallRecorder {

    private final List<String> calls = new CopyOnWriteArrayList<>();

    @Bean
    RestClientCustomizer recordDownstreamCalls() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String query = request.getURI().getRawQuery();
            calls.add(request.getMethod() + " " + request.getURI().getRawPath() + (query != null ? "?" + query : ""));
            return execution.execute(request, body);
        });
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
    }
}
