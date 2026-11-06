package com.enjoytrip;

public class ApiException extends RuntimeException {
    final int status;
    final String field;
    public ApiException(int status, String message) { this(status, message, null); }
    public ApiException(int status, String message, String field) {
        super(message); this.status = status; this.field = field;
    }
}
