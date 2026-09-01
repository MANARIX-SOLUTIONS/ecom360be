package com.ecom360.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.application.dto.BusinessThemeRequest;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.infrastructure.storage.BusinessLogoStorageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {

  @Mock
  BusinessRepository businessRepository;
  @Mock
  SubscriptionService subscriptionService;
  @Mock
  BusinessLogoStorageService logoStorage;

  BusinessProfileService service;
  UUID businessId = UUID.randomUUID();
  UUID userId = UUID.randomUUID();
  Business business;

  @BeforeEach
  void setUp() {
    service = new BusinessProfileService(businessRepository, subscriptionService, logoStorage);
    business = Business.create("Acme", "acme@test.com");
    business.setId(businessId);
  }

  private UserPrincipal owner() {
    return new UserPrincipal(userId, "owner@test.com", businessId, "proprietaire", null, false);
  }

  private UserPrincipal cashier() {
    return new UserPrincipal(userId, "caisse@test.com", businessId, "caissier", null, false);
  }

  private UserPrincipal platformAdmin() {
    return new UserPrincipal(userId, "admin@test.com", businessId, "PLATFORM_ADMIN", null, true);
  }

  private void stubBusinessAndSave() {
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void stubPlan(boolean customBranding) {
    Plan plan = new Plan();
    plan.setFeatureCustomBranding(customBranding);
    when(subscriptionService.getPlanForBusiness(businessId)).thenReturn(Optional.of(plan));
  }

  @Test
  void cashierCannotUpdateTheme() {
    assertThatThrownBy(
        () -> service.updateTheme(new BusinessThemeRequest("#166534", "#10b981"), cashier()))
        .isInstanceOf(AccessDeniedException.class);
    verify(businessRepository, never()).save(any());
  }

  @Test
  void ownerWithoutBrandingCannotSetColors() {
    stubPlan(false);
    assertThatThrownBy(
        () -> service.updateTheme(new BusinessThemeRequest("#166534", "#10b981"), owner()))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("plan Business");
  }

  @Test
  void ownerWithoutBrandingCanResetToDefault() {
    stubBusinessAndSave();
    business.setThemePrimaryColor("#166534");
    business.setThemeAccentColor("#10b981");
    var res = service.updateTheme(new BusinessThemeRequest(null, null), owner());
    assertThat(res.themePrimaryColor()).isNull();
    assertThat(res.themeAccentColor()).isNull();
  }

  @Test
  void ownerWithBrandingSavesNormalizedHex() {
    stubBusinessAndSave();
    stubPlan(true);
    var res = service.updateTheme(new BusinessThemeRequest("  #166534  ", "#10B981"), owner());
    assertThat(res.themePrimaryColor()).isEqualTo("#166534");
    assertThat(res.themeAccentColor()).isEqualTo("#10b981");
  }

  @Test
  void platformAdminCanUpdateTheme() {
    stubBusinessAndSave();
    stubPlan(true);
    var res = service.updateTheme(new BusinessThemeRequest("#334155", "#6366f1"), platformAdmin());
    assertThat(res.themePrimaryColor()).isEqualTo("#334155");
  }

  @Test
  void invalidHexIsRejected() {
    stubPlan(true);
    assertThatThrownBy(
        () -> service.updateTheme(new BusinessThemeRequest("navy", "#10b981"), owner()))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void lowContrastPrimaryIsRejected() {
    stubPlan(true);
    assertThatThrownBy(
        () -> service.updateTheme(new BusinessThemeRequest("#ffff00", "#0ea5e9"), owner()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("trop claire");
  }
}
