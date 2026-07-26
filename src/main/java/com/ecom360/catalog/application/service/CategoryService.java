package com.ecom360.catalog.application.service;

import com.ecom360.catalog.application.dto.*;
import com.ecom360.catalog.domain.model.Category;
import com.ecom360.catalog.domain.repository.CategoryRepository;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import com.ecom360.shared.infrastructure.cache.CachedLookups;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
  private final CategoryRepository repo;
  private final ProductRepository productRepo;
  private final RolePermissionService permissionService;
  private final CachedLookups cachedLookups;

  public CategoryService(
      CategoryRepository repo,
      ProductRepository productRepo,
      RolePermissionService permissionService,
      CachedLookups cachedLookups) {
    this.repo = repo;
    this.productRepo = productRepo;
    this.permissionService = permissionService;
    this.cachedLookups = cachedLookups;
  }

  public CategoryResponse create(CategoryRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.CATEGORIES_CREATE);
    if (repo.existsByBusinessIdAndName(p.businessId(), r.name()))
      throw new ResourceAlreadyExistsException("Category", r.name());
    Category c = new Category();
    c.setBusinessId(p.businessId());
    c.setName(r.name());
    c.setColor(r.color());
    c.setSortOrder(r.sortOrder());
    CategoryResponse created = map(repo.save(c));
    cachedLookups.evictCategories(p.businessId());
    return created;
  }

  public List<CategoryResponse> list(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.CATEGORIES_READ);
    return cachedLookups.categoriesByBusiness(p.businessId());
  }

  public CategoryResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.CATEGORIES_READ);
    return map(find(id, p));
  }

  public CategoryResponse update(UUID id, CategoryRequest r, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.CATEGORIES_UPDATE);
    Category c = find(id, p);
    if (!c.getName().equals(r.name()) && repo.existsByBusinessIdAndName(p.businessId(), r.name()))
      throw new ResourceAlreadyExistsException("Category", r.name());
    c.setName(r.name());
    c.setColor(r.color());
    c.setSortOrder(r.sortOrder());
    CategoryResponse updated = map(repo.save(c));
    cachedLookups.evictCategories(p.businessId());
    return updated;
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.CATEGORIES_DELETE);
    Category c = find(id, p);
    long productCount = productRepo.countByBusinessIdAndCategoryIdAndIsActive(p.businessId(), id, true);
    if (productCount > 0) {
      throw new BusinessRuleException(
          "Impossible de supprimer cette catégorie : " + productCount + " produit(s) l'utilisent.");
    }
    repo.delete(c);
    cachedLookups.evictCategories(p.businessId());
  }

  private Category find(UUID id, UserPrincipal p) {
    return repo.findById(id)
        .filter(c -> c.getBusinessId().equals(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Category", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }

  private CategoryResponse map(Category c) {
    return new CategoryResponse(
        c.getId(),
        c.getBusinessId(),
        c.getName(),
        c.getColor(),
        c.getSortOrder(),
        c.getCreatedAt());
  }
}
