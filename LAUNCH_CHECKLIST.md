# Scan Lanka — Launch checklist

Dev conveniences that **must** be flipped/configured before going live, plus known gaps.
Copy `.env.example` → `.env` for local dev (`spring-dotenv` loads `.env` on startup). In production,
set these as real environment variables (docker-compose `environment:`, systemd, k8s secrets, …).

## 🔐 Security (must do)

- [ ] **Re-arm admin 2FA** — `ADMIN_TOTP_REQUIRED=true`.
      Default in `application.yml` is `${ADMIN_TOTP_REQUIRED:false}` (OFF for dev). With it true,
      admins must enrol TOTP before `/api/admin/**` works.
- [ ] **JWT secret** — `JWT_SECRET=<random ≥32 bytes>` (the committed dev default must never ship).
- [ ] **Secure cookies** — `AUTH_COOKIE_SECURE=true` (HTTPS only).
- [ ] **DB role** — app connects as a non-superuser, `NOBYPASSRLS` role (RLS must apply).
      Set `app.db-role-check.enabled=true` (and `fail-on-violation=true`) so startup refuses an
      over-privileged role. Provide `DB_URL` / `DB_USER` / `DB_PASSWORD`.
- [ ] **Initial admin** — `INITIAL_ADMIN_EMAIL` + `INITIAL_ADMIN_PASSWORD` (seeds one admin on first
      boot when none exists), then enrol 2FA. Rotate the password after first login.
- [ ] **PayHere** — `PAYHERE_MERCHANT_ID`, `PAYHERE_MERCHANT_SECRET`,
      `PAYHERE_NOTIFY_URL` / `RETURN_URL` / `CANCEL_URL` (public HTTPS URLs).
- [ ] **CSP enforce (frontend)** — `CSP_ENFORCE=true` after a soak in report-only mode.
- [ ] **Real email provider** — replace `LoggingEmailProvider` (logs the body, incl. OTP/reset codes!)
      with an SMTP/SES/SendGrid adapter behind `EmailProvider`. Never log bodies in prod.

## ⚙️ Config

- [ ] `NEXT_PUBLIC_API_BASE` / site URL point at the deployed backend.
- [ ] `DB_URL` etc. point at the production Postgres; run Flyway migrations on deploy.
- [ ] Redis host/port for the rate limiter (fail-closed if unreachable).

## 🧪 Pre-launch verification

- [ ] Backend: `mvn verify` green (Testcontainers). Local Docker recipe:
      `DOCKER_API_VERSION=1.44 TESTCONTAINERS_RYUK_DISABLED=true mvn -o -DargLine="-Dapi.version=1.44" verify`
- [ ] Frontend: `npm run build` clean.
- [ ] Smoke test: register → verify email → checkout (PayHere + bank slip + COD) → admin confirm.

## 🚧 Known gaps to close

- [ ] **`/actuator/prometheus` not registered** — micrometer / Spring Boot 3.3 wiring mismatch
      (only `health` + `info` are exposed). Metrics scraping won't work until fixed; the security
      gating is correct. `ActuatorAuthzIT` asserts only the authz boundary and tolerates the 404.
- [ ] Drop a real `frontend/public/herosectionimg.png` and `logo.png` (done) — confirm sizes/retina.
