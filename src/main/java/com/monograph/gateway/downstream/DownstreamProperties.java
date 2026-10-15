package com.monograph.gateway.downstream;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the gateway finds each backend service, bound from {@code monograph.downstream.*}.
 * Only base URLs and timeouts live here; each {@code Http*Client} owns its own paths.
 */
@ConfigurationProperties("monograph.downstream")
public record DownstreamProperties(Service customer, Service account, Service financial) {

    public DownstreamProperties {
        Objects.requireNonNull(customer, "monograph.downstream.customer.base-url is required");
        Objects.requireNonNull(account, "monograph.downstream.account.base-url is required");
        Objects.requireNonNull(financial, "monograph.downstream.financial.base-url is required");
    }

    /**
     * @param connectTimeout optional; unset keeps the HTTP client's default
     * @param readTimeout optional; how long to wait for a response. Unset keeps the HTTP client's
     *     default, which for the JDK client is to wait indefinitely.
     */
    public record Service(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

        public Service {
            Objects.requireNonNull(baseUrl, "base-url is required");
            requirePositive(connectTimeout, "connect-timeout");
            requirePositive(readTimeout, "read-timeout");
        }

        private static void requirePositive(Duration timeout, String name) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException(name + " must be positive, got " + timeout);
            }
        }
    }
}
