package com.careflow.exception;

/** Raised when a request is well-formed but violates a workflow invariant. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
