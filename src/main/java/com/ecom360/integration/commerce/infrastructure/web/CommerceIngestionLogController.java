package com.ecom360.integration.commerce.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.commerce.application.dto.CommerceIngestionLogResponse;
import com.ecom360.integration.commerce.application.service.CommerceIngestionLogService;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/commerce/ingestions")
@Tag(name = "Commerce ingestions", description = "Journal d'ingestion des commandes web")
@SecurityRequirement(name = "bearerAuth")
public class CommerceIngestionLogController {

  private final CommerceIngestionLogService ingestionLogService;

  public CommerceIngestionLogController(CommerceIngestionLogService ingestionLogService) {
    this.ingestionLogService = ingestionLogService;
  }

  @GetMapping
  @Operation(summary = "Lister le journal d'ingestion (optionnellement filtré par connexion)")
  public ResponseEntity<PageResponse<CommerceIngestionLogResponse>> list(
      @RequestParam(required = false) UUID connectionId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        PageResponse.of(
            ingestionLogService.list(
                principal,
                connectionId,
                PageRequest.of(page, Math.min(size, ApiConstants.MAX_PAGE_SIZE)))));
  }
}
