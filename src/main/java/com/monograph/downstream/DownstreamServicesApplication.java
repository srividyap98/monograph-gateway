package com.monograph.downstream;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Simulated backend REST services (Customer, Account, Financial) that the gateway calls over HTTP.
 * In production these are separate deployments owned by separate teams; here they share one process
 * on port 8090 under distinct path prefixes. The gateway cannot tell the difference: it only knows
 * one base URL per service.
 *
 * <p>Lives outside {@code com.monograph.gateway} so neither application component-scans the other.
 * Reads {@code downstream.yml} instead of {@code application.yml}, and disables GraphQL because the
 * gateway's schema files are on the same classpath.
 *
 * <pre>mvn spring-boot:run -Dspring-boot.run.main-class=com.monograph.downstream.DownstreamServicesApplication</pre>
 */
@SpringBootApplication(exclude = GraphQlAutoConfiguration.class)
public class DownstreamServicesApplication {

    public static void main(String[] args) {
        start(args);
    }

    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(DownstreamServicesApplication.class)
                .properties("spring.config.name=downstream")
                .run(args);
    }
}
