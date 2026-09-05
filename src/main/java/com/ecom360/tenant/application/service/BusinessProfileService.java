package com.ecom360.tenant.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.SharedCatalogStockService;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.BusinessCatalogModeRequest;
import com.ecom360.tenant.application.dto.BusinessLogoRequest;
import com.ecom360.tenant.application.dto.BusinessProfileRequest;
import com.ecom360.tenant.application.dto.BusinessProfileResponse;
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
  private final SharedCatalogStockService sharedCatalogStockService;

  public BusinessProfileService(
      BusinessRepository businessRepository,
      SubscriptionService subscriptionService,
      BusinessLogoStorageService businessLogoStorageService,
      SharedCatalogStockService sharedCatalogStockService) {
    this.businessRepository = businessRepository;
    this.subscriptionService = subscriptionService;
    this.businessLogoStorageService = businessLogoStorageService;
    this.sharedCatalogStockService = sharedCatalogStockService;
  }

  public BusinessProfileResponse get(UserPrincipal p) {
    return map(findBusiness(p));
  }

  @Transactional
  public BusinessProfileResponse update(BusinessProfileRequest req, UserPrincipal p) {
    requireOwner(p, "Seul le rôle propriétaire peut modifier les informations de l'entreprise");
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
    b = businessRepository.save(b);
    return map(b);
  }

  @Transactional
  public BusinessProfileResponse updateCatalogMode(BusinessCatalogModeRequest req, UserPrincipal p) {
    requireOwner(p, "Seul le rôle propriétaire peut modifier le mode catalogue");
    Business b = findBusiness(p);
    String mode = req.catalogMode() != null ? req.catalogMode().trim().toUpperCase() : "";
    if (!Business.CATALOG_MODE_PER_STORE.equals(mode)
        && !Business.CATALOG_MODE_SHARED.equals(mode)) {
      throw new BusinessRuleException("Mode catalogue invalide");
    }
    boolean enablingShared = Business.CATALOG_MODE_SHARED.equals(mode) && !b.hasSharedCatalog();
    b.setCatalogMode(mode);
    b = businessRepository.save(b);
    if (enablingShared) {
      sharedCatalogStockService.backfillMissingForBusiness(b.getId());
    }
    return map(b);
  }

  @Transactional
  public BusinessProfileResponse updateLogo(BusinessLogoRequest req, UserPrincipal p) {
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Seul le rôle propriétaire peut modifier le logo");
    }
    Business b = findBusiness(p);
    String nl = req.logoUrl() != null ? req.logoUrl().trim() : "";
    if (nl.isEmpty()) {
      businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
      b.setLogoUrl(null);
    } else {
      subscriptionService
          .getPlanForBusiness(p.businessId())
          .ifPresent(
              plan -> {
                if (!Boolean.TRUE.equals(plan.getFeatureCustomBranding())) {
                  throw new AccessDeniedException(
                      "Personnalisation (logo) réservée au plan Business.");
                }
              });
      businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
      b.setLogoUrl(nl);
    }
    b = businessRepository.save(b);
    return map(b);
  }

  @Transactional
  public BusinessProfileResponse uploadLogo(MultipartFile file, UserPrincipal p) {
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Seul le rôle propriétaire peut modifier le logo");
    }
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureCustomBranding())) {
                throw new AccessDeniedException(
                    "Personnalisation (logo) réservée au plan Business.");
              }
            });
    Business b = findBusiness(p);
    businessLogoStorageService.deleteManagedLogoIfPresent(p.businessId(), b.getLogoUrl());
    String relative = businessLogoStorageService.saveUploadedLogo(p.businessId(), file);
    b.setLogoUrl(relative);
    b = businessRepository.save(b);
    return map(b);
  }

  private void requireOwner(UserPrincipal p, String message) {
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException(message);
    }
  }

  private BusinessProfileResponse map(Business b) {
    String mode = b.getCatalogMode() != null ? b.getCatalogMode() : Business.CATALOG_MODE_PER_STORE;
    return new BusinessProfileResponse(
        b.getId(),
        b.getName(),
        b.getEmail(),
        b.getPhone(),
        b.getAddress(),
        b.getLogoUrl(),
        b.getCreatedAt(),
        mode);
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
