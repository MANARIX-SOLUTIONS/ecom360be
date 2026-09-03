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
    validateParentForWrite(r.parentId(), null, p);
    if (nameExists(p.businessId(), r.parentId(), r.name()))
      throw new ResourceAlreadyExistsException("Category", r.name());
    Category c = new Category();
    c.setBusinessId(p.businessId());
    c.setParentId(r.parentId());
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
    validateParentForWrite(r.parentId(), id, p);
    if (!c.getName().equals(r.name()) && nameExists(p.businessId(), r.parentId(), r.name()))
      throw new ResourceAlreadyExistsException("Category", r.name());
    c.setParentId(r.parentId());
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
    long childCount = repo.countByBusinessIdAndParentId(p.businessId(), id);
    if (childCount > 0) {
      throw new BusinessRuleException(
          "Impossible de supprimer cette catégorie : "
              + childCount
              + " sous-catégorie(s) l'utilisent.");
    }
    long productCount =
        productRepo.countByBusinessIdAndCategoryIdAndIsActive(p.businessId(), id, true);
    if (productCount > 0) {
      throw new BusinessRuleException(
          "Impossible de supprimer cette catégorie : "
              + productCount
              + " produit(s) l'utilisent.");
    }
    repo.delete(c);
    cachedLookups.evictCategories(p.businessId());
  }

  public void requireLeafCategory(UUID categoryId, UUID businessId) {
    Category c =
        repo.findById(categoryId)
            .filter(cat -> cat.getBusinessId().equals(businessId))
            .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    if (repo.countByBusinessIdAndParentId(businessId, categoryId) > 0) {
      throw new BusinessRuleException(
          "Assignez le produit à une sous-catégorie, pas à une catégorie parente.");
    }
  }

  private void validateParentForWrite(UUID parentId, UUID categoryId, UserPrincipal p) {
    if (parentId == null) {
      if (categoryId != null && repo.countByBusinessIdAndParentId(p.businessId(), categoryId) > 0) {
        throw new BusinessRuleException(
            "Impossible de déplacer une catégorie parente sous une autre catégorie.");
      }
      return;
    }
    if (categoryId != null && parentId.equals(categoryId)) {
      throw new BusinessRuleException("Une catégorie ne peut pas être sa propre parente.");
    }
    Category parent = find(parentId, p);
    if (parent.getParentId() != null) {
      throw new BusinessRuleException(
          "Seules les catégories racine peuvent avoir des sous-catégories.");
    }
    if (categoryId != null && repo.countByBusinessIdAndParentId(p.businessId(), categoryId) > 0) {
      throw new BusinessRuleException(
          "Supprimez d'abord les sous-catégories avant de déplacer cette catégorie.");
    }
  }

  private boolean nameExists(UUID businessId, UUID parentId, String name) {
    if (parentId == null) {
      return repo.existsByBusinessIdAndParentIdIsNullAndName(businessId, name);
    }
    return repo.existsByBusinessIdAndParentIdAndName(businessId, parentId, name);
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
        c.getParentId(),
        c.getName(),
        c.getColor(),
        c.getSortOrder(),
        c.getCreatedAt());
  }
}
