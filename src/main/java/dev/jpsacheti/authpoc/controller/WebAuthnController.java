package dev.jpsacheti.authpoc.controller;

import dev.jpsacheti.authpoc.service.WebAuthnService;
import dev.jpsacheti.authpoc.dto.AuthDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webauthn")
@RequiredArgsConstructor
@Validated
@Tag(name = "WebAuthn", description = "WebAuthn (FIDO2) authentication endpoints")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    @PostMapping("/register/start")
    @Operation(summary = "Start WebAuthn registration", description = "Initiates the WebAuthn registration process and returns a challenge")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Registration challenge created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    })
    public ResponseEntity<String> startRegistration(
            @Parameter(description = "Username for registration (3-50 characters)", required = true)
            @RequestParam @NotBlank(message = "Username cannot be blank") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username,
            @Parameter(description = "Authenticator attachment preference (platform or cross-platform)")
            @RequestParam(name = "attachment", required = false) String attachment) {
        return ResponseEntity.ok(webAuthnService.startRegistration(username, attachment));
    }

    @PostMapping("/register/finish")
    @Operation(summary = "Finish WebAuthn registration", description = "Completes the WebAuthn registration process with the credential")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Registration completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid credential or username")
    })
    public ResponseEntity<Void> finishRegistration(
            @Parameter(description = "Username for registration (3-50 characters)", required = true)
            @RequestParam @NotBlank(message = "Username cannot be blank") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username,
            @Parameter(description = "WebAuthn credential JSON", required = true)
            @RequestBody @NotBlank(message = "Credential JSON cannot be blank") String credentialJson) {
        webAuthnService.finishRegistration(username, credentialJson);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login/start")
    @Operation(summary = "Start WebAuthn login", description = "Initiates the WebAuthn login process and returns a challenge")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login challenge created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Invalid username"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<String> startLogin(
            @Parameter(description = "Username for login (3-50 characters)", required = true)
            @RequestParam @NotBlank(message = "Username cannot be blank") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username) {
        return ResponseEntity.ok(webAuthnService.startLogin(username));
    }

    @PostMapping("/login/finish")
    @Operation(summary = "Finish WebAuthn login", description = "Completes the WebAuthn login process and returns JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthDtos.AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid credential or username"),
        @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    public ResponseEntity<AuthDtos.AuthResponse> finishLogin(
            @Parameter(description = "Username for login (3-50 characters)", required = true)
            @RequestParam @NotBlank(message = "Username cannot be blank") @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") String username,
            @Parameter(description = "WebAuthn credential JSON", required = true)
            @RequestBody @NotBlank(message = "Credential JSON cannot be blank") String credentialJson) {
        return ResponseEntity.ok(webAuthnService.finishLogin(username, credentialJson));
    }
}
