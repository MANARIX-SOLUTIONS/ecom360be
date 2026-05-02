package com.ecom360.tenant.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.CheckoutSessionResponse;
import com.ecom360.tenant.application.dto.PaymentTransactionResponse;
import com.ecom360.tenant.application.payment.CheckoutCreateRequest;
import com.ecom360.tenant.application.payment.CheckoutCreateResponse;
import com.ecom360.tenant.application.payment.PaymentCallbackResult;
import com.ecom360.tenant.application.payment.PaymentProvider;
import com.ecom360.tenant.application.payment.PaymentProviderRegistry;
import com.ecom360.tenant.domain.model.Invoice;
import com.ecom360.tenant.domain.model.PaymentTransaction;
import com.ecom360.tenant.domain.model.PaymentTransactionStatus;
import com.ecom360.tenant.domain.repository.InvoiceRepository;
import com.ecom360.tenant.domain.repository.PaymentTransactionRepository;
import com.ecom360.tenant.infrastructure.payment.PaymentProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionPaymentService {

  private final SubscriptionService subscriptionService;
  private final PaymentProviderRegistry paymentProviderRegistry;
  private final PaymentProperties paymentProperties;
  private final InvoiceRepository invoiceRepository;
  private final PaymentTransactionRepository paymentTransactionRepository;

  public SubscriptionPaymentService(
      SubscriptionService subscriptionService,
      PaymentProviderRegistry paymentProviderRegistry,
      PaymentProperties paymentProperties,
      InvoiceRepository invoiceRepository,
      PaymentTransactionRepository paymentTransactionRepository) {
    this.subscriptionService = subscriptionService;
    this.paymentProviderRegistry = paymentProviderRegistry;
    this.paymentProperties = paymentProperties;
    this.invoiceRepository = invoiceRepository;
    this.paymentTransactionRepository = paymentTransactionRepository;
  }

  @Transactional
  public CheckoutSessionResponse createCheckout(
      String planSlug, String billingCycle, String paymentMethod, UserPrincipal principal) {
    SubscriptionService.CheckoutSubscriptionDraft draft = subscriptionService.createIncompleteSubscriptionForCheckout(
        planSlug, billingCycle, principal);

    Invoice invoice = new Invoice();
    invoice.setBusinessId(principal.businessId());
    invoice.setSubscriptionId(draft.subscription().getId());
    invoice.setNumber(nextInvoiceNumber());
    invoice.setAmount(draft.amount());
    invoice.setStatus("draft");
    invoice.setDueDate(LocalDate.now().plusDays(1));
    invoice = invoiceRepository.save(invoice);

    PaymentProvider provider = paymentProviderRegistry.get(paymentProperties.getProvider());
    PaymentTransaction transaction = new PaymentTransaction();
    transaction.setBusinessId(principal.businessId());
    transaction.setSubscriptionId(draft.subscription().getId());
    transaction.setInvoiceId(invoice.getId());
    transaction.setProvider(provider.getName());
    transaction.setProviderReference("local-" + UUID.randomUUID());
    transaction.setAmount(draft.amount());
    transaction.setCurrency(paymentProperties.getCurrency());
    transaction.setStatus(PaymentTransactionStatus.PENDING);
    transaction.setPlanSlug(draft.plan().getSlug());
    transaction.setBillingCycle(draft.billingCycle());
    transaction = paymentTransactionRepository.save(transaction);

    CheckoutCreateResponse checkout = provider.createCheckout(
        new CheckoutCreateRequest(
            transaction.getId(),
            principal.businessId(),
            draft.subscription().getId(),
            invoice.getNumber(),
            draft.plan().getName(),
            draft.plan().getSlug(),
            draft.billingCycle(),
            draft.amount(),
            paymentProperties.getCurrency(),
            principal.email(),
            principal.email(),
            paymentMethod,
            Map.of("provider", provider.getName())));

    transaction.setProviderReference(checkout.providerReference());
    transaction.setCheckoutUrl(checkout.checkoutUrl());
    transaction = paymentTransactionRepository.save(transaction);

    return toCheckoutResponse(transaction, invoice);
  }

  @Transactional(readOnly = true)
  public PaymentTransactionResponse getTransaction(UUID transactionId, UserPrincipal principal) {
    PaymentTransaction transaction = paymentTransactionRepository
        .findById(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", transactionId));
    if (!transaction.getBusinessId().equals(principal.businessId())) {
      throw new AccessDeniedException("Payment transaction not found");
    }
    Invoice invoice = invoiceRepository
        .findById(transaction.getInvoiceId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Invoice", transaction.getInvoiceId()));
    return toPaymentResponse(transaction, invoice);
  }

  @Transactional
  public void processProviderCallback(String providerName, String rawPayload) {
    PaymentProvider provider = paymentProviderRegistry.get(providerName);
    PaymentCallbackResult callback = provider.parseCallback(rawPayload);
    PaymentTransaction transaction = paymentTransactionRepository
        .findByProviderAndProviderReference(provider.getName(), callback.providerReference())
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "PaymentTransaction", callback.providerReference()));

    if (transaction.isFinalized()) {
      return;
    }

    if (PaymentTransactionStatus.PAID.equals(callback.status())) {
      transaction.markPaid(callback.rawPayload());
      paymentTransactionRepository.save(transaction);

      Invoice invoice = invoiceRepository
          .findById(transaction.getInvoiceId())
          .orElseThrow(
              () -> new ResourceNotFoundException("Invoice", transaction.getInvoiceId()));
      invoice.markPaid(provider.getName());
      invoiceRepository.save(invoice);
      subscriptionService.activatePaidSubscription(transaction.getSubscriptionId());
      return;
    }

    if (PaymentTransactionStatus.FAILED.equals(callback.status())
        || PaymentTransactionStatus.CANCELLED.equals(callback.status())
        || PaymentTransactionStatus.EXPIRED.equals(callback.status())) {
      transaction.markFailed(callback.status(), callback.failureReason(), callback.rawPayload());
      paymentTransactionRepository.save(transaction);
    }
  }

  private CheckoutSessionResponse toCheckoutResponse(
      PaymentTransaction transaction, Invoice invoice) {
    return new CheckoutSessionResponse(
        transaction.getId(),
        invoice.getId(),
        invoice.getNumber(),
        transaction.getProvider(),
        transaction.getProviderReference(),
        transaction.getCheckoutUrl(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getStatus());
  }

  private PaymentTransactionResponse toPaymentResponse(
      PaymentTransaction transaction, Invoice invoice) {
    return new PaymentTransactionResponse(
        transaction.getId(),
        invoice.getId(),
        invoice.getNumber(),
        transaction.getProvider(),
        transaction.getProviderReference(),
        transaction.getCheckoutUrl(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getStatus(),
        transaction.getFailureReason(),
        transaction.getPaidAt());
  }

  private String nextInvoiceNumber() {
    String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    return "INV-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
