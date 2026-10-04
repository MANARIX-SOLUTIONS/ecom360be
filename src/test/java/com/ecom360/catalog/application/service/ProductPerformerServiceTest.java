package com.ecom360.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.catalog.application.dto.EligiblePerformerResponse;
import com.ecom360.catalog.application.dto.PerformerSnapshot;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.model.ProductPerformer;
import com.ecom360.catalog.domain.repository.ProductPerformerRepository;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.BusinessUserStore;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.BusinessUserStoreRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPerformerServiceTest {

  @Mock private ProductRepository productRepo;
  @Mock private ProductPerformerRepository performerRepo;
  @Mock private BusinessUserRepository businessUserRepo;
  @Mock private BusinessUserStoreRepository businessUserStoreRepo;
  @Mock private UserRepository userRepo;
  @Mock private StoreRepository storeRepo;
  @Mock private RolePermissionService permissionService;

  private ProductPerformerService service;
  private UUID businessId;
  private UUID storeId;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    service =
        new ProductPerformerService(
            productRepo,
            performerRepo,
            businessUserRepo,
            businessUserStoreRepo,
            userRepo,
            storeRepo,
            permissionService);
    businessId = UUID.randomUUID();
    storeId = UUID.randomUUID();
    principal =
        new UserPrincipal(UUID.randomUUID(), "owner@test.sn", businessId, "OWNER", null, false);
  }

  @Test
  void replaceRefusesRetailProduct() {
    Product product = product("pièce");
    when(productRepo.findByBusinessIdAndId(businessId, product.getId()))
        .thenReturn(Optional.of(product));

    assertThatThrownBy(
            () -> service.replace(product.getId(), List.of(UUID.randomUUID()), principal))
        .isInstanceOf(BusinessRuleException.class);
    verify(performerRepo, never()).save(any());
  }

  @Test
  void dropIfNotServiceDeletesLinksOfRetailProduct() {
    Product product = product("pièce");

    service.dropIfNotService(product);

    verify(performerRepo).deleteByProductId(product.getId());
  }

  @Test
  void dropIfNotServiceKeepsLinksOfService() {
    Product product = product(" Forfait ");

    service.dropIfNotService(product);

    verify(performerRepo, never()).deleteByProductId(any());
  }

  @Test
  void serviceLineWithoutPerformerIsRefused() {
    Product product = product("prestation");

    assertThatThrownBy(() -> service.resolveForPosSale(businessId, storeId, product, null))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Choisissez");
  }

  @Test
  void retailLineWithPerformerIsRefused() {
    Product product = product("pièce");

    assertThatThrownBy(
            () -> service.resolveForPosSale(businessId, storeId, product, UUID.randomUUID()))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void retailLineWithoutPerformerHasNoSnapshot() {
    assertThat(service.resolveForPosSale(businessId, storeId, product("pièce"), null)).isNull();
  }

  @Test
  void performerNotHabilitatedIsRefused() {
    Product product = product("prestation");
    BusinessUser member = activeMember("Awa Diop");
    when(performerRepo.existsByProductIdAndBusinessUserId(product.getId(), member.getId()))
        .thenReturn(false);

    assertThatThrownBy(
            () -> service.resolveForPosSale(businessId, storeId, product, member.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("habilité");
  }

  @Test
  void inactiveMemberIsRefused() {
    Product product = product("prestation");
    BusinessUser member = activeMember("Awa Diop");
    member.setIsActive(false);

    assertThatThrownBy(
            () -> service.resolveForPosSale(businessId, storeId, product, member.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("actif");
  }

  @Test
  void inactiveUserIsRefused() {
    Product product = product("prestation");
    BusinessUser member = activeMember("Awa Diop");
    userRepo.findById(member.getUserId()).orElseThrow().setIsActive(false);

    assertThatThrownBy(
            () -> service.resolveForPosSale(businessId, storeId, product, member.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("actif");
  }

  @Test
  void memberAssignedToAnotherStoreIsRefused() {
    Product product = product("prestation");
    BusinessUser member = habilitated(product, activeMember("Awa Diop"));
    when(businessUserStoreRepo.findByBusinessUserId(member.getId()))
        .thenReturn(List.of(BusinessUserStore.create(member.getId(), UUID.randomUUID())));

    assertThatThrownBy(
            () -> service.resolveForPosSale(businessId, storeId, product, member.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("salon");
  }

  @Test
  void habilitatedMemberWithoutStoreAssignmentIsAccepted() {
    Product product = product("prestation");
    BusinessUser member = habilitated(product, activeMember("Awa Diop"));
    when(businessUserStoreRepo.findByBusinessUserId(member.getId())).thenReturn(List.of());

    PerformerSnapshot snapshot =
        service.resolveForPosSale(businessId, storeId, product, member.getId());

    assertThat(snapshot).isEqualTo(new PerformerSnapshot(member.getId(), "Awa Diop"));
  }

  @Test
  void twoHabilitatedMembersCanPerformTheSameService() {
    Product product = product("prestation");
    BusinessUser awa = habilitated(product, activeMember("Awa Diop"));
    BusinessUser fatou = habilitated(product, activeMember("Fatou Sow"));
    when(businessUserStoreRepo.findByBusinessUserId(awa.getId()))
        .thenReturn(List.of(BusinessUserStore.create(awa.getId(), storeId)));
    when(businessUserStoreRepo.findByBusinessUserId(fatou.getId())).thenReturn(List.of());

    assertThat(service.resolveForPosSale(businessId, storeId, product, awa.getId()).name())
        .isEqualTo("Awa Diop");
    assertThat(service.resolveForPosSale(businessId, storeId, product, fatou.getId()).name())
        .isEqualTo("Fatou Sow");
  }

  @Test
  void eligibleKeepsActiveMembersPresentInStore() {
    Product product = product("prestation");
    when(productRepo.findByBusinessIdAndId(businessId, product.getId()))
        .thenReturn(Optional.of(product));
    Store store = Store.create(businessId, "Salon", null, null);
    when(storeRepo.findById(storeId)).thenReturn(Optional.of(store));
    BusinessUser awa = member();
    BusinessUser away = member();
    BusinessUser inactive = member();
    inactive.setIsActive(false);
    when(performerRepo.findByProductId(product.getId()))
        .thenReturn(
            List.of(
                ProductPerformer.create(product.getId(), awa.getId()),
                ProductPerformer.create(product.getId(), away.getId()),
                ProductPerformer.create(product.getId(), inactive.getId())));
    when(businessUserRepo.findAllById(any())).thenReturn(List.of(awa, away, inactive));
    when(userRepo.findAllById(any()))
        .thenReturn(
            List.of(
                user(awa.getUserId(), "Awa Diop"),
                user(away.getUserId(), "Ndeye Fall"),
                user(inactive.getUserId(), "Moussa Ba")));
    when(businessUserStoreRepo.findByBusinessUserIdIn(any()))
        .thenReturn(List.of(BusinessUserStore.create(away.getId(), UUID.randomUUID())));

    List<EligiblePerformerResponse> eligible =
        service.eligible(storeId, product.getId(), principal);

    assertThat(eligible).containsExactly(new EligiblePerformerResponse(awa.getId(), "Awa Diop"));
  }

  private Product product(String unit) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setBusinessId(businessId);
    product.setStoreId(storeId);
    product.setName("Tresses");
    product.setUnit(unit);
    return product;
  }

  private BusinessUser member() {
    BusinessUser member = BusinessUser.create(businessId, UUID.randomUUID(), null);
    member.setId(UUID.randomUUID());
    return member;
  }

  private User user(UUID id, String fullName) {
    User user = User.create(fullName, fullName.replace(' ', '.') + "@test.sn", "x", null);
    user.setId(id);
    return user;
  }

  private BusinessUser activeMember(String fullName) {
    BusinessUser member = member();
    User user = user(member.getUserId(), fullName);
    when(businessUserRepo.findById(member.getId())).thenReturn(Optional.of(member));
    when(userRepo.findById(member.getUserId())).thenReturn(Optional.of(user));
    return member;
  }

  private BusinessUser habilitated(Product product, BusinessUser member) {
    when(performerRepo.existsByProductIdAndBusinessUserId(product.getId(), member.getId()))
        .thenReturn(true);
    return member;
  }
}
