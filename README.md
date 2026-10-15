# monograph-gateway

Spring for GraphQL gateway recreating Monograph. It receives GraphQL requests from the
customer-service platform, calls the Customer, Account and Financial REST services, and
assembles one GraphQL response.

- **Phase 1 / Level 1:** Customer schema + `customer(id)` query.
- **Phase 2 / Stage 6:** `customerProfile(customerId)` aggregated from three downstream services over HTTP.
- **Phase 2 / Stage 7:** `customers(ids)` + `Customer.accounts`; DataLoader batching makes downstream calls
  one per service endpoint per request instead of one per object (see `DataLoaderTest`).
- **Polyglot backend:** the Financial service also exists as a Node.js/TypeScript service
  (`services/financial-service-node`). The gateway calls it over HTTP with timeouts and explicit
  `DOWNSTREAM_UNAVAILABLE` errors. See [Architecture](#architecture).

## Architecture

```
customer-service platform
        │  POST /graphql
        ▼
┌─────────────────────────── Spring Boot gateway (:8080) ───────────────────────────┐
│ GraphQL schema + resolvers → per-request DataLoaders → ports (Customer/Account/   │
│ FinancialClient) → Http*Client (RestClient, base URL + timeouts per service)      │
└───────────┬──────────────────────┬──────────────────────────────┬─────────────────┘
            │ REST                 │ REST                         │ REST  monograph.downstream.financial.base-url
            ▼                      ▼                              ▼
   Customer service        Account service            Financial service: Java simulator (:8090)
   (Java simulator :8090)  (Java simulator :8090)     or financial-service-node (:8091), same contract
```

The gateway stays the only GraphQL endpoint and owns the schema, batching, validation and error
mapping. Node is used only for a **backend service**, at the seam the gateway already has: one
port interface plus one configurable base URL per service.

**Why Financial.** It is read-only, so no mutable state is split across processes (the Customer
service handles writes). It is keyed only by account id. And its contract has the one genuinely
cross-language hazard in this system: money is a JSON number that must keep its scale.
Jackson writes `BigDecimal("10500.00")` as `10500.00`. `JSON.stringify` writes `10500`, and the
gateway would then serve `"10500"`. The Node service keeps amounts as decimal text and emits them
with `JSON.rawJSON`.

**What it is, honestly.** `financial-service-node` is a port of the *simulator*: same seed data,
same paths, byte-identical responses. It is a demonstration that the gateway's HTTP boundary is
language-neutral, not a production Financial service. It adds no business rules. The default
configuration still points at the Java simulator, so existing behavior is unchanged until you
switch the base URL.

**Failure handling at the Financial boundary** (`downstream/DownstreamHttp`, `HttpFinancialClient`):

| Failure                                   | GraphQL error message                               |
|-------------------------------------------|-----------------------------------------------------|
| no response within `read-timeout` (2s)    | `Financial service timed out`                       |
| connection refused / connect timeout (1s) | `Financial service is unreachable` / `... timed out` |
| any non-2xx status                        | `Financial service responded with HTTP 503`         |
| body that is not the expected JSON        | `Financial service returned an unreadable response` |

All of these use `extensions.classification: DOWNSTREAM_UNAVAILABLE`. The downstream body and the
stack trace are never returned. The cause is logged on one line. No retries: a retry would add
latency on top of a timeout that is already the latency budget. How much of the response survives
depends on the existing schema nullability: `balance` is nullable, so only that field becomes
null, but `transactions` is `[Transaction!]!`, so its failure nulls `customerProfile`
(see `FinancialServiceFailureIntegrationTest`).

## Run

Two processes: the simulated backends, then the gateway.

```bash
# 1. Simulated Customer / Account / Financial services on :8090
mvn spring-boot:run -Dspring-boot.run.main-class=com.monograph.downstream.DownstreamServicesApplication

# 2. Gateway on :8080 (add -Dspring-boot.run.arguments=--server.port=8081 to change port)
mvn spring-boot:run
```

- GraphQL endpoint: `POST /graphql`
- GraphiQL IDE: `/graphiql`
- Merged schema: `GET /graphql/schema`

```bash
curl -s -X POST localhost:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{ customerProfile(customerId: \"C-1001\") { customer { firstName } accounts { id type balance { available } transactions(limit: 2) { amount type description } } } }"}'
```

Mutations are forwarded to the Customer service (the system of record):

```bash
curl -s -X POST localhost:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"mutation { createCustomer(input: {firstName: \"Katherine\", lastName: \"Johnson\", email: \"kj@example.com\"}) { id status } }"}'
curl -s -X POST localhost:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"mutation { updateCustomerStatus(id: \"C-1004\", status: SUSPENDED) { id status } }"}'
```

Errors: invalid input → `BAD_REQUEST`, unknown id → `NOT_FOUND`, duplicate email → `CONFLICT`.

Sample ids: `C-1001` (ACTIVE, checking + savings), `C-1002` (INACTIVE, null email, one closed account),
`C-1003` (SUSPENDED, frozen credit card).

Downstream base URLs and optional `connect-timeout` / `read-timeout` are configured under
`monograph.downstream.*` in `application.yml`. Only Financial has timeouts set by default.

### With the Node Financial service

Requires Node.js 22 or newer (`JSON.rawJSON`).

```bash
# 1. Java simulators as above (Customer and Account are still used from :8090)
mvn spring-boot:run -Dspring-boot.run.main-class=com.monograph.downstream.DownstreamServicesApplication

# 2. Node Financial service on :8091
cd services/financial-service-node && npm install && npm run build && npm start

# 3. Gateway pointed at it
MONOGRAPH_DOWNSTREAM_FINANCIAL_BASE_URL=http://localhost:8091/financial-service mvn spring-boot:run
```

To see the timeout path, restart step 2 with `FINANCIAL_RESPONSE_DELAY_MS=3000 npm start`
(demo-only fault injection). Queries that select `balance` or `transactions` then return
`DOWNSTREAM_UNAVAILABLE` errors. Selections without money fields still succeed.

Node service settings (environment): `PORT` (8091), `BASE_PATH` (`/financial-service`),
`FINANCIAL_RESPONSE_DELAY_MS` (0). It also serves `GET /health`.

## Test

```bash
# Java: gateway unit, slice and integration tests (the Node contract test is skipped)
mvn test

# Node: compiles TypeScript, then runs node:test against the service on a random port
cd services/financial-service-node && npm install && npm test

# Cross-language contract: Node responses must be byte-identical to the Java simulator's,
# and the gateway must serve correct money values from Node. Start the Node service first.
FINANCIAL_SERVICE_NODE_URL=http://localhost:8091/financial-service mvn test -Dtest=NodeFinancialServiceContractTest
```

Integration tests start the simulated services on a random port (`support/DownstreamServices`).
Failure-mode tests (`HttpFinancialClientTest`, `FinancialServiceFailureIntegrationTest`) use a real
socket (`support/StubHttpServer`) that can delay or return errors.

## Layout

```
graphql/<domain>.graphqls        one schema file per domain, merged at startup (extend type for cross-domain fields)
<domain>/<Domain>Controller      @QueryMapping / @SchemaMapping resolvers
<domain>/<Domain>Client          port to the backing domain service
<domain>/Http<Domain>Client      RestClient adapter calling the service
profile/                         customerProfile aggregate root
downstream/DownstreamProperties  base URL and optional timeouts per backend service
downstream/DownstreamHttp        RestClient construction + failure -> DownstreamUnavailableException
error/                           shared exception -> GraphQLError mapping

com.monograph.downstream         simulated backend services (separate Spring Boot app, not part of the gateway)

services/financial-service-node  Node.js/TypeScript Financial simulator (own package.json, no runtime deps)
  src/data.ts                    seed data, kept identical to FinancialServiceController
  src/app.ts                     GET /balances, GET /transactions, /health; money via JSON.rawJSON
  src/server.ts                  entry point, env config, graceful shutdown
  test/app.test.ts               node:test, exact response bodies
```

## Tradeoffs and limits

- **Duplicated seed data.** Java and Node simulators each hold the same data. The contract test
  catches drift, but only when it is run with the Node service up. It is not part of plain `mvn test`.
- **Two batch endpoints only.** The Node port serves what `HttpFinancialClient` calls. The Java
  simulator's per-account endpoints predate DataLoader and are unused.
- **Limit validation.** The Node service rejects a negative `limit` with 400. The Java simulator
  answers 500. The gateway never sends one (it validates 1..50).
- **Timeouts add up.** The balances and transactions batch loaders run one after the other, so a
  Financial outage costs up to 2 × `read-timeout` (about 4s by default) before the response returns.
  An overall request deadline, or running the loaders concurrently, would bound this and is a
  separate change.
- **Scope of the error mapping.** Customer and Account accept the same timeout settings, but their
  failures still surface as `INTERNAL_ERROR`. `DownstreamHttp.call` was not applied to them,
  because the Customer client gives 404 and 409 domain meaning and that behavior is unchanged.
- **Explicit request factory.** When a service has timeouts, its RestClient is built from
  `ClientHttpRequestFactoryBuilder.detect()`, so global `spring.http.clients.*` settings do not
  apply to it. The gateway sets none today.
