package com.monograph.downstream.customer;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated Customer service: system of record for customer identity and status.
 * Owns the business rules for writes (default status, unique email); the gateway only forwards.
 */
@RestController
@RequestMapping("/customer-service/customers")
class CustomerServiceController {

    /** The service's own wire format; the gateway maps it into its own model. */
    record CustomerDto(String id, String firstName, String lastName, String email, String status) {

        CustomerDto withStatus(String newStatus) {
            return new CustomerDto(id, firstName, lastName, email, newStatus);
        }
    }

    record CreateCustomerRequest(String firstName, String lastName, String email) {
    }

    record StatusChangeRequest(String status) {
    }

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED");

    /** Mutable and shared by all request threads, hence a concurrent map. */
    private final Map<String, CustomerDto> customers = new ConcurrentHashMap<>(Map.of(
            "C-1001", new CustomerDto("C-1001", "Ada", "Lovelace", "ada@example.com", "ACTIVE"),
            "C-1002", new CustomerDto("C-1002", "Alan", "Turing", null, "INACTIVE"),
            "C-1003", new CustomerDto("C-1003", "Grace", "Hopper", "grace@example.com", "SUSPENDED")));

    private final AtomicInteger nextId = new AtomicInteger(1004);

    @GetMapping("/{id}")
    ResponseEntity<CustomerDto> byId(@PathVariable String id) {
        return ResponseEntity.of(Optional.ofNullable(customers.get(id)));
    }

    /** Batch lookup: {@code ?ids=C-1&ids=C-2}. Unknown ids are simply absent from the result. */
    @GetMapping(params = "ids")
    List<CustomerDto> byIds(@RequestParam List<String> ids) {
        return ids.stream().distinct().map(customers::get).filter(Objects::nonNull).toList();
    }

    /**
     * 201 with the created customer, or 409 if the email is already registered. New customers
     * start ACTIVE: a business rule owned here, not by the gateway. Synchronized so the
     * uniqueness check and the insert cannot interleave between two requests.
     */
    @PostMapping
    synchronized ResponseEntity<?> create(@RequestBody CreateCustomerRequest request) {
        if (request.email() != null && customers.values().stream()
                .anyMatch(c -> request.email().equalsIgnoreCase(c.email()))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Email " + request.email() + " is already registered"));
        }
        String id = "C-" + nextId.getAndIncrement();
        CustomerDto created = new CustomerDto(id, request.firstName(), request.lastName(), request.email(), "ACTIVE");
        customers.put(id, created);
        return ResponseEntity.created(URI.create("/customer-service/customers/" + id)).body(created);
    }

    /** 200 with the updated customer, 404 if unknown, 400 if the status is not recognised. */
    @PatchMapping("/{id}/status")
    ResponseEntity<?> changeStatus(@PathVariable String id, @RequestBody StatusChangeRequest request) {
        if (!STATUSES.contains(request.status())) {
            return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Unknown status " + request.status()));
        }
        // computeIfPresent is atomic per key: no lost update between concurrent status changes.
        CustomerDto updated = customers.computeIfPresent(id, (key, existing) -> existing.withStatus(request.status()));
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
}
