package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminInviteRequest;
import com.ecom360.admin.application.dto.AdminUserResponse;
import com.ecom360.admin.application.service.AdminUserService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/users")
@Tag(name = "Admin Users", description = "Platform admin: list platform users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @GetMapping
  @Operation(summary = "List all users (platform admin)")
  public ResponseEntity<PageResponse<AdminUserResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            adminUserService.list(
                p, page, Math.min(size, ApiConstants.MAX_PAGE_SIZE), search, status, role)));
  }

  @PostMapping("/invite")
  @Operation(summary = "Invite user to business (platform admin)")
  public ResponseEntity<AdminUserResponse> invite(
      @Valid @RequestBody AdminInviteRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(adminUserService.invite(req, p));
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Enable or disable user (platform admin)")
  public ResponseEntity<Void> setStatus(
      @PathVariable UUID id,
      @RequestBody Map<String, Boolean> body,
      @AuthenticationPrincipal UserPrincipal p) {
    Boolean active = body != null && body.containsKey("active") ? body.get("active") : null;
    if (active == null) {
      throw new IllegalArgumentException("active is required");
    }
    adminUserService.setStatus(id, active, p);
    return ResponseEntity.noContent().build();
  }
}
