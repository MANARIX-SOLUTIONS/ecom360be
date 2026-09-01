package com.ecom360.tenant.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.BusinessLogoRequest;
import com.ecom360.tenant.application.dto.BusinessProfileRequest;
import com.ecom360.tenant.application.dto.BusinessProfileResponse;
import com.ecom360.tenant.application.dto.BusinessThemeRequest;
import com.ecom360.tenant.domain.ThemeColors;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.infrastructure.storage.BusinessLogoStorageService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BusinessProfileService {

  private final BusinessRepository businessRepository;
  private final SubscriptionService subscriptionService;
  private final BusinessLogoStorageService businessLogoStorageService;

  public BusinessProfileService(
      BusinessRepository businessRepository,
      SubscriptionService subscriptionService,
      BusinessLogoStorageService businessLogoStorageService) {
    this.businessRepository = businessRepository;
    this.subscriptionService = subscriptionService;
    this.businessLogoStorageService = businessLogoStorageService;
  }

  public BusinessProfileResponse get(UserPrincipal p) {
    return toResponse(findBusiness(p));
  }

  @Transactional
  public BusinessProfileResponse update(BusinessProfileRequest req, UserPrincipal p) {
    requireOwnerOrPlatformAdmin(
        p, "Seul le rôle propriétaire peut modifier les informations de l'entreprise");
    Business b = findBusiness(p);
    UUID currentId = b.getId();
    if (!req.email().equalsIgnoreCase(b.getEmail())) {
      businessRepository
          .findByEmail(req.email())
          .filter(other -> !other.getId().equals(currentId))
          .ifPresent(
              __ -> {
                throw new ResourceAlreadyExistsException("Business", req.email());
              });
    }
    b.setName(req.name());
    b.setEmail(req.email());
    b.setPhone(req.phone());
    b.setAddress(req.address());
    return toResponse(businessRepository.save(b));
  }

  @Transactional
  public BusinessProfileResponse updateLogo(BusinessLogoRequest req, UserPrincipal p) {
    requireOwnerOrPlatformAdmin(p, "Seul le rôle propriétaire peut modifier le logo");
    Business b = findBusiness(p);
    String nl = req.logoUrl() != null ? req.logoUrl().trim() : "";
    if (nl.isEmpty()) {
      businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
      b.setLogoUrl(null);
    } else {
      requireCustomBranding(p, "Personnalisation (logo) réservée au plan Business.");
      businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
      b.setLogoUrl(nl);
    }
    return toResponse(businessRepository.save(b));
  }

  @Transactional
  public BusinessProfileResponse uploadLogo(MultipartFile file, UserPrincipal p) {
    requireOwnerOrPlatformAdmin(p, "Seul le rôle propriétaire peut modifier le logo");
    requireCustomBranding(p, "Personnalisation (logo) réservée au plan Business.");
    Business b = findBusiness(p);
    businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
    String relative = businessLogoStorageService.saveUploadedLogo(p.businessId(), file);
    b.setLogoUrl(relative);
    return toResponse(businessRepository.save(b));
  }

  @Transactional
  public BusinessProfileResponse updateTheme(BusinessThemeRequest req, UserPrincipal p) {
    requireOwnerOrPlatformAdmin(p, "Seul le rôle propriétaire peut modifier le thème");
    String primary = ThemeColors.normalize(req.themePrimaryColor());
    String accent = ThemeColors.normalize(req.themeAccentColor());
    if (primary != null || accent != null) {
      requireCustomBranding(p, "Personnalisation (thème) réservée au plan Business.");
      if (primary != null) {
        ThemeColors.requireValidHex(primary);
        ThemeColors.requireContrastAgainstWhite(primary);
      }
      if (accent != null) {
        ThemeColors.requireValidHex(accent);
      }
    }
    Business b = findBusiness(p);
    b.setThemePrimaryColor(primary);
    b.setThemeAccentColor(accent);
    return toResponse(businessRepository.save(b));
  }

  private void requireOwnerOrPlatformAdmin(UserPrincipal p, String message) {
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException(message);
    }
  }

  private void requireCustomBranding(UserPrincipal p, String message) {
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureCustomBranding())) {
                throw new AccessDeniedException(message);
              }
            });
  }

  private BusinessProfileResponse toResponse(Business b) {
    return new BusinessProfileResponse(
        b.getId(),
        b.getName(),
        b.getEmail(),
        b.getPhone(),
        b.getAddress(),
        b.getLogoUrl(),
        b.getThemePrimaryColor(),
        b.getThemeAccentColor(),
        b.getCreatedAt());
  }

  private Business findBusiness(UserPrincipal p) {
    if (p.businessId() == null) {
      throw new AccessDeniedException("Business context required");
    }
    return businessRepository
        .findById(p.businessId())
        .orElseThrow(() -> new ResourceNotFoundException("Business", p.businessId()));
  }
}
