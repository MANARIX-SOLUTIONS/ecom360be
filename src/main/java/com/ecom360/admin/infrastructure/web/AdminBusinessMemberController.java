package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminBusinessMemberResponse;
import com.ecom360.admin.application.dto.AdminBusinessRoleOptionResponse;
import com.ecom360.admin.application.dto.AdminUpdateMemberRoleRequest;
import com.ecom360.admin.application.service.AdminBusinessMemberService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
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
@RequestMapping(ApiConstants.API_BASE + "/admin/businesses")
@Tag(name = "Admin Business Members", description = "Plateforme : membres et rôles par entreprise")
@SecurityRequirement(name = "bearerAuth")
public class AdminBusinessMemberController {

  private final AdminBusinessMemberService adminBusinessMemberService;

  public AdminBusinessMemberController(AdminBusinessMemberService adminBusinessMemberService) {
    this.adminBusinessMemberService = adminBusinessMemberService;
  }

  @GetMapping("/{businessId}/members")
  @Operation(summary = "Lister les membres d'une entreprise")
  public ResponseEntity<List<AdminBusinessMemberResponse>> listMembers(
      @PathVariable UUID businessId, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminBusinessMemberService.listMembers(businessId, p));
  }

  @GetMapping("/{businessId}/roles")
  @Operation(summary = "Rôles disponibles pour cette entreprise (liste déroulante)")
  public ResponseEntity<List<AdminBusinessRoleOptionResponse>> listRoles(
      @PathVariable UUID businessId, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(adminBusinessMemberService.listRoleOptions(businessId, p));
  }

  @PatchMapping("/{businessId}/members/{businessUserId}/role")
  @Operation(summary = "Changer le rôle d'un membre")
  public ResponseEntity<AdminBusinessMemberResponse> updateMemberRole(
      @PathVariable UUID businessId,
      @PathVariable UUID businessUserId,
      @Valid @RequestBody AdminUpdateMemberRoleRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        adminBusinessMemberService.updateMemberRole(businessId, businessUserId, req, p));
  }
}
