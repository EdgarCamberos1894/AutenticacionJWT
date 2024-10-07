package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request object for updating a user's profile information, including name, last name, and profile photo.")
public record UserProfileReq(

        @Schema(description = "The user's updated first name. It should contain only letters and spaces.", example = "Juan")
        @Size(max = 50, message = "Name must be a maximum of 50 characters.")
        @Pattern(regexp = "^[A-Za-zñáéíóúü]+(?: [A-Za-zñáéíóúü]+)*$",message = "Name should only contain letters and spaces.")
        String name,

        @Schema(description = "The user's updated last name. It should contain only letters and spaces.", example = "Pérez")
        @Size(max = 50, message = "Last name must be a maximum of 50 characters.")
        @Pattern(regexp = "^[A-Za-zñáéíóúü]+(?: [A-Za-zñáéíóúü]+)*$",message = "Name should only contain letters and spaces.")
        String lastname,

        @Schema(description = "URL or path to the user's updated profile photo.", example = "https://example.com/photos/profile123.jpg")
        String photo,

        @Schema(description = "Boolean flag to indicate if the profile photo should be removed. If true, the photo will be deleted.", example = "false")
        boolean isRemovePhoto
) {
}
