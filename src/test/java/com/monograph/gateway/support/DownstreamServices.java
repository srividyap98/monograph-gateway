package com.monograph.gateway.support;

import com.monograph.downstream.DownstreamServicesApplication;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Starts the simulated downstream services once per test JVM on a random port and points the
 * gateway at them. Use from a {@code @DynamicPropertySource} method:
 *
 * <pre>
 * &#64;DynamicPropertySource
 * static void downstream(DynamicPropertyRegistry registry) {
 *     DownstreamServices.register(registry);
 * }
 * </pre>
 *
 * Never stopped explicitly: Spring's shutdown hook closes it when the JVM exits, so gateway
 * contexts cached across test classes never point at a dead port.
 */
public final class DownstreamServices {

    private static ConfigurableApplicationContext context;

    private DownstreamServices() {
    }

    public static synchronized String baseUrl() {
        if (context == null) {
            context = DownstreamServicesApplication.start("--server.port=0");
        }
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("monograph.downstream.customer.base-url", () -> baseUrl() + "/customer-service");
        registry.add("monograph.downstream.account.base-url", () -> baseUrl() + "/account-service");
        registry.add("monograph.downstream.financial.base-url", () -> baseUrl() + "/financial-service");
    }
}
