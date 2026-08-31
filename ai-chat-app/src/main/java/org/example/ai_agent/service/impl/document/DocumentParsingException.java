package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.exception.ExternalDependencyException;

/** Raised when the document-parser Adapter cannot produce a document or segments. */
public class DocumentParsingException extends ExternalDependencyException {

    public DocumentParsingException(String operation, Throwable cause) {
        super("document-parser", operation, cause);
    }
}
