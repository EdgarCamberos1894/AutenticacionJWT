package com.cambers.auth.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/contract")
class ContractTestController {

    @PostMapping("/validation")
    void validation(@Valid @RequestBody ValidationRequest request) {
    }

    @PostMapping("/object-validation")
    void objectValidation(@Valid @RequestBody ObjectValidationRequest request) {
    }

    @PostMapping("/method-validation")
    void methodValidation(@RequestParam @Min(2) int amount) {
    }

    @PostMapping("/required-parameter")
    void requiredParameter(@RequestParam String value) {
    }

    @GetMapping(value = "/representation", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> representation() {
        return Map.of("status", "ok");
    }

    @PostMapping("/conflict")
    void conflict() {
        throw new ConflictException("The requested state conflicts with the current resource state.");
    }

    record ValidationRequest(@NotBlank String name) {
    }

    @ContractObjectConstraint
    record ObjectValidationRequest(String first, String second) {
    }
}
