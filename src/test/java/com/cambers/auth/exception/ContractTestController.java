package com.cambers.auth.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contract")
class ContractTestController {

    @PostMapping("/validation")
    void validation(@Valid @RequestBody ValidationRequest request) {
    }

    @PostMapping("/conflict")
    void conflict() {
        throw new ConflictException("The requested state conflicts with the current resource state.");
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
