package com.ecom360.catalog.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.catalog.application.dto.*;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.CategoryRepository;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
  private final ProductRepository productRepo;
  private final CategoryRepository categoryRepo;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;
  private final AuditLogService auditLogService;

  public ProductService(
      ProductRepository productRepo,
      CategoryRepository categoryRepo,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService,
      AuditLogService auditLogService) {
    this.productRepo = productRepo;
    this.categoryRepo = categoryRepo;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
    this.auditLogService = auditLogService;
  }

  public ProductResponse create(ProductRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.PRODUCTS_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxProducts())) {
                long count = productRepo.countByBusinessId(p.businessId());
                if (count >= plan.getMaxProducts()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxProducts()
                          + " produit(s). Passez à un plan supérieur.");
                }
              }
            });
    if (r.sku() != null
        && !r.sku().isBlank()
        && productRepo.existsByBusinessIdAndSku(p.businessId(), r.sku()))
      throw new ResourceAlreadyExistsException("Product with SKU", r.sku());
    if (r.categoryId() != null
        && categoryRepo.findByBusinessIdOrderBySortOrderAsc(p.businessId()).stream()
            .noneMatch(c -> c.getId().equals(r.categoryId())))
      throw new ResourceNotFoundException("Category", r.categoryId());
    Product prod = new Product();
    prod.setBusinessId(p.businessId());
    applyFields(prod, r);
    Product saved = productRepo.save(prod);
    auditLogService.logAsync(
        p.businessId(),
        p.userId(),
        "CREATE",
        "Product",
        saved.getId(),
        Map.of("name", saved.getName(), "sku", saved.getSku() != null ? saved.getSku() : ""));
    return map(saved);
  }

  public ProductResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.PRODUCTS_READ);
    return map(find(id, p));
  }

  public Page<ProductResponse> list(UserPrincipal p, Pageable pg, String search) {
    requireBiz(p);
    permissionService.require(p, Permission.PRODUCTS_READ);
    Page<Product> page =
        (search != null && !search.isBlank())
            ? productRepo.searchByBusinessId(p.businessId(), search.trim(), pg)
            : productRepo.findByBusinessIdAndIsActive(p.businessId(), true, pg);
    return page.map(this::map);
  }

  @Transactional
  public ProductResponse update(UUID id, ProductRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.PRODUCTS_UPDATE);
    Product prod = find(id, p);
    if (r.sku() != null
        && !r.sku().isBlank()
        && !r.sku().equals(prod.getSku())
        && productRepo.existsByBusinessIdAndSku(p.businessId(), r.sku()))
      throw new ResourceAlreadyExistsException("Product with SKU", r.sku());
    if (r.categoryId() != null
        && categoryRepo.findByBusinessIdOrderBySortOrderAsc(p.businessId()).stream()
            .noneMatch(c -> c.getId().equals(r.categoryId())))
      throw new ResourceNotFoundException("Category", r.categoryId());
    applyFields(prod, r);
    Product saved = productRepo.save(prod);
    auditLogService.logAsync(
        p.businessId(),
        p.userId(),
        "UPDATE",
        "Product",
        id,
        Map.of("name", saved.getName()));
    return map(saved);
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.PRODUCTS_DELETE);
    Product prod = find(id, p);
    auditLogService.logAsync(
        p.businessId(),
        p.userId(),
        "DELETE",
        "Product",
        id,
        Map.of("name", prod.getName()));
    productRepo.delete(prod);
  }

  private Product find(UUID id, UserPrincipal p) {
    return productRepo
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Product", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private void applyFields(Product prod, ProductRequest r) {
    prod.setCategoryId(r.categoryId());
    prod.setName(r.name());
    prod.setSku(r.sku());
    prod.setBarcode(r.barcode());
    prod.setDescription(r.description());
    prod.setCostPrice(r.costPrice());
    prod.setSalePrice(r.salePrice());
    prod.setUnit(r.unit());
    prod.setImageUrl(r.imageUrl());
    prod.setIsActive(r.isActive());
  }

  private ProductResponse map(Product p) {
    return new ProductResponse(
        p.getId(),
        p.getBusinessId(),
        p.getCategoryId(),
        p.getName(),
        p.getSku(),
        p.getBarcode(),
        p.getDescription(),
        p.getCostPrice(),
        p.getSalePrice(),
        p.getUnit(),
        p.getImageUrl(),
        p.getIsActive(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
