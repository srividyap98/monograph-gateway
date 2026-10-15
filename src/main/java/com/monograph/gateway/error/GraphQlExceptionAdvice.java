package com.monograph.gateway.error;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Maps resolver exceptions to GraphQL errors for every domain controller.
 * Unhandled exceptions fall through to Spring's default INTERNAL_ERROR, which hides details.
 */
@ControllerAdvice
public class GraphQlExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionAdvice.class);

    @GraphQlExceptionHandler
    public GraphQLError handleBadRequest(IllegalArgumentException ex, DataFetchingEnvironment env) {
        return error(ErrorType.BAD_REQUEST, ex, env);
    }

    @GraphQlExceptionHandler
    public GraphQLError handleNotFound(NotFoundException ex, DataFetchingEnvironment env) {
        return error(ErrorType.NOT_FOUND, ex, env);
    }

    @GraphQlExceptionHandler
    public GraphQLError handleConflict(ConflictException ex, DataFetchingEnvironment env) {
        return error(GatewayErrorType.CONFLICT, ex, env);
    }

    /**
     * The cause is logged here and deliberately not sent to the client. One line, no stack trace:
     * a failed batch call reaches this handler once per field it was loading.
     */
    @GraphQlExceptionHandler
    public GraphQLError handleDownstreamUnavailable(DownstreamUnavailableException ex, DataFetchingEnvironment env) {
        // String.valueOf: a trailing Throwable argument would be logged with its full stack trace.
        log.warn("{} at {}: {}", ex.getMessage(), env.getExecutionStepInfo().getPath(), String.valueOf(ex.getCause()));
        return error(GatewayErrorType.DOWNSTREAM_UNAVAILABLE, ex, env);
    }

    private static GraphQLError error(ErrorClassification type, RuntimeException ex, DataFetchingEnvironment env) {
        return GraphQLError.newError()
                .errorType(type)
                .message(ex.getMessage())
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .build();
    }
}
