package com.ecom360.identity.domain.model;

import com.ecom360.shared.domain.model.AggregateRoot;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User extends AggregateRoot {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String locale = "fr";

    @Column(name = "is_platform_admin", nullable = false)
    private Boolean isPlatformAdmin = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {}

    // ── Factory ──────────────────────────────────────────────
    public static User create(String fullName, String email, String passwordHash, String phone) {
        User u = new User();
        u.fullName = fullName;
        u.email = email;
        u.passwordHash = passwordHash;
        u.phone = phone;
        u.isActive = true;
        u.isPlatformAdmin = false;
        u.locale = "fr";
        return u;
    }

    // ── Domain behaviour ─────────────────────────────────────
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean isPlatformAdmin() {
        return Boolean.TRUE.equals(isPlatformAdmin);
    }

    // ── Accessors ────────────────────────────────────────────
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public Boolean getIsPlatformAdmin() { return isPlatformAdmin; }
    public void setIsPlatformAdmin(Boolean isPlatformAdmin) { this.isPlatformAdmin = isPlatformAdmin; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
