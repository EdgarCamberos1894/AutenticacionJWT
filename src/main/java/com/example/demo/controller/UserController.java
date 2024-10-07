package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.request.PasswordChangeReq;
import com.example.demo.service.interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("${api.base}/user")
@SecurityRequirement(name = "bearer-key")
@Tag(name="Usuario")
@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;

    @Operation(summary = "Change password",tags = {"Usuario"}, description = "Change the user's password. This operation requires the user to be authenticated and provide the current password along with the new password.")
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody @Valid PasswordChangeReq request){
        userService.changePassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed successfully.",null));
    }

}
