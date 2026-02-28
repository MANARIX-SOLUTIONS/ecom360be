package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.tenant.application.dto.AssignStoresRequest;
import com.ecom360.tenant.application.dto.BusinessUserResponse;
import com.ecom360.tenant.application.dto.InviteUserRequest;
import com.ecom360.tenant.application.service.BusinessUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/business/users")
@Tag(name = "Business Users", description = "Manage business team members")
@SecurityRequirement(name = "bearerAuth")
public class BusinessUserController {

  private final BusinessUserService businessUserService;

  public BusinessUserController(BusinessUserService businessUserService) {
    this.businessUserService = businessUserService;
  }

  @GetMapping
  @Operation(summary = "List business users")
  public ResponseEntity<List<BusinessUserResponse>> list(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessUserService.list(p));
  }

  @PostMapping
  @Operation(summary = "Invite user to business")
  public ResponseEntity<BusinessUserResponse> invite(
      @Valid @RequestBody InviteUserRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(businessUserService.invite(req, p));
  }

  @PutMapping("/{id}/stores")
  @Operation(summary = "Assign stores to employee (multi-store)")
  public ResponseEntity<List<StoreResponse>> assignStores(
      @PathVariable UUID id,
      @Valid @RequestBody AssignStoresRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessUserService.assignStores(id, req, p));
  }

  @GetMapping("/{id}/stores")
  @Operation(summary = "Get assigned stores for employee")
  public ResponseEntity<List<StoreResponse>> getAssignedStores(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessUserService.getAssignedStores(id, p));
  }
}
