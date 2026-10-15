package com.monograph.gateway.downstream;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

import com.monograph.gateway.error.DownstreamUnavailableException;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Shared plumbing for the {@code Http*Client} adapters. */
public final class DownstreamHttp {

    private DownstreamHttp() {
    }

    /**
     * A RestClient for one backend: base URL plus the service's timeouts, if configured.
     * Without timeouts the builder's request factory is left as Boot configured it.
     */
    public static RestClient restClient(RestClient.Builder builder, DownstreamProperties.Service service) {
        builder.baseUrl(service.baseUrl().toString());
        if (service.connectTimeout() != null || service.readTimeout() != null) {
            builder.requestFactory(ClientHttpRequestFactoryBuilder.detect()
                    .build(HttpClientSettings.defaults().withTimeouts(service.connectTimeout(), service.readTimeout())));
        }
        return builder.build();
    }

    /**
     * Runs one downstream call and turns every way it can fail into a
     * {@link DownstreamUnavailableException}: timeout, connection failure, non-2xx status, or a
     * body that cannot be read. Only for calls where no failure status has a domain meaning; the
     * Customer client keeps handling its own 404/409 instead.
     *
     * @param service human-readable name used in the GraphQL error message, e.g. "Financial service"
     */
    public static <T> T call(String service, Supplier<T> request) {
        try {
            return request.get();
        }
        catch (ResourceAccessException ex) {
            throw new DownstreamUnavailableException(service + (isTimeout(ex) ? " timed out" : " is unreachable"), ex);
        }
        catch (RestClientResponseException ex) {
            throw new DownstreamUnavailableException(
                    service + " responded with HTTP " + ex.getStatusCode().value(), ex);
        }
        catch (RestClientException ex) {
            throw new DownstreamUnavailableException(service + " returned an unreadable response", ex);
        }
    }

    /** JDK client: HttpTimeoutException (connect timeouts are a subclass); blocking clients: SocketTimeoutException. */
    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
