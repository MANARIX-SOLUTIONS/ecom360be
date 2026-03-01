package com.ecom360.delivery.application.service;

import com.ecom360.delivery.application.dto.DeliveryRequest;
import com.ecom360.delivery.application.dto.DeliveryResponse;
import com.ecom360.delivery.domain.model.Delivery;
import com.ecom360.delivery.domain.repository.CourierRepository;
import com.ecom360.delivery.domain.repository.DeliveryRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

  private final DeliveryRepository deliveryRepo;
  private final CourierRepository courierRepo;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public DeliveryService(
      DeliveryRepository deliveryRepo,
      CourierRepository courierRepo,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.deliveryRepo = deliveryRepo;
    this.courierRepo = courierRepo;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }

  private void requireDeliveryPlan(UserPrincipal p) {
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureDeliveryCouriers())) {
                throw new AccessDeniedException(
                    "Gestion des livreurs non incluse dans votre plan.");
              }
            });
  }

  public DeliveryResponse create(DeliveryRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_CREATE);
    requireDeliveryPlan(p);
    courierRepo
        .findByBusinessIdAndId(p.businessId(), req.courierId())
        .orElseThrow(() -> new ResourceNotFoundException("Livreur", req.courierId()));
    if (!List.of("delivered", "failed", "cancelled").contains(req.status())) {
      throw new IllegalArgumentException("Invalid status: " + req.status());
    }
    Delivery d = new Delivery();
    d.setBusinessId(p.businessId());
    d.setCourierId(req.courierId());
    d.setSaleId(req.saleId());
    d.setStatus(req.status());
    d.setParcelsCount(req.parcelsCount());
    d.setNotes(req.notes());
    if ("delivered".equals(req.status())) {
      d.setDeliveredAt(Instant.now());
    }
    return toResponse(deliveryRepo.save(d));
  }

  public List<DeliveryResponse> listByCourier(UUID courierId, UserPrincipal p, int page, int size) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryPlan(p);
    courierRepo
        .findByBusinessIdAndId(p.businessId(), courierId)
        .orElseThrow(() -> new ResourceNotFoundException("Livreur", courierId));
    return deliveryRepo
        .findByBusinessIdAndCourierIdOrderByDeliveredAtDesc(
            p.businessId(), courierId, PageRequest.of(page, Math.min(size, 50)))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  public List<DeliveryResponse> listByBusiness(UserPrincipal p, int page, int size) {
    requireBiz(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireDeliveryPlan(p);
    return deliveryRepo
        .findByBusinessIdOrderByCreatedAtDesc(p.businessId(), PageRequest.of(page, Math.min(size, 100)))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private DeliveryResponse toResponse(Delivery d) {
    return new DeliveryResponse(
        d.getId(),
        d.getBusinessId(),
        d.getCourierId(),
        d.getSaleId(),
        d.getStatus(),
        d.getParcelsCount() != null ? d.getParcelsCount() : 1,
        d.getDeliveredAt(),
        d.getNotes(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
