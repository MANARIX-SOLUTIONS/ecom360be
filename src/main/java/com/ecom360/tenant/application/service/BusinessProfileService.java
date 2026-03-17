package com.ecom360.tenant.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.BusinessLogoRequest;
import com.ecom360.tenant.application.dto.BusinessProfileRequest;
import com.ecom360.tenant.application.dto.BusinessProfileResponse;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

  private final BusinessRepository businessRepository;
  private final SubscriptionService subscriptionService;

  public BusinessProfileService(
      BusinessRepository businessRepository, SubscriptionService subscriptionService) {
    this.businessRepository = businessRepository;
    this.subscriptionService = subscriptionService;
  }

  public BusinessProfileResponse get(UserPrincipal p) {
    Business b = findBusiness(p);
    return new BusinessProfileResponse(
        b.getId(), b.getName(), b.getEmail(), b.getPhone(), b.getAddress(), b.getLogoUrl());
  }

  @Transactional
  public BusinessProfileResponse update(BusinessProfileRequest req, UserPrincipal p) {
    String role = p.role() != null ? p.role() : "";
    if (!"proprietaire".equalsIgnoreCase(role) && !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Seul le rôle propriétaire peut modifier les informations de l'entreprise");
    }
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
    return new BusinessProfileResponse(
        b.getId(), b.getName(), b.getEmail(), b.getPhone(), b.getAddress(), b.getLogoUrl());
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
      b.setLogoUrl(nl);
    }
    b = businessRepository.save(b);
    return new BusinessProfileResponse(
        b.getId(), b.getName(), b.getEmail(), b.getPhone(), b.getAddress(), b.getLogoUrl());
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
