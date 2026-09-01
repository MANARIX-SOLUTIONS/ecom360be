package com.ecom360.delivery.application.service;

import com.ecom360.delivery.application.dto.CourierRequest;
import com.ecom360.delivery.application.dto.CourierResponse;
import com.ecom360.delivery.application.dto.CourierStatsResponse;
import com.ecom360.delivery.domain.model.Courier;
import com.ecom360.delivery.domain.repository.CourierDeliveryStatsRow;
import com.ecom360.delivery.domain.repository.CourierRepository;
import com.ecom360.delivery.domain.repository.DeliveryRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourierService {

  private final CourierRepository courierRepo;
  private final DeliveryRepository deliveryRepo;
  private final RolePermissionService permissionService;
  private final DeliveryAccessGuard accessGuard;

  public CourierService(
      CourierRepository courierRepo,
      DeliveryRepository deliveryRepo,
      RolePermissionService permissionService,
      DeliveryAccessGuard accessGuard) {
    this.courierRepo = courierRepo;
    this.deliveryRepo = deliveryRepo;
    this.permissionService = permissionService;
    this.accessGuard = accessGuard;
  }

  @Transactional(readOnly = true)
  public List<CourierResponse> list(UserPrincipal p, boolean activeOnly) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    List<Courier> list =
        activeOnly
            ? courierRepo.findByBusinessIdAndIsActiveTrueOrderByNameAsc(p.businessId())
            : courierRepo.findByBusinessIdOrderByNameAsc(p.businessId());
    return list.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public CourierStatsResponse getStats(UUID courierId, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    requireCourier(p.businessId(), courierId);
    long totalParcels =
        deliveryRepo.sumParcelsDeliveredByCourierAndBusiness(courierId, p.businessId());
    long delivered = deliveryRepo.countDeliveredByCourierAndBusiness(courierId, p.businessId());
    long failed = deliveryRepo.countFailedByCourierAndBusiness(courierId, p.businessId());
    return statsOf(courierId, totalParcels, delivered, failed);
  }

  @Transactional(readOnly = true)
  public List<CourierStatsResponse> getAllStats(UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    List<Courier> couriers = courierRepo.findByBusinessIdOrderByNameAsc(p.businessId());
    Map<UUID, CourierStatsResponse> statsMap = new LinkedHashMap<>();
    for (Courier c : couriers) {
      statsMap.put(c.getId(), statsOf(c.getId(), 0L, 0L, 0L));
    }
    for (CourierDeliveryStatsRow row : deliveryRepo.findDeliveryStatsByBusinessId(p.businessId())) {
      UUID courierId = row.getCourierId();
      if (!statsMap.containsKey(courierId)) {
        continue;
      }
      statsMap.put(
          courierId,
          statsOf(
              courierId,
              nz(row.getTotalParcels()),
              nz(row.getDeliveredCount()),
              nz(row.getFailedCount())));
    }
    return couriers.stream().map(c -> statsMap.get(c.getId())).toList();
  }

  @Transactional(readOnly = true)
  public CourierResponse getById(UUID id, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_READ);
    return toResponse(requireCourier(p.businessId(), id));
  }

  @Transactional
  public CourierResponse create(CourierRequest req, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_CREATE);
    if (courierRepo.existsByBusinessIdAndNameIgnoreCase(p.businessId(), req.name())) {
      throw new ResourceAlreadyExistsException("Livreur", req.name());
    }
    Courier c = new Courier();
    c.setBusinessId(p.businessId());
    applyFields(c, req);
    return toResponse(courierRepo.save(c));
  }

  @Transactional
  public CourierResponse update(UUID id, CourierRequest req, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_UPDATE);
    Courier c = requireCourier(p.businessId(), id);
    if (courierRepo.existsByBusinessIdAndNameIgnoreCaseAndIdNot(
        p.businessId(), req.name(), id)) {
      throw new ResourceAlreadyExistsException("Livreur", req.name());
    }
    applyFields(c, req);
    return toResponse(courierRepo.save(c));
  }

  /**
   * Hard-deletes a courier with no history; otherwise deactivates so past
   * deliveries stay available for stats.
   */
  @Transactional
  public void delete(UUID id, UserPrincipal p) {
    accessGuard.requireDeliveryPlan(p);
    permissionService.require(p, Permission.DELIVERY_COURIERS_DELETE);
    Courier c = requireCourier(p.businessId(), id);
    if (deliveryRepo.existsByBusinessIdAndCourierId(p.businessId(), id)) {
      c.setIsActive(false);
      courierRepo.save(c);
      return;
    }
    courierRepo.delete(c);
  }

  private Courier requireCourier(UUID businessId, UUID id) {
    return courierRepo
        .findByBusinessIdAndId(businessId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Livreur", id));
  }

  private void applyFields(Courier c, CourierRequest req) {
    c.setName(req.name());
    c.setPhone(req.phone());
    c.setEmail(req.email());
    c.setIsActive(req.isActive());
  }

  private static CourierStatsResponse statsOf(
      UUID courierId, long totalParcels, long delivered, long failed) {
    long completed = delivered + failed;
    double successRatePercent = completed > 0 ? (100.0 * delivered / completed) : 100.0;
    return new CourierStatsResponse(
        courierId, totalParcels, delivered, failed, successRatePercent);
  }

  private static long nz(Long value) {
    return value != null ? value : 0L;
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
