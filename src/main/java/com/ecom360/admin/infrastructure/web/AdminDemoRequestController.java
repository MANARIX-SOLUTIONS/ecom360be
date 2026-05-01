package com.ecom360.admin.infrastructure.web;

import com.ecom360.identity.application.dto.DemoRejectRequest;
import com.ecom360.identity.application.dto.DemoRequestResponse;
import com.ecom360.identity.application.service.DemoRequestService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/demo-requests")
@Tag(name = "Admin demo requests", description = "Platform admin: validate demo signups")
@SecurityRequirement(name = "bearerAuth")
public class AdminDemoRequestController {

  private final DemoRequestService demoRequestService;

  public AdminDemoRequestController(DemoRequestService demoRequestService) {
    this.demoRequestService = demoRequestService;
  }

  @GetMapping
  @Operation(summary = "List demo requests")
  public ResponseEntity<PageResponse<DemoRequestResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(demoRequestService.list(p, status, page, Math.min(size, 100))));
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve demo request — creates tenant and trial")
  public ResponseEntity<Void> approve(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    demoRequestService.approve(id, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reject")
  @Operation(summary = "Reject demo request")
  public ResponseEntity<Void> reject(
      @PathVariable UUID id,
      @RequestBody(required = false) DemoRejectRequest body,
      @AuthenticationPrincipal UserPrincipal p) {
    demoRequestService.reject(id, p, body != null ? body : new DemoRejectRequest(null));
    return ResponseEntity.noContent().build();
  }
}
