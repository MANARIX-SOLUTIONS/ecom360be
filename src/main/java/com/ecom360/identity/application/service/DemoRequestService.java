package com.ecom360.identity.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.dto.*;
import com.ecom360.identity.domain.model.DemoRequest;
import com.ecom360.identity.domain.repository.DemoRequestRepository;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoRequestService {

  private final DemoRequestRepository demoRequestRepo;
  private final UserRepository userRepository;
  private final BusinessRepository businessRepository;
  private final AuthService authService;
  private final AuditLogService auditLogService;
  private final EmailService emailService;

  public DemoRequestService(
      DemoRequestRepository demoRequestRepo,
      UserRepository userRepository,
      BusinessRepository businessRepository,
      AuthService authService,
      AuditLogService auditLogService,
      EmailService emailService) {
    this.demoRequestRepo = demoRequestRepo;
    this.userRepository = userRepository;
    this.businessRepository = businessRepository;
    this.authService = authService;
    this.auditLogService = auditLogService;
    this.emailService = emailService;
  }

  @Transactional
  public DemoRequestAckResponse submit(DemoRequestSubmitRequest req) {
    String email = req.email().trim().toLowerCase();
    if (userRepository.existsByEmail(email)) {
      throw new BusinessRuleException(
          "Un compte existe déjà avec cette adresse e-mail. Connectez-vous ou utilisez « Mot de passe oublié ».");
    }
    if (businessRepository.existsByEmail(email)) {
      throw new BusinessRuleException(
          "Cette adresse e-mail est déjà utilisée pour une entreprise.");
    }
    if (demoRequestRepo.existsByEmailAndStatus(email, DemoRequest.STATUS_PENDING)) {
      throw new BusinessRuleException(
          "Une demande de démo est déjà en cours pour cette adresse e-mail.");
    }

    String phone = req.phone().trim();
    DemoRequest dr =
        DemoRequest.create(
            req.fullName().trim(),
            email,
            phone,
            req.businessName().trim(),
            null,
            req.message() != null && !req.message().isBlank() ? req.message().trim() : null,
            req.jobTitle() != null && !req.jobTitle().isBlank() ? req.jobTitle().trim() : null,
            req.city() != null && !req.city().isBlank() ? req.city().trim() : null,
            req.sector() != null && !req.sector().isBlank() ? req.sector().trim() : null);
    dr = demoRequestRepo.save(dr);

    auditLogService.logAsync(
        null,
        null,
        "DEMO_REQUEST",
        "DemoRequest",
        dr.getId(),
        java.util.Map.of("email", email, "businessName", dr.getBusinessName()));

    try {
      emailService.sendDemoRequestReceivedEmail(
          dr.getEmail(), dr.getFullName(), dr.getBusinessName());
    } catch (Exception ignored) {
      // Ne pas bloquer l'enregistrement si l'e-mail échoue
    }

    return new DemoRequestAckResponse(
        "Votre demande a bien été enregistrée. Vérifiez votre boîte e-mail : nous vous répondons sous 48 h (souvent sous 24 h).");
  }

  public Page<DemoRequestResponse> list(UserPrincipal admin, String status, int page, int size) {
    requirePlatformAdmin(admin);
    Pageable pg = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
    Page<DemoRequest> p =
        status != null && !status.isBlank()
            ? demoRequestRepo.findByStatusOrderByCreatedAtDesc(status.trim(), pg)
            : demoRequestRepo.findAllByOrderByCreatedAtDesc(pg);
    return p.map(this::map);
  }

  @Transactional
  public void approve(UUID id, UserPrincipal admin) {
    requirePlatformAdmin(admin);
    DemoRequest dr =
        demoRequestRepo
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DemoRequest", id));
    if (!DemoRequest.STATUS_PENDING.equals(dr.getStatus())) {
      throw new BusinessRuleException("Cette demande n'est plus en attente.");
    }

    boolean hadPassword = dr.getPasswordHash() != null && !dr.getPasswordHash().isBlank();
    ProvisionedTenant pt =
        authService.provisionTenantAfterDemoApproval(
            dr.getFullName(),
            dr.getEmail(),
            dr.getPasswordHash(),
            dr.getPhone(),
            dr.getBusinessName());

    dr.markApproved(admin.userId());
    demoRequestRepo.save(dr);

    auditLogService.logAsync(
        pt.businessId(),
        admin.userId(),
        "DEMO_REQUEST_APPROVED",
        "DemoRequest",
        dr.getId(),
        java.util.Map.of("email", dr.getEmail(), "businessName", dr.getBusinessName()));

    if (!hadPassword) {
      try {
        String raw = authService.createPasswordResetToken(pt.userId());
        String link = emailService.buildResetPasswordLink(raw);
        emailService.sendInvitationEmail(
            dr.getEmail(), dr.getFullName(), dr.getBusinessName(), link);
      } catch (Exception ignored) {
        // compte créé même si l'e-mail échoue
      }
    }
  }

  @Transactional
  public void reject(UUID id, UserPrincipal admin, DemoRejectRequest req) {
    requirePlatformAdmin(admin);
    DemoRequest dr =
        demoRequestRepo
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DemoRequest", id));
    if (!DemoRequest.STATUS_PENDING.equals(dr.getStatus())) {
      throw new BusinessRuleException("Cette demande n'est plus en attente.");
    }
    String reason = req != null && req.reason() != null ? req.reason().trim() : null;
    dr.markRejected(admin.userId(), reason);
    demoRequestRepo.save(dr);

    auditLogService.logAsync(
        null,
        admin.userId(),
        "DEMO_REQUEST_REJECTED",
        "DemoRequest",
        dr.getId(),
        java.util.Map.of("email", dr.getEmail(), "reason", reason != null ? reason : ""));

    try {
      emailService.sendDemoRequestRejectedEmail(dr.getEmail(), dr.getFullName(), reason);
    } catch (Exception ignored) {
      // ignore
    }
  }

  private DemoRequestResponse map(DemoRequest d) {
    return new DemoRequestResponse(
        d.getId(),
        d.getFullName(),
        d.getEmail(),
        d.getPhone(),
        d.getBusinessName(),
        d.getMessage(),
        d.getJobTitle(),
        d.getCity(),
        d.getSector(),
        d.getStatus(),
        d.getCreatedAt(),
        d.getReviewedAt(),
        d.getReviewedByUserId(),
        d.getRejectionReason());
  }

  private void requirePlatformAdmin(UserPrincipal p) {
    if (p == null || !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Platform admin required");
    }
  }
}
