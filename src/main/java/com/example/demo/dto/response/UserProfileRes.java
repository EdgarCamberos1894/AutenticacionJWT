package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object containing user profile information.")
public record UserProfileRes(

        @Schema(description = "The first name of the user.", example = "John")
        String name,

        @Schema(description = "The last name of the user.", example = "Doe")
        String lastname,

        @Schema(description = "The URL of the user's profile photo.", example = "https://example.com/photos/john_doe.jpg")
        String photo

) {
}
