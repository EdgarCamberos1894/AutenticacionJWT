package com.example.demo.exception;

public class IncorrectCurrentPasswordException extends RuntimeException {
    public IncorrectCurrentPasswordException(String message) {
        super(message);
    }
}
