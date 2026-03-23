package com.ecom360.notification.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.notification.application.dto.NotificationPreferenceResponse;
import com.ecom360.notification.application.dto.NotificationPreferencesUpdateRequest;
import com.ecom360.notification.application.dto.NotificationResponse;
import com.ecom360.notification.application.service.NotificationPreferenceService;
import com.ecom360.notification.application.service.NotificationService;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
  private final NotificationService svc;
  private final NotificationPreferenceService preferenceSvc;

  public NotificationController(
      NotificationService svc, NotificationPreferenceService preferenceSvc) {
    this.svc = svc;
    this.preferenceSvc = preferenceSvc;
  }

  @GetMapping
  @Operation(summary = "List notifications")
  public ResponseEntity<PageResponse<NotificationResponse>> list(
      @RequestParam(required = false) Boolean unreadOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(svc.list(p, unreadOnly, PageRequest.of(page, Math.min(size, 100)))));
  }

  @GetMapping("/unread-count")
  @Operation(summary = "Unread count")
  public ResponseEntity<Map<String, Long>> unread(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(Map.of("count", svc.unreadCount(p)));
  }

  @PatchMapping("/{id}/read")
  @Operation(summary = "Mark read")
  public ResponseEntity<NotificationResponse> markRead(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.markAsRead(id, p));
  }

  @PostMapping("/mark-all-read")
  @Operation(summary = "Mark all as read")
  public ResponseEntity<Map<String, Integer>> markAll(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(Map.of("marked", svc.markAllAsRead(p)));
  }

  @GetMapping("/preferences")
  @Operation(summary = "Get notification preferences")
  public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(preferenceSvc.getPreferences(p));
  }

  @PutMapping("/preferences")
  @Operation(summary = "Update notification preferences")
  public ResponseEntity<List<NotificationPreferenceResponse>> updatePreferences(
      @RequestBody NotificationPreferencesUpdateRequest req,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(preferenceSvc.updatePreferences(p, req));
  }
}
