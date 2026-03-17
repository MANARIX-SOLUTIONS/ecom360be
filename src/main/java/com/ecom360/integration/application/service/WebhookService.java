package com.ecom360.integration.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.application.dto.WebhookCreateResponse;
import com.ecom360.integration.application.dto.WebhookRequest;
import com.ecom360.integration.application.dto.WebhookResponse;
import com.ecom360.integration.domain.model.Webhook;
import com.ecom360.integration.domain.repository.WebhookRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private final WebhookRepository webhookRepository;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public WebhookService(
      WebhookRepository webhookRepository,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.webhookRepository = webhookRepository;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  private void requireApi(UserPrincipal p) {
    subscriptionService.requireFeatureApi(p.businessId());
  }

  @Transactional
  public WebhookCreateResponse create(WebhookRequest request, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_CREATE);
    String secret = generateSecret();
    String secretHash = hashSecret(secret);

    Webhook webhook = new Webhook();
    webhook.setBusinessId(p.businessId());
    webhook.setUrl(request.url());
    webhook.setEvents(request.events());
    webhook.setSecretHash(secretHash);
    webhook.setIsActive(request.isActive());
    webhook = webhookRepository.save(webhook);

    return new WebhookCreateResponse(
        webhook.getId(),
        webhook.getBusinessId(),
        webhook.getUrl(),
        webhook.getEvents(),
        webhook.getIsActive(),
        webhook.getCreatedAt(),
        webhook.getUpdatedAt(),
        secret);
  }

  public List<WebhookResponse> list(UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_READ);
    return webhookRepository.findByBusinessId(p.businessId()).stream()
        .map(this::toResponse)
        .toList();
  }

  public WebhookResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_READ);
    return toResponse(find(id, p));
  }

  @Transactional
  public WebhookResponse update(UUID id, WebhookRequest request, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_UPDATE);
    Webhook webhook = find(id, p);
    webhook.setUrl(request.url());
    webhook.setEvents(request.events());
    webhook.setIsActive(request.isActive());
    webhook = webhookRepository.save(webhook);
    return toResponse(webhook);
  }

  @Transactional
  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    requireApi(p);
    permissionService.require(p, Permission.WEBHOOKS_DELETE);
    webhookRepository.delete(find(id, p));
  }

  private Webhook find(UUID id, UserPrincipal p) {
    return webhookRepository
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Webhook", id));
  }

  private WebhookResponse toResponse(Webhook w) {
    return new WebhookResponse(
        w.getId(),
        w.getBusinessId(),
        w.getUrl(),
        w.getEvents(),
        w.getIsActive(),
        w.getCreatedAt(),
        w.getUpdatedAt());
  }

  private String generateSecret() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashSecret(String secret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }
}
