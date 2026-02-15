package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.BusinessUserResponse;
import com.ecom360.tenant.application.dto.InviteUserRequest;
import com.ecom360.tenant.application.service.BusinessUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<BusinessUserResponse> invite(@Valid @RequestBody InviteUserRequest req,
                                                      @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(201).body(businessUserService.invite(req, p));
    }
}
