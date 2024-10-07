package com.example.demo.exception;

public class InvalidTokenPurposeException extends RuntimeException {
    public InvalidTokenPurposeException(String message) {
        super(message);
    }
}
