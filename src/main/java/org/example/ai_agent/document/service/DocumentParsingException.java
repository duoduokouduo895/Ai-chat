package org.example.ai_agent.document.service;

import org.example.ai_agent.common.exception.ExternalDependencyException;

/** Raised when the document-parser Adapter cannot produce a document or segments. */
public class DocumentParsingException extends ExternalDependencyException {

    public DocumentParsingException(String operation, Throwable cause) {
        super("document-parser", operation, cause);
    }
}
