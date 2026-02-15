package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminStatsResponse;
import com.ecom360.admin.application.service.AdminStatsService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/stats")
@Tag(name = "Admin Stats", description = "Platform admin: dashboard statistics")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping
    @Operation(summary = "Get platform statistics (platform admin)")
    public ResponseEntity<AdminStatsResponse> getStats(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(adminStatsService.getStats(p));
    }
}
