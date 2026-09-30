package com.ecom360.tenant.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsRequest;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsResponse;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.domain.repository.BusinessPaymentProviderRepository;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysCredentials;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessPaymentProviderServiceTest {

  private static final String PLAN_MSG = "Paiement Wave / Orange Money en ligne réservé au plan Business";

  @Mock
  BusinessPaymentProviderRepository repository;
  @Mock
  SubscriptionService subscriptionService;
  @Mock
  BictorysClient bictorysClient;
  @Mock
  AuditLogService auditLogService;

  BusinessPaymentProviderService service;
  final UUID businessId = UUID.randomUUID();
  final UserPrincipal owner = new UserPrincipal(UUID.randomUUID(), "o@example.com", businessId, "proprietaire", null,
      false);
  final UserPrincipal cashier = new UserPrincipal(UUID.randomUUID(), "c@example.com", businessId, "caissier", null,
      false);

  @BeforeEach
  void setUp() {
    service = new BusinessPaymentProviderService(
        repository, subscriptionService, bictorysClient, auditLogService);
  }

  @Test
  void settings_onStarterOrPro_areRejected() {
    doThrow(new BusinessRuleException(PLAN_MSG))
        .when(subscriptionService).requirePosOnlinePayment(businessId);

    assertThatThrownBy(() -> service.getBictorys(owner)).hasMessage(PLAN_MSG);
    assertThatThrownBy(() -> service.saveBictorys(request("sk_1234567890", "wh_1234567890"), owner))
        .hasMessage(PLAN_MSG);
    assertThatThrownBy(() -> service.testBictorys(owner)).hasMessage(PLAN_MSG);
    verify(repository, never()).save(any());
    verifyNoInteractions(bictorysClient);
  }

  @Test
  void settings_areOwnerOnly() {
    assertThatThrownBy(() -> service.getBictorys(cashier)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.saveBictorys(request("k", "s"), cashier))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(subscriptionService, repository);
  }

  @Test
  void save_onBusinessPlan_createsAccountAndMasksKeys() {
    when(repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS))
        .thenReturn(Optional.empty());
    when(repository.save(any(BusinessPaymentProvider.class))).thenAnswer(inv -> inv.getArgument(0));

    BictorysSettingsResponse res = service.saveBictorys(request("sk_test_ABCDEFGH1234", "whsec_ZYXWVUT9876"), owner);

    assertThat(res.configured()).isTrue();
    assertThat(res.apiKeyMasked()).isEqualTo("sk_t••••1234").doesNotContain("ABCDEFGH");
    assertThat(res.webhookSecretMasked()).isEqualTo("whse••••9876");
    assertThat(res.webhookPath()).startsWith(BusinessPaymentProviderService.POS_WEBHOOK_PATH);
    assertThat(res.webhookPath().substring(BusinessPaymentProviderService.POS_WEBHOOK_PATH.length()))
        .hasSize(48);
  }

  @Test
  void save_withBlankKeys_keepsStoredValues() {
    BusinessPaymentProvider existing = new BusinessPaymentProvider();
    existing.setBusinessId(businessId);
    existing.setApiKey("sk_stored_key_0001");
    existing.setWebhookSecret("wh_stored_secret_01");
    existing.setWebhookToken("tok");
    when(repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS))
        .thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);

    service.saveBictorys(
        new BictorysSettingsRequest(" ", null, BusinessPaymentProvider.ENV_LIVE, "SN", true), owner);

    assertThat(existing.getApiKey()).isEqualTo("sk_stored_key_0001");
    assertThat(existing.getWebhookSecret()).isEqualTo("wh_stored_secret_01");
    assertThat(existing.isLive()).isTrue();
  }

  @Test
  void save_firstTimeWithoutSecret_isRejected() {
    when(repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.saveBictorys(request("sk_1234567890", null), owner))
        .isInstanceOf(BusinessRuleException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void requireUsableCredentials_withoutAccount_pointsToSettings() {
    when(repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireUsableCredentials(businessId))
        .hasMessage(BusinessPaymentProviderService.NOT_CONFIGURED);
  }

  @Test
  void requireUsableCredentials_disabledAccount_isRefused_butStatusSyncStillWorks() {
    BusinessPaymentProvider cfg = new BusinessPaymentProvider();
    cfg.setBusinessId(businessId);
    cfg.setApiKey("sk_key");
    cfg.setWebhookSecret("wh_secret");
    cfg.setEnabled(false);
    when(repository.findByBusinessIdAndProvider(businessId, BusinessPaymentProvider.BICTORYS))
        .thenReturn(Optional.of(cfg));

    assertThatThrownBy(() -> service.requireUsableCredentials(businessId))
        .isInstanceOf(BusinessRuleException.class);
    Optional<BictorysCredentials> creds = service.findCredentials(businessId);
    assertThat(creds).isPresent();
    assertThat(creds.get().apiKey()).isEqualTo("sk_key");
  }

  private static BictorysSettingsRequest request(String apiKey, String secret) {
    return new BictorysSettingsRequest(apiKey, secret, BusinessPaymentProvider.ENV_TEST, "SN", true);
  }
}
