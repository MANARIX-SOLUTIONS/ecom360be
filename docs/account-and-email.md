# Account profile, passwords, and transactional email

Source-backed guide for the signed-in user profile, password reset and change,
and the mailer those flows use.

Verified against `UserController`, `UserProfileService`, `UserProfileRequest` /
`UserProfileResponse`, `User`, `UserRepository`, `AuthController`, `AuthService`,
`PasswordReset`, `ForgotPasswordRequest`, `ResetPasswordRequest`,
`ChangePasswordRequest`, `BusinessUserService.invite`, `DemoRequestService`,
`EmailService`, `SecurityConfig`, `SubscriptionRequiredFilter`,
`JwtAuthenticationFilter`, `JwtService`, `GlobalExceptionHandler`,
`application.yml`, `application-prod.yml`, `docker-compose.yml`, `.env.example`,
and `V1__initial_schema.sql` (`users`, `password_reset`).

Demo-request review (who may approve, provisioning order) is a separate
workflow. This page covers the profile API, the password endpoints, and how
mail is actually sent.

## Intent

- Let any authenticated user read and update their own name, email, and phone.
- Issue a 24-hour set-password link for forgotten passwords, new invites, and
  demo approvals that have no password yet.
- Send those messages as plain text. If SMTP is missing or the send fails, keep
  the API success path and write the message to the log.

## Profile API

JWT access token required. Base path: `/api/v1/users`.

`UserController` has no `@PreAuthorize` and does not call
`RolePermissionService`. Any authenticated principal can read and update the
user id in the token. Nav key `settings:profile` is only a frontend hint
(`STORES_READ`); the API does not check it.

| Method | Path | Success |
|--------|------|---------|
| `GET` | `/users/me` | 200 `UserProfileResponse` |
| `PUT` | `/users/me` | 200 `UserProfileResponse` |

`UserProfileRequest`:

| Field | Constraint | Notes |
|-------|------------|--------|
| `fullName` | `@NotBlank`, max 255 | Stored as submitted. Not trimmed. |
| `email` | `@NotBlank`, `@Email`, max 255 | Not lowercased or trimmed. |
| `phone` | max 50, optional | `null` and `""` are accepted. No uniqueness check. |

`UserProfileResponse` is `id`, `fullName`, `email`, `phone`. Jackson is
configured with `default-property-inclusion: non_null`, so a null phone is
omitted. `avatarUrl`, `locale`, `isActive`, and `passwordHash` are not on this
API. `locale` stays `fr` unless some other writer changes it.

Lookup is `userRepository.findById(principal.userId())`. A missing row is HTTP
404. Email uniqueness uses `existsByEmail`, which is an exact match. The check
is skipped when `req.email()` equals the current email ignoring case, and the
new casing is then saved. A different account that differs only by case is not
detected here. The database unique index on `users.email` is also
case-sensitive (`VARCHAR(255) NOT NULL UNIQUE`). Conflict is HTTP 409
`User already exists: {email}`.

`SubscriptionRequiredFilter` returns HTTP 402 `SUBSCRIPTION_REQUIRED` for
`/api/v1/users/**` when the access token has a `businessId` and that business
has no `trialing` or `active` subscription whose `currentPeriodEnd` is today or
later. Paths under `/api/v1/subscription`, `/api/v1/admin`, and `/api/v1/public/`
are the exemptions. A principal with a null `businessId` is not blocked.

There is no profile audit event. The access token is not reissued. Its `email`
claim stays at login-time value until the next `POST /api/v1/auth/refresh` or
login, both of which read `user.email` from the database. `GET /users/me` reads
the database, not the claim.

## Password endpoints

| Method | Path | Auth | Success |
|--------|------|------|---------|
| `POST` | `/auth/forgot-password` | Public | 204, always when the body is valid |
| `POST` | `/auth/reset-password` | Public | 204 |
| `POST` | `/auth/change-password` | JWT | 204 |

`/auth/change-password` is not in `SecurityConfig.PUBLIC_PATHS` and is not
exempt from the subscription filter. Expired tenants receive 402 before the
service runs. Forgot and reset are public, so the filter sees no principal and
does not apply.

### Forgot password

`ForgotPasswordRequest.email` must be a non-blank `@Email`. The service loads
`findByEmail` with that exact string. Unknown emails and inactive users produce
the same 204 and no row in `password_reset`.

For an active user the service inserts a `password_reset` row and calls
`sendPasswordResetEmail`. It does not mark older unused tokens as used. Token
lifetime is `AuthService.PASSWORD_RESET_TOKEN_VALIDITY_HOURS` (24). The raw
token is `{uuid}-{epochMillis}`. Only `Base64(SHA-256(rawToken))` is stored.

### Reset password

`ResetPasswordRequest`: `token` required, `newPassword` required and at least 8
characters. There is no maximum and no complexity rule.

`findByTokenHashAndUsedFalse` misses unknown or already-used tokens and returns
422 `Invalid or expired reset token`. A row that is unused but past
`expiresAt` returns 422 `Reset token has expired`. Success sets `password_hash`
(BCrypt strength 12) and `used = true` on that row only. Other outstanding
tokens stay valid. Existing access and refresh JWTs are not revoked. The
`session` table has no writer in this codebase.

### Change password

