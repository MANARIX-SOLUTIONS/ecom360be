package com.ecom360.supplier.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.supplier.application.dto.*;
import com.ecom360.supplier.domain.model.*;
import com.ecom360.supplier.domain.repository.*;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {
  private final SupplierRepository supplierRepo;
  private final SupplierPaymentRepository paymentRepo;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;

  public SupplierService(
      SupplierRepository supplierRepo,
      SupplierPaymentRepository paymentRepo,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService) {
    this.supplierRepo = supplierRepo;
    this.paymentRepo = paymentRepo;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
  }

  public SupplierResponse create(SupplierRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxSuppliers())) {
                long count = supplierRepo.countByBusinessId(p.businessId());
                if (count >= plan.getMaxSuppliers()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxSuppliers()
                          + " fournisseur(s). Passez à un plan supérieur.");
                }
              }
            });
    Supplier s = new Supplier();
    s.setBusinessId(p.businessId());
    s.setName(r.name());
    s.setPhone(r.phone());
    s.setEmail(r.email());
    s.setZone(r.zone());
    s.setAddress(r.address());
    s.setIsActive(r.isActive());
    return map(supplierRepo.save(s));
  }

  public Page<SupplierResponse> list(UserPrincipal p, Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_READ);
    return supplierRepo.findByBusinessIdAndIsActive(p.businessId(), true, pg).map(this::map);
  }

  public SupplierResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_READ);
    return map(find(id, p));
  }

  public SupplierResponse update(UUID id, SupplierRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_UPDATE);
    Supplier s = find(id, p);
    s.setName(r.name());
    s.setPhone(r.phone());
    s.setEmail(r.email());
    s.setZone(r.zone());
    s.setAddress(r.address());
    s.setIsActive(r.isActive());
    return map(supplierRepo.save(s));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_DELETE);
    supplierRepo.delete(find(id, p));
  }

  @Transactional
  public SupplierPaymentResponse recordPayment(
      UUID supplierId, SupplierPaymentRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_UPDATE);
    Supplier s = find(supplierId, p);
    s.deductFromBalance(r.amount());
    supplierRepo.save(s);
    SupplierPayment pay = new SupplierPayment();
    pay.setSupplierId(supplierId);
    pay.setUserId(p.userId());
    pay.setAmount(r.amount());
    pay.setPaymentMethod(r.paymentMethod());
    pay.setNote(r.note());
    pay = paymentRepo.save(pay);
    return new SupplierPaymentResponse(
        pay.getId(),
        pay.getSupplierId(),
        pay.getUserId(),
        pay.getAmount(),
        pay.getPaymentMethod(),
        pay.getNote(),
        pay.getCreatedAt());
  }

  public Page<SupplierPaymentResponse> getPayments(UUID supplierId, UserPrincipal p, Pageable pg) {
    requireBiz(p);
    permissionService.require(p, Permission.SUPPLIERS_READ);
    find(supplierId, p);
    return paymentRepo
        .findBySupplierIdOrderByCreatedAtDesc(supplierId, pg)
        .map(
            pay ->
                new SupplierPaymentResponse(
                    pay.getId(),
                    pay.getSupplierId(),
                    pay.getUserId(),
                    pay.getAmount(),
                    pay.getPaymentMethod(),
                    pay.getNote(),
                    pay.getCreatedAt()));
  }

  private Supplier find(UUID id, UserPrincipal p) {
    return supplierRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private SupplierResponse map(Supplier s) {
    return new SupplierResponse(
        s.getId(),
        s.getBusinessId(),
        s.getName(),
        s.getPhone(),
        s.getEmail(),
        s.getZone(),
        s.getAddress(),
        s.getBalance(),
        s.getIsActive(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
