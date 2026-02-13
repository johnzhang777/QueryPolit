package com.querypilot.exception;

public class SqlSafetyException extends RuntimeException {

    public SqlSafetyException(String message) {
        super(message);
    }

    public SqlSafetyException(String message, Throwable cause) {
        super(message, cause);
    }
}
