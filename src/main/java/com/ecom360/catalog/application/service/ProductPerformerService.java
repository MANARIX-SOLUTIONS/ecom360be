package com.ecom360.catalog.application.service;

import com.ecom360.catalog.application.dto.EligiblePerformerResponse;
import com.ecom360.catalog.application.dto.PerformerSnapshot;
import com.ecom360.catalog.application.dto.ProductPerformerResponse;
import com.ecom360.catalog.domain.ServiceUnits;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.model.ProductPerformer;
import com.ecom360.catalog.domain.repository.ProductPerformerRepository;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.BusinessUserStore;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.BusinessUserStoreRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductPerformerService {
  private final ProductRepository productRepo;
  private final ProductPerformerRepository performerRepo;
  private final BusinessUserRepository businessUserRepo;
  private final BusinessUserStoreRepository businessUserStoreRepo;
  private final UserRepository userRepo;
  private final StoreRepository storeRepo;
  private final RolePermissionService permissionService;

  public ProductPerformerService(
      ProductRepository productRepo,
      ProductPerformerRepository performerRepo,
      BusinessUserRepository businessUserRepo,
      BusinessUserStoreRepository businessUserStoreRepo,
      UserRepository userRepo,
      StoreRepository storeRepo,
      RolePermissionService permissionService) {
    this.productRepo = productRepo;
    this.performerRepo = performerRepo;
    this.businessUserRepo = businessUserRepo;
    this.businessUserStoreRepo = businessUserStoreRepo;
    this.userRepo = userRepo;
    this.storeRepo = storeRepo;
    this.permissionService = permissionService;
  }

  public List<ProductPerformerResponse> list(UUID productId, UserPrincipal principal) {
    requireBiz(principal);
    permissionService.require(principal, Permission.PRODUCTS_READ);
    Product product = findProduct(productId, principal.businessId());
    return mapLinks(performerRepo.findByProductId(product.getId()));
  }

  @Transactional
  public List<ProductPerformerResponse> replace(
      UUID productId, List<UUID> businessUserIds, UserPrincipal principal) {
    requireBiz(principal);
    permissionService.require(principal, Permission.PRODUCTS_UPDATE);
    Product product = findProduct(productId, principal.businessId());
    if (!ServiceUnits.isService(product.getUnit())) {
      throw new BusinessRuleException(
          "Seules les prestations et les forfaits peuvent être attribués à un employé.");
    }
    Set<UUID> uniqueIds = new LinkedHashSet<>(businessUserIds);
    Map<UUID, BusinessUser> members =
        uniqueIds.isEmpty()
            ? Map.of()
            : businessUserRepo.findAllById(uniqueIds).stream()
                .filter(bu -> principal.businessId().equals(bu.getBusinessId()))
                .collect(Collectors.toMap(BusinessUser::getId, Function.identity()));
    for (UUID id : uniqueIds) {
      if (!members.containsKey(id)) {
        throw new ResourceNotFoundException("BusinessUser", id);
      }
    }
    performerRepo.deleteByProductId(product.getId());
    performerRepo.flush();
    for (UUID id : uniqueIds) {
      performerRepo.save(ProductPerformer.create(product.getId(), id));
    }
    return mapLinks(performerRepo.findByProductId(product.getId()));
  }

  /** Retire les habilitations dès que l'article n'est plus une prestation ou un forfait. */
  @Transactional
  public void dropIfNotService(Product product) {
    if (product.getId() == null || ServiceUnits.isService(product.getUnit())) {
      return;
    }
    performerRepo.deleteByProductId(product.getId());
  }

  public List<EligiblePerformerResponse> eligible(
      UUID storeId, UUID productId, UserPrincipal principal) {
    requireBiz(principal);
    permissionService.require(principal, Permission.SALES_CREATE);
    storeRepo
        .findById(storeId)
        .filter(s -> s.belongsTo(principal.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
    Product product = findProduct(productId, principal.businessId());
    if (!ServiceUnits.isService(product.getUnit())) {
      throw new BusinessRuleException(
          "Le choix d'un employé concerne une prestation ou un forfait.");
    }
    return eligibleMembers(principal.businessId(), storeId, product.getId()).stream()
        .map(m -> new EligiblePerformerResponse(m.businessUser().getId(), m.name()))
        .toList();
  }

  /**
   * Règle d'encaissement. {@code null} pour un article retail sans intervenant. L'import commerce
   * n'appelle pas cette méthode.
   */
  public PerformerSnapshot resolveForPosSale(
      UUID businessId, UUID storeId, Product product, UUID performerBusinessUserId) {
    boolean service = ServiceUnits.isService(product.getUnit());
    if (!service) {
      if (performerBusinessUserId != null) {
        throw new BusinessRuleException(
            "Un article autre qu'une prestation ne porte pas d'employé.");
      }
      return null;
    }
    if (performerBusinessUserId == null) {
      throw new BusinessRuleException(
          "Choisissez l'employé qui réalise « " + product.getName() + " ».");
    }
    BusinessUser member =
        businessUserRepo
            .findById(performerBusinessUserId)
            .filter(bu -> businessId.equals(bu.getBusinessId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("BusinessUser", performerBusinessUserId));
    User user = userRepo.findById(member.getUserId()).orElse(null);
    if (!isActiveMember(member, user)) {
      throw new BusinessRuleException("Cet employé n'est pas actif.");
    }
    if (!performerRepo.existsByProductIdAndBusinessUserId(product.getId(), member.getId())) {
      throw new BusinessRuleException(
          "Cet employé n'est pas habilité pour « " + product.getName() + " ».");
    }
    if (!assignedToStore(member.getId(), storeId)) {
      throw new BusinessRuleException("Cet employé n'est pas affecté à ce salon.");
    }
    return new PerformerSnapshot(member.getId(), displayName(user));
  }

  private List<ActiveMember> eligibleMembers(UUID businessId, UUID storeId, UUID productId) {
    List<ProductPerformer> links = performerRepo.findByProductId(productId);
    if (links.isEmpty()) {
      return List.of();
    }
    List<UUID> memberIds = links.stream().map(ProductPerformer::getBusinessUserId).toList();
    Map<UUID, BusinessUser> members =
        businessUserRepo.findAllById(memberIds).stream()
            .filter(bu -> businessId.equals(bu.getBusinessId()))
            .collect(Collectors.toMap(BusinessUser::getId, Function.identity()));
    Set<UUID> userIds =
        members.values().stream().map(BusinessUser::getUserId).collect(Collectors.toSet());
    Map<UUID, User> users =
        userIds.isEmpty()
            ? Map.of()
            : userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<UUID, List<BusinessUserStore>> storesByMember =
        businessUserStoreRepo.findByBusinessUserIdIn(memberIds).stream()
            .collect(Collectors.groupingBy(BusinessUserStore::getBusinessUserId));
    List<ActiveMember> eligible = new ArrayList<>();
    for (UUID memberId : memberIds) {
      BusinessUser member = members.get(memberId);
      if (member == null) {
        continue;
      }
      User user = users.get(member.getUserId());
      if (!isActiveMember(member, user)) {
        continue;
      }
      if (!worksAtStore(storesByMember.getOrDefault(memberId, List.of()), storeId)) {
        continue;
      }
      eligible.add(new ActiveMember(member, displayName(user)));
    }
    return eligible;
  }

  private boolean assignedToStore(UUID businessUserId, UUID storeId) {
    return worksAtStore(businessUserStoreRepo.findByBusinessUserId(businessUserId), storeId);
  }

  /** Aucune affectation boutique = présent dans tous les salons. */
  private boolean worksAtStore(List<BusinessUserStore> assignments, UUID storeId) {
    if (assignments.isEmpty()) {
      return true;
    }
    return assignments.stream().anyMatch(row -> storeId.equals(row.getStoreId()));
  }

  private boolean isActiveMember(BusinessUser member, User user) {
    return member.isActive() && user != null && user.isActive();
  }

  private String displayName(User user) {
    if (user.getFullName() != null && !user.getFullName().isBlank()) {
      return user.getFullName().trim();
    }
    return user.getEmail();
  }

  private List<ProductPerformerResponse> mapLinks(List<ProductPerformer> links) {
    if (links.isEmpty()) {
      return List.of();
    }
    List<UUID> memberIds =
        links.stream().map(ProductPerformer::getBusinessUserId).distinct().toList();
    Map<UUID, BusinessUser> members =
        businessUserRepo.findAllById(memberIds).stream()
            .collect(Collectors.toMap(BusinessUser::getId, Function.identity()));
    Set<UUID> userIds =
        members.values().stream().map(BusinessUser::getUserId).collect(Collectors.toSet());
    Map<UUID, User> users =
        userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<ProductPerformerResponse> responses = new ArrayList<>();
    for (ProductPerformer link : links) {
      BusinessUser member = members.get(link.getBusinessUserId());
      if (member == null) {
        continue;
      }
      User user = users.get(member.getUserId());
      responses.add(
          new ProductPerformerResponse(
              member.getId(), user == null ? "" : displayName(user), isActiveMember(member, user)));
    }
    return responses;
  }

  private Product findProduct(UUID productId, UUID businessId) {
    return productRepo
        .findByBusinessIdAndId(businessId, productId)
        .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
  }

  private void requireBiz(UserPrincipal principal) {
    if (!principal.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }

  private record ActiveMember(BusinessUser businessUser, String name) {}
}
