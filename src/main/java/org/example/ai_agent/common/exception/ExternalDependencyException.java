package org.example.ai_agent.common.exception;

/**
 * An infrastructure Adapter failed after the request entered the application.
 * The global handler deliberately keeps the existing 500 response contract.
 */
public class ExternalDependencyException extends RuntimeException {

    private final String dependency;
    private final String operation;

    public ExternalDependencyException(String dependency, String operation, Throwable cause) {
        super(dependency + " " + operation + " failed", cause);
        this.dependency = dependency;
        this.operation = operation;
    }

    public String dependency() {
        return dependency;
    }

    public String operation() {
        return operation;
    }
}
