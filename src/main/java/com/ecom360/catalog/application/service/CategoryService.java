package com.ecom360.catalog.application.service;

import com.ecom360.catalog.application.dto.*;
import com.ecom360.catalog.domain.model.Category;
import com.ecom360.catalog.domain.repository.CategoryRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.*;
import org.springframework.stereotype.Service;
import java.util.List; import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    public CategoryService(CategoryRepository repo) { this.repo = repo; }

    public CategoryResponse create(CategoryRequest r, UserPrincipal p) {
        requireBiz(p);
        if (repo.existsByBusinessIdAndName(p.businessId(), r.name())) throw new ResourceAlreadyExistsException("Category", r.name());
        Category c = new Category(); c.setBusinessId(p.businessId()); c.setName(r.name()); c.setColor(r.color()); c.setSortOrder(r.sortOrder());
        return map(repo.save(c));
    }
    public List<CategoryResponse> list(UserPrincipal p) { requireBiz(p); return repo.findByBusinessIdOrderBySortOrderAsc(p.businessId()).stream().map(this::map).toList(); }
    public CategoryResponse getById(UUID id, UserPrincipal p) { requireBiz(p); return map(find(id, p)); }
    public CategoryResponse update(UUID id, CategoryRequest r, UserPrincipal p) {
        requireBiz(p); Category c = find(id, p);
        if (!c.getName().equals(r.name()) && repo.existsByBusinessIdAndName(p.businessId(), r.name())) throw new ResourceAlreadyExistsException("Category", r.name());
        c.setName(r.name()); c.setColor(r.color()); c.setSortOrder(r.sortOrder());
        return map(repo.save(c));
    }
    public void delete(UUID id, UserPrincipal p) { requireBiz(p); repo.delete(find(id, p)); }

    private Category find(UUID id, UserPrincipal p) { return repo.findById(id).filter(c -> c.getBusinessId().equals(p.businessId())).orElseThrow(() -> new ResourceNotFoundException("Category", id)); }
    private void requireBiz(UserPrincipal p) { if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required"); }
    private CategoryResponse map(Category c) { return new CategoryResponse(c.getId(), c.getBusinessId(), c.getName(), c.getColor(), c.getSortOrder(), c.getCreatedAt()); }
}
