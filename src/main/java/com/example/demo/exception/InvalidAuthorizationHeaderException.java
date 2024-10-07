package com.example.demo.exception;

public class InvalidAuthorizationHeaderException extends RuntimeException {
    public InvalidAuthorizationHeaderException(String message) {
        super(message);
    }
}
