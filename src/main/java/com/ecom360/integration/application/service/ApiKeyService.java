package com.ecom360.integration.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.application.dto.ApiKeyRequest;
import com.ecom360.integration.application.dto.ApiKeyResponse;
import com.ecom360.integration.domain.model.ApiKey;
import com.ecom360.integration.domain.repository.ApiKeyRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
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
public class ApiKeyService {

  private static final String KEY_PREFIX = "ecom360_";

  private final ApiKeyRepository apiKeyRepository;
  private final RolePermissionService permissionService;

  public ApiKeyService(ApiKeyRepository apiKeyRepository, RolePermissionService permissionService) {
    this.apiKeyRepository = apiKeyRepository;
    this.permissionService = permissionService;
  }

  @Transactional
  public ApiKeyResponse create(ApiKeyRequest request, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.API_KEYS_CREATE);
    String rawKey = generateRawKey();
    String keyHash = hashKey(rawKey);

    ApiKey apiKey = new ApiKey();
    apiKey.setBusinessId(p.businessId());
    apiKey.setKeyHash(keyHash);
    apiKey.setLabel(request.label());
    apiKey.setPermissions(request.permissions());
    apiKey.setExpiresAt(request.expiresAt());
    apiKey.setIsActive(true);
    apiKey = apiKeyRepository.save(apiKey);

    return new ApiKeyResponse(
        apiKey.getId(),
        apiKey.getBusinessId(),
        apiKey.getLabel(),
        apiKey.getPermissions(),
        apiKey.getExpiresAt(),
        apiKey.getIsActive(),
        apiKey.getCreatedAt(),
        rawKey);
  }

  public List<ApiKeyResponse> list(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.API_KEYS_READ);
    return apiKeyRepository.findByBusinessId(p.businessId()).stream()
        .map(this::toResponse)
        .toList();
  }

  public ApiKeyResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.API_KEYS_READ);
    return toResponse(find(id, p));
  }

  @Transactional
  public void revoke(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.API_KEYS_DELETE);
    ApiKey apiKey = find(id, p);
    apiKey.setIsActive(false);
    apiKeyRepository.save(apiKey);
  }

  private ApiKey find(UUID id, UserPrincipal p) {
    return apiKeyRepository
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
  }

  private ApiKeyResponse toResponse(ApiKey a) {
    return ApiKeyResponse.withoutRawKey(
        a.getId(),
        a.getBusinessId(),
        a.getLabel(),
        a.getPermissions(),
        a.getExpiresAt(),
        a.getIsActive(),
        a.getCreatedAt());
  }

  private String generateRawKey() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashKey(String key) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }
}
