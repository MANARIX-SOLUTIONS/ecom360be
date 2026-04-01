package com.ecom360.identity.infrastructure.web;

import com.ecom360.identity.application.dto.*;
import com.ecom360.identity.application.service.AuthService;
import com.ecom360.identity.application.service.DemoRequestService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/auth")
@Tag(name = "Authentication", description = "User authentication and demo signup")
public class AuthController {

  private final AuthService authService;
  private final DemoRequestService demoRequestService;

  public AuthController(AuthService authService, DemoRequestService demoRequestService) {
    this.authService = authService;
    this.demoRequestService = demoRequestService;
  }

  @PostMapping("/login")
  @Operation(summary = "Login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/demo-request")
  @Operation(summary = "Request demo access — pending admin approval (no JWT)")
  public ResponseEntity<DemoRequestAckResponse> demoRequest(
      @Valid @RequestBody DemoRequestSubmitRequest request) {
    return ResponseEntity.status(202).body(demoRequestService.submit(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access token")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refreshToken(request));
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Request password reset")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Reset password with token")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/change-password")
  @Operation(summary = "Change password (authenticated)")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal UserPrincipal p) {
    authService.changePassword(p.userId(), p.businessId(), request);
    return ResponseEntity.noContent().build();
  }
}
