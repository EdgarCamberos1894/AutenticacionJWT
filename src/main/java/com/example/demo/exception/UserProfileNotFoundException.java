package com.example.demo.exception;

public class UserProfileNotFoundException extends  RuntimeException{
    public UserProfileNotFoundException(String message) {
        super(message);
    }
}
