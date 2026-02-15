package com.ecom360.audit.infrastructure.web;

import com.ecom360.audit.application.dto.AuditLogResponse;
import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/audit-logs")
@Tag(name = "Audit Logs")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {
  private final AuditLogService svc;

  public AuditLogController(AuditLogService svc) {
    this.svc = svc;
  }

  @GetMapping
  @Operation(summary = "List audit logs")
  public ResponseEntity<PageResponse<AuditLogResponse>> list(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(p, entityType, userId, PageRequest.of(page, Math.min(size, 100)))));
  }
}
