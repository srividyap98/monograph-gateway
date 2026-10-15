package com.monograph.gateway.financial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.monograph.gateway.downstream.DownstreamProperties;
import com.monograph.gateway.error.DownstreamUnavailableException;
import com.monograph.gateway.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestClient;

/**
 * The Financial HTTP boundary on its own, against a real socket: the wire format it accepts and
 * how every failure mode is reported. No Spring context.
 */
class HttpFinancialClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private final StubHttpServer stub = new StubHttpServer();

    @AfterEach
    void stop() {
        stub.close();
    }

    private static HttpFinancialClient client(String baseUrl) {
        var service = new DownstreamProperties.Service(URI.create(baseUrl), Duration.ofMillis(500), READ_TIMEOUT);
        return new HttpFinancialClient(RestClient.builder(), new DownstreamProperties(service, service, service));
    }

    @Test
    void decimalScaleOnTheWireIsKept() {
        stub.respond(200, """
                [{"accountId":"A-2002","available":10500.00,"ledger":-845.10,"currency":"USD","asOf":"2026-09-28T00:00:00Z"}]
                """);

        Balance balance = client(stub.baseUrl()).findBalances(List.of("A-2002")).get("A-2002");

        assertThat(balance.available().toPlainString()).isEqualTo("10500.00");
        assertThat(balance.ledger().toPlainString()).isEqualTo("-845.10");
    }

    @Test
    void slowResponseFailsAtTheReadTimeoutNotWhenTheServerAnswers() {
        stub.respond(200, "[]").delay(Duration.ofSeconds(3));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client(stub.baseUrl()).findBalances(List.of("A-2001")))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("Financial service timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void serverErrorStatusIsReportedWithTheStatusCode() {
        stub.respond(503, "{\"error\":\"maintenance\"}");

        assertThatThrownBy(() -> client(stub.baseUrl()).findTransactions(List.of("A-2001"), 10))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("Financial service responded with HTTP 503")
                // the downstream body is not echoed to GraphQL clients
                .message().doesNotContain("maintenance");
    }

    @Test
    void clientErrorStatusIsAlsoADownstreamFailure() {
        stub.respond(400, "");

        assertThatThrownBy(() -> client(stub.baseUrl()).findBalances(List.of("A-2001")))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("Financial service responded with HTTP 400");
    }

    @Test
    void unparseableBodyIsReported() {
        stub.respond(200, "<html>proxy error</html>");

        assertThatThrownBy(() -> client(stub.baseUrl()).findBalances(List.of("A-2001")))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("Financial service returned an unreadable response");
    }

    @Test
    void nothingListeningIsUnreachable() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://127.0.0.1:" + freePort).findBalances(List.of("A-2001")))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("Financial service is unreachable");
    }
}
