package com.ecom360.delivery.application.service;

import com.ecom360.delivery.application.dto.DeliveryRequest;
import com.ecom360.delivery.application.dto.DeliveryResponse;
import com.ecom360.delivery.domain.model.Courier;
import com.ecom360.delivery.domain.model.Delivery;
import com.ecom360.delivery.domain.model.DeliveryStatus;
import com.ecom360.delivery.domain.repository.CourierRepository;
import com.ecom360.delivery.domain.repository.DeliveryRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

  private final DeliveryRepository deliveryRepo;
  private final CourierRepository courierRepo;
  private final SaleRepository saleRepo;
  private final RolePermissionService permissionService;
  private final DeliveryAccessGuard accessGuard;

  public DeliveryService(
      DeliveryRepository deliveryRepo,
      CourierRepository courierRepo,
      SaleRepository saleRepo,
      RolePermissionService permissionService,
      DeliveryAccessGuard accessGuard) {
    this.deliveryRepo = deliveryRepo;
    this.courierRepo = courierRepo;
    this.saleRepo = saleRepo;
    this.permissionService = permissionService;
    this.accessGuard = accessGuard;
  }

  @Transactional
  public DeliveryResponse create(DeliveryRequest req, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_CREATE);
    Courier courier =
        courierRepo
            .findByBusinessIdAndId(p.businessId(), req.courierId())
            .orElseThrow(() -> new ResourceNotFoundException("Livreur", req.courierId()));
    if (!Boolean.TRUE.equals(courier.getIsActive())) {
      throw new BusinessRuleException("Ce livreur est inactif.");
    }
    if (req.saleId() != null) {
      saleRepo
          .findByBusinessIdAndId(p.businessId(), req.saleId())
          .orElseThrow(() -> new ResourceNotFoundException("Vente", req.saleId()));
    }
    DeliveryStatus status = req.parsedStatus();
    Delivery d = new Delivery();
    d.setBusinessId(p.businessId());
    d.setCourierId(req.courierId());
    d.setSaleId(req.saleId());
    d.setStatus(status.name());
    d.setParcelsCount(req.parcelsCount());
    d.setNotes(req.notes());
    if (status == DeliveryStatus.delivered) {
      d.setDeliveredAt(Instant.now());
    }
    return toResponse(deliveryRepo.save(d));
  }

  @Transactional(readOnly = true)
  public List<DeliveryResponse> listByCourier(UUID courierId, UserPrincipal p, int page, int size) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    courierRepo
        .findByBusinessIdAndId(p.businessId(), courierId)
        .orElseThrow(() -> new ResourceNotFoundException("Livreur", courierId));
    return deliveryRepo
        .findByBusinessIdAndCourierIdOrderByDeliveredAtDesc(
            p.businessId(), courierId, pageRequest(page, size, 50))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DeliveryResponse> listByBusiness(UserPrincipal p, int page, int size) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    return deliveryRepo
        .findByBusinessIdOrderByCreatedAtDesc(
            p.businessId(), pageRequest(page, size, ApiConstants.MAX_PAGE_SIZE))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private static PageRequest pageRequest(int page, int size, int maxSize) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), maxSize);
    return PageRequest.of(safePage, safeSize);
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
