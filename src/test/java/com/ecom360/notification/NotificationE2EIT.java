package com.ecom360.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecom360.Ecom360Application;
import com.ecom360.identity.application.dto.ProvisionedTenant;
import com.ecom360.identity.application.service.AuthService;
import com.ecom360.identity.infrastructure.security.JwtService;
import com.ecom360.notification.application.dto.NotificationResponse;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.tenant.application.service.BillingReminderService;
import com.ecom360.tenant.application.service.SubscriptionReminderService;
import com.ecom360.tenant.domain.model.Invoice;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import com.ecom360.tenant.domain.repository.InvoiceRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flux bout-en-bout : provisioning tenant → notification bienvenue via API ; puis rappel d’échéance
 * d’essai/abonnement (service + relisting API).
 */
@SpringBootTest(
    classes = Ecom360Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.subscription.billing-reminders-enabled=true"})
@ActiveProfiles("test")
class NotificationE2EIT {

  private static final String TEST_DB = "ecom360_test_it";

  @DynamicPropertySource
  static void dbProps(DynamicPropertyRegistry registry) {
    ensureDatabaseExists();
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
  }

  private static void ensureDatabaseExists() {
    try (Connection conn =
            DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres");
        Statement checkStmt = conn.createStatement();
        ResultSet rs =
            checkStmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + TEST_DB + "'")) {
      if (!rs.next()) {
        try (Statement createStmt = conn.createStatement()) {
          createStmt.execute("CREATE DATABASE " + TEST_DB);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to prepare test database " + TEST_DB, e);
    }
  }

  @LocalServerPort int port;

  @Autowired AuthService authService;
  @Autowired JwtService jwtService;
  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired BusinessRoleRepository businessRoleRepository;
  @Autowired SubscriptionReminderService subscriptionReminderService;
  @Autowired BillingReminderService billingReminderService;
  @Autowired InvoiceRepository invoiceRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestRestTemplate restTemplate;

  private String bearer(UUID userId, String email, UUID businessId, UUID roleId) {
    return jwtService.generateAccessToken(userId, email, businessId, "PROPRIETAIRE", roleId, false);
  }

  private ResponseEntity<PageResponse<NotificationResponse>> getNotifications(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    String url = "http://localhost:%d/api/v1/notifications?page=0&size=50".formatted(port);
    return restTemplate.exchange(
        url,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        new ParameterizedTypeReference<PageResponse<NotificationResponse>>() {});
  }

  @Test
  void welcomeThenSubscriptionReminder_flowThroughHttpApi() {
    String uniq = UUID.randomUUID().toString().substring(0, 8);
    String email = "notif-e2e-" + uniq + "@example.com";

    ProvisionedTenant tenant =
        authService.provisionTenantAfterDemoApproval(
            "Notif Tester",
            email,
            passwordEncoder.encode("NotifE2E!1"),
            "+221770000001",
            "Boutique " + uniq);

    UUID roleId =
        businessRoleRepository
            .findByBusinessIdAndCode(tenant.businessId(), "PROPRIETAIRE")
            .orElseThrow()
            .getId();

    String token = bearer(tenant.userId(), email, tenant.businessId(), roleId);

    ResponseEntity<PageResponse<NotificationResponse>> first = getNotifications(token);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getBody()).isNotNull();
    assertThat(first.getBody().content())
        .anyMatch(n -> "system".equals(n.type()) && n.title().contains("Bienvenue"));

    Subscription sub =
        subscriptionRepository
            .findFirstByBusinessIdOrderByCreatedAtDesc(tenant.businessId())
            .orElseThrow();
    sub.setCurrentPeriodEnd(LocalDate.now().plusDays(7));
    subscriptionRepository.save(sub);

    subscriptionReminderService.sendRemindersFor(LocalDate.now());

    ResponseEntity<PageResponse<NotificationResponse>> second = getNotifications(token);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody()).isNotNull();
    assertThat(second.getBody().content())
        .anyMatch(
            n ->
                "subscription".equals(n.type())
                    && !n.title().isBlank()
                    && n.actionUrl() != null
                    && n.actionUrl().endsWith("/settings/subscription"));

    assertThat(second.getBody().content().stream().filter(n -> "subscription".equals(n.type())))
        .hasSize(1);

    subscriptionReminderService.sendRemindersFor(LocalDate.now());
    ResponseEntity<PageResponse<NotificationResponse>> third = getNotifications(token);
    assertThat(third.getBody()).isNotNull();
    assertThat(third.getBody().content().stream().filter(n -> "subscription".equals(n.type())))
        .hasSize(1);
  }

  @Test
  void billingReminder_dueInvoice_flowThroughHttpApi() {
    String uniq = UUID.randomUUID().toString().substring(0, 8);
    String email = "notif-billing-" + uniq + "@example.com";

    ProvisionedTenant tenant =
        authService.provisionTenantAfterDemoApproval(
            "Billing Tester",
            email,
            passwordEncoder.encode("BillingE2E!1"),
            "+221770000002",
            "Facture " + uniq);

    UUID roleId =
        businessRoleRepository
            .findByBusinessIdAndCode(tenant.businessId(), "PROPRIETAIRE")
            .orElseThrow()
            .getId();
    String token = bearer(tenant.userId(), email, tenant.businessId(), roleId);

    Subscription sub =
        subscriptionRepository
            .findFirstByBusinessIdOrderByCreatedAtDesc(tenant.businessId())
            .orElseThrow();

    Invoice inv = new Invoice();
    inv.setBusinessId(tenant.businessId());
    inv.setSubscriptionId(sub.getId());
    inv.setNumber("INV-E2E-" + uniq);
    inv.setAmount(50000);
    inv.setStatus("draft");
    inv.setDueDate(LocalDate.now().plusDays(7));
    invoiceRepository.save(inv);

    billingReminderService.sendBillingRemindersFor(LocalDate.now());

    ResponseEntity<PageResponse<NotificationResponse>> first = getNotifications(token);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getBody()).isNotNull();
    assertThat(first.getBody().content())
        .anyMatch(
            n ->
                "billing".equals(n.type())
                    && n.title() != null
                    && n.title().contains("Facturation")
                    && n.actionUrl() != null
                    && n.actionUrl().endsWith("/settings/subscription"));
    assertThat(first.getBody().content().stream().filter(n -> "billing".equals(n.type())))
        .hasSize(1);

    billingReminderService.sendBillingRemindersFor(LocalDate.now());
    ResponseEntity<PageResponse<NotificationResponse>> second = getNotifications(token);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody()).isNotNull();
    assertThat(second.getBody().content().stream().filter(n -> "billing".equals(n.type())))
        .hasSize(1);
  }
}
