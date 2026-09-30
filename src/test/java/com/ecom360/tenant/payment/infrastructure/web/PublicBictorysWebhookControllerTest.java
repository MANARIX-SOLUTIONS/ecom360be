package com.ecom360.tenant.payment.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecom360.sales.application.service.PosDigitalCheckoutService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.payment.application.service.BusinessPaymentProviderService;
import com.ecom360.tenant.payment.application.service.SubscriptionCheckoutService;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysCredentials;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicBictorysWebhookControllerTest {

  private static final String BASE = ApiConstants.API_BASE + "/public/payments/bictorys";
  private static final String PLATFORM_SECRET = "platform_secret";
  private static final String BODY =
      "{\"id\":\"tx_1\",\"type\":\"payment\",\"status\":\"succeeded\",\"amount\":5000}";

  @Mock SubscriptionCheckoutService subscriptionCheckoutService;
  @Mock PosDigitalCheckoutService posCheckoutService;
  @Mock BusinessPaymentProviderService providerService;

  MockMvc mvc;
  final BusinessPaymentProvider accountA = account();
  final BictorysCredentials credsA =
      BictorysCredentials.forEnvironment(false, "key_a", "secret_a", "SN");

  @BeforeEach
  void setUp() {
    BictorysProperties props = new BictorysProperties();
    props.setEnabled(true);
    props.setApiKey("platform_key");
    props.setWebhookSecret(PLATFORM_SECRET);
    ObjectMapper mapper = new ObjectMapper();
    PublicBictorysWebhookController controller = new PublicBictorysWebhookController(
        subscriptionCheckoutService,
        posCheckoutService,
        providerService,
        new BictorysClient(props, mapper),
        mapper);
    mvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void posWebhook_signedWithTheAccountSecret_isProcessed() throws Exception {
    stubAccountA();

    mvc.perform(signed(post(BASE + "/pos/token_a"), "secret_a")).andExpect(status().isOk());

    verify(posCheckoutService).handleWebhook(eq(accountA), any());
    verifyNoInteractions(subscriptionCheckoutService);
  }

  @Test
  void posWebhook_signedWithAnotherBusinessSecret_isRejected() throws Exception {
    stubAccountA();

    mvc.perform(signed(post(BASE + "/pos/token_a"), "secret_b")).andExpect(status().isOk());

    verifyNoInteractions(posCheckoutService, subscriptionCheckoutService);
  }

  @Test
  void posWebhook_signedWithThePlatformSecret_isRejected() throws Exception {
    stubAccountA();

    mvc.perform(signed(post(BASE + "/pos/token_a"), PLATFORM_SECRET)).andExpect(status().isOk());

    verifyNoInteractions(posCheckoutService);
  }

  @Test
  void posWebhook_unknownToken_isAcknowledgedAndIgnored() throws Exception {
    when(providerService.findByWebhookToken("nope")).thenReturn(Optional.empty());

    mvc.perform(signed(post(BASE + "/pos/nope"), "secret_a")).andExpect(status().isOk());

    verifyNoInteractions(posCheckoutService);
  }

  @Test
  void posWebhook_unknownIntent_asksBictorysToRetry() throws Exception {
    stubAccountA();
    doThrow(new PaymentIntentNotFoundException("missing"))
        .when(posCheckoutService).handleWebhook(eq(accountA), any());

    mvc.perform(signed(post(BASE + "/pos/token_a"), "secret_a"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void subscriptionWebhook_staysOnThePlatformAccount() throws Exception {
    mvc.perform(signed(post(BASE + "/webhook"), PLATFORM_SECRET)).andExpect(status().isOk());

    verify(subscriptionCheckoutService).handleBictorysWebhook(any());
    verifyNoInteractions(posCheckoutService, providerService);
  }

  @Test
  void subscriptionWebhook_signedWithABusinessSecret_isRejected() throws Exception {
    mvc.perform(signed(post(BASE + "/webhook"), "secret_a")).andExpect(status().isOk());

    verifyNoInteractions(subscriptionCheckoutService, posCheckoutService);
  }

  private void stubAccountA() {
    when(providerService.findByWebhookToken("token_a")).thenReturn(Optional.of(accountA));
    when(providerService.credentials(accountA)).thenReturn(credsA);
  }

  private static MockHttpServletRequestBuilder signed(
      MockHttpServletRequestBuilder req, String secret) {
    String ts = String.valueOf(System.currentTimeMillis());
    return req.contentType(MediaType.APPLICATION_JSON)
        .content(BODY)
        .header("X-Webhook-Timestamp", ts)
        .header("X-Webhook-Signature", hmacHex(secret, ts + "." + BODY));
  }

  private static String hmacHex(String secret, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static BusinessPaymentProvider account() {
    BusinessPaymentProvider account = new BusinessPaymentProvider();
    account.setBusinessId(UUID.randomUUID());
    return account;
  }
}
