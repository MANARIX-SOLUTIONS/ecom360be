package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminBusinessResponse;
import com.ecom360.admin.application.service.AdminBusinessService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/businesses")
@Tag(name = "Admin Businesses", description = "Platform admin: list and manage businesses")
@SecurityRequirement(name = "bearerAuth")
public class AdminBusinessController {

  private final AdminBusinessService adminBusinessService;

  public AdminBusinessController(AdminBusinessService adminBusinessService) {
    this.adminBusinessService = adminBusinessService;
  }

  @GetMapping
  @Operation(summary = "List all businesses (platform admin)")
  public ResponseEntity<PageResponse<AdminBusinessResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String plan,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            adminBusinessService.list(
                p, page, Math.min(size, ApiConstants.MAX_PAGE_SIZE), search, status, plan)));
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Update business status (suspend/activate)")
  public ResponseEntity<Void> setStatus(
      @PathVariable UUID id,
      @RequestBody Map<String, String> body,
      @AuthenticationPrincipal UserPrincipal p) {
    String status = body != null && body.containsKey("status") ? body.get("status") : null;
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status is required");
    }
    adminBusinessService.setStatus(id, status.trim(), p);
    return ResponseEntity.noContent().build();
  }
}
