package dev.javafornestjs.capstone.core;

public final class RequestFailure extends RuntimeException {
    private final Code code;

    public RequestFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, RESOURCE_CONFLICT
    }
}