`ChangePasswordRequest`: `currentPassword` required, `newPassword` required and
at least 8 characters. A mismatch with the stored hash is 422
`Current password is incorrect`. The service does not require the new password
to differ from the current one. When the token has a `businessId`, it writes an
audit event `PASSWORD_CHANGE` on entity type `Auth`. Tokens are not revoked.

### Login constraint

`LoginRequest.password` must be non-blank, but `AuthService.login` does not
call `passwordEncoder.matches`. The comparison is commented out. Login still
requires an active user and one accepted, active `business_user` row, records
`last_login_at`, writes a `LOGIN` audit event, and returns tokens. A platform
admin flag is applied in `buildAuthResponse` only after that membership is
found. No membership is 422 `No active business membership found`.

`BusinessUser.create` sets `acceptedAt` immediately, so an invited user counts
as accepted before they open the set-password link.

## Transactional email

`EmailService` sends `SimpleMailMessage` text. There are no HTML templates on
this branch. Sends are synchronous on the caller thread. There is no retry
queue.

`JavaMailSender` is injected with `required = false`. `send` uses the sender
when the bean exists. Any `Exception` from `mailSender.send` is logged at warn
and the same body is logged at info. When the bean is absent, the body is
logged and nothing is sent. Callers other than `DemoRequestService` do not add
their own try/catch. `DemoRequestService` wraps its three sends in try/catch,
but `send` already swallows `Exception`, so those catches do not change the
outcome.

| Caller | Message | When it is sent |
|--------|---------|-----------------|
| `AuthService.forgotPassword` | Password reset | Active user, exact email match |
| `BusinessUserService.invite` | Invitation | A new `users` row was just created |
| `DemoRequestService.submit` | Demo received | After the pending request is saved |
| `DemoRequestService.approve` | Invitation | Approval, and the request had no password hash |
| `DemoRequestService.reject` | Demo rejected | After the request is marked rejected |

Links are `{APP_URL}/reset-password?token={raw}`. `app.url` defaults to
`http://localhost:5173` and a trailing slash is stripped.
`EmailService.buildResetPasswordLink` is the only link builder.

Invite details that affect mail:

- An email that already belongs to a user does not get an invitation. The
  service only adds a `business_user` row. If that membership already exists,
  the result is HTTP 409 and no mail.
- The new user's display name is the local-part of the email. Phone is null.
- A random 24-character password is hashed and stored, and is not included in
  the email. The message contains the set-password link only.
- The invitation subject and body use the business name, or the literal
  `l'entreprise` when the business row is missing.
- Plan checks run before mail: `maxUsers` counts active members (`0` means
  unlimited via `Plan.isUnlimited`); when `featureRoleManagement` is not
  `Boolean.TRUE`, only role code `CAISSIER` can be invited. No plan row skips
  both checks (`Optional.ifPresent`).

Demo received copy says the team replies within 48 working hours and that the
trial is 14 days. The 14 days matches `SubscriptionService.TRIAL_DAYS`. The 48
hours is email copy, not a scheduler. Rejection appends `Motif : …` only when
the reason is non-blank.

### SMTP configuration

| Env | Property | Default |
|-----|----------|---------|
| `MAIL_HOST` | `spring.mail.host` | `maildev` in `application.yml`; empty in `application-prod.yml` |
| `MAIL_PORT` | `spring.mail.port` | `1025` |
| `MAIL_USERNAME` | `spring.mail.username` | empty |
| `MAIL_PASSWORD` | `spring.mail.password` | empty |
| `MAIL_FROM` | `spring.mail.from` | `noreply@ecom360.local` |
| `APP_URL` | `app.url` | `http://localhost:5173` |

`spring.mail.properties.mail.smtp.auth` and `starttls.enable` are both
`${MAIL_USERNAME:false}`. Unset username stores the string `false`. A set
username stores that username, not `true`. JavaMail enables those flags only
for the value `true`, so setting `MAIL_USERNAME` / `MAIL_PASSWORD` does not by
itself turn on SMTP auth or STARTTLS.

`docker-compose.yml` points the API at the `maildev` service (`MAIL_HOST=maildev`,
port `1025`) and publishes the Maildev UI on `http://localhost:1080`.
`./gradlew bootRun` without that hostname uses the `maildev` default, fails the
send, and logs the message.

Prod sets `spring.mail.host` to `${MAIL_HOST:}`. An empty host still satisfies
Spring Boot's mail auto-configuration (`@ConditionalOnProperty` on
`spring.mail.host` matches any value other than `false`), so a sender bean is
created, the send fails, and `EmailService` logs the body. The log line
includes the reset or invitation URL.

Failed or unconfigured mail does not change HTTP status: forgot-password stays
204, demo submit stays 202, invite still returns 201, and demo approve/reject
still complete.

## Pitfalls

- Profile email is not normalized. Login and forgot-password use exact
  `findByEmail`. Demo submit is the path that lowercases email first.
- Changing the profile email does not update the current access token.
- Forgot-password cannot be used to discover whether an email exists. It also
  cannot reset an inactive user.
- A green invite or demo approval does not prove the message left the server.
  Check logs or Maildev.
- Password reset and change leave already issued JWTs valid until they expire
  (`jwt.expiration-ms` 24h by default, 1h in prod; refresh 7 days by default,
  24h in prod).
- Login currently accepts any non-blank password for an active member because
  the hash check is commented out in `AuthService.login`.
