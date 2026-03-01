package com.ecom360.delivery.application.service;

import com.ecom360.delivery.application.dto.CourierRequest;
import com.ecom360.delivery.application.dto.CourierResponse;
import com.ecom360.delivery.application.dto.CourierStatsResponse;
import com.ecom360.delivery.domain.model.Courier;
import com.ecom360.delivery.domain.repository.CourierRepository;
import com.ecom360.delivery.domain.repository.DeliveryRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CourierService {

  private final CourierRepository courierRepo;
  private final DeliveryRepository deliveryRepo;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public CourierService(
      CourierRepository courierRepo,
      DeliveryRepository deliveryRepo,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.courierRepo = courierRepo;
    this.deliveryRepo = deliveryRepo;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }

  private void requireDeliveryCouriersPlan(UserPrincipal p) {
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureDeliveryCouriers())) {
                throw new AccessDeniedException(
                    "Gestion des livreurs non incluse dans votre plan. Passez au plan Pro ou Business.");
              }
            });
  }

  public List<CourierResponse> list(UserPrincipal p, boolean activeOnly) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryCouriersPlan(p);
    List<Courier> list =
        activeOnly
            ? courierRepo.findByBusinessIdAndIsActiveTrueOrderByNameAsc(p.businessId())
            : courierRepo.findByBusinessIdOrderByNameAsc(p.businessId());
    return list.stream().map(this::toResponse).toList();
  }

  public CourierStatsResponse getStats(UUID courierId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryCouriersPlan(p);
    courierRepo
        .findByBusinessIdAndId(p.businessId(), courierId)
        .orElseThrow(() -> new ResourceNotFoundException("Livreur", courierId));
    long totalParcels = deliveryRepo.sumParcelsDeliveredByCourierAndBusiness(courierId, p.businessId());
    long delivered = deliveryRepo.countDeliveredByCourierAndBusiness(courierId, p.businessId());
    long failed = deliveryRepo.countFailedByCourierAndBusiness(courierId, p.businessId());
    long completed = delivered + failed;
    double successRatePercent = completed > 0 ? (100.0 * delivered / completed) : 100.0;
    return new CourierStatsResponse(courierId, totalParcels, delivered, failed, successRatePercent);
  }

  public List<CourierStatsResponse> getAllStats(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryCouriersPlan(p);
    List<Courier> couriers = courierRepo.findByBusinessIdOrderByNameAsc(p.businessId());
    java.util.Map<UUID, CourierStatsResponse> statsMap = new java.util.HashMap<>();
    for (Courier c : couriers) {
      statsMap.put(
          c.getId(),
          new CourierStatsResponse(c.getId(), 0L, 0L, 0L, 100.0));
    }
    for (Object[] row : deliveryRepo.findDeliveryStatsByBusinessId(p.businessId())) {
      UUID courierId = (UUID) row[0];
      long totalParcels = row[1] != null ? ((Number) row[1]).longValue() : 0L;
      long delivered = row[2] != null ? ((Number) row[2]).longValue() : 0L;
      long failed = row[3] != null ? ((Number) row[3]).longValue() : 0L;
      long completed = delivered + failed;
      double successRatePercent = completed > 0 ? (100.0 * delivered / completed) : 100.0;
      statsMap.put(
          courierId,
          new CourierStatsResponse(courierId, totalParcels, delivered, failed, successRatePercent));
    }
    return couriers.stream().map(c -> statsMap.get(c.getId())).toList();
  }

  public CourierResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryCouriersPlan(p);
    Courier c =
        courierRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Livreur", id));
    return toResponse(c);
  }

  public CourierResponse create(CourierRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_CREATE);
    requireDeliveryCouriersPlan(p);
    if (courierRepo.existsByBusinessIdAndNameIgnoreCase(p.businessId(), req.name())) {
      throw new ResourceAlreadyExistsException("Livreur", req.name());
    }
    Courier c = new Courier();
    c.setBusinessId(p.businessId());
    c.setName(req.name());
    c.setPhone(req.phone());
    c.setEmail(req.email());
    c.setIsActive(req.isActive());
    return toResponse(courierRepo.save(c));
  }

  public CourierResponse update(UUID id, CourierRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_UPDATE);
    requireDeliveryCouriersPlan(p);
    Courier c =
        courierRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Livreur", id));
    c.setName(req.name());
    c.setPhone(req.phone());
    c.setEmail(req.email());
    c.setIsActive(req.isActive());
    return toResponse(courierRepo.save(c));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_DELETE);
    requireDeliveryCouriersPlan(p);
    Courier c =
        courierRepo
            .findByBusinessIdAndId(p.businessId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Livreur", id));
    courierRepo.delete(c);
  }

  private CourierResponse toResponse(Courier c) {
    return new CourierResponse(
        c.getId(),
        c.getBusinessId(),
        c.getName(),
        c.getPhone(),
        c.getEmail(),
        c.getIsActive(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
