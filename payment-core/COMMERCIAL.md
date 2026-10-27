# Canonical commercial mode

The `server` command defaults to commercial mode, with no demo, synthetic confirmation or Model 2 endpoints mounted. It uses the existing PaymentIntent, SQLite repository, state machine, notification parser and HMAC matcher. Applications create immutable references and query one canonical payment identity.

Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline test installDist`.

Create a NEW private configuration with `python3 tools/create_commercial_config.py --receivers /private/public-receivers.json --output /private/commercial.json --client-id shop --allow-loopback-http`. Input is a JSON array of `id`, `name`, `phone`, `qrFile` and `deviceId`. The helper generates independent random keys, saves mode 600, refuses overwrites and leaves every receiver disabled and unverified. Keep the directory mode 700. A phone/account can have only one receiver identity.

Run with `PAYMENT_MODE=commercial PAYMENT_HOST=127.0.0.1 PAYMENT_PORT=8087 PAYMENT_CONFIG_FILE=/private/commercial.json PAYMENT_DB_PATH=/private/commercial.sqlite build/install/qr/bin/qr server`. Keep the HMAC secret and database stable across restarts. An existing laboratory database cannot be adopted. Never commit private files or database contents.

Use TLS outside loopback. For a TLS reverse proxy, bind the server to loopback and permit its local HTTP leg explicitly; expose only the HTTPS endpoint to devices. Forwarded headers do not bypass transport checks. The configuration file is operator managed and loaded at startup; the server does not change receiver account identity dynamically.

Application Bearer keys allow GET `/api/receivers`, POST `/api/payments`, GET `/api/payments/{id}`, POST `/{id}/resolve-yape`, POST `/{id}/cancel` and GET `/{id}/events`. Create fields: `reference`, `amountMinor`, `currency=604`, `method=YAPE_NOTIFICATION`, `receiverId`, `subject`. Resolve takes exactly three digits in `securityCode`. Results include state, immutable amount/receiver/reference/QR hash, expiry and a non-secret evidence ID. Only the owning authenticated application can read or resolve a payment. SSE snapshots are advisory; query canonical state before applying money.

Device Bearer keys are different and bound to one configured receiver. POST `/api/bridge/heartbeat` with `listenerConnected` reports availability. POST `/api/bridge/notifications` accepts `bridgeEventId`, `receiverId`, `packageName`, `postedAt` ISO with millisecond precision, minimum notification text fields and an optional security code extracted from authentic notification fields. ACK requires boolean `accepted=true` and the exact `eventId`. Raw notification text and codes are not persisted: codes use HMAC.

Only recognized incoming Yape notifications are considered. Evidence is deduplicated by stable callback ID and receiver/source/time/payer/amount fingerprint, and can be consumed once. Amount alone and customer-supplied code alone never confirm. Missing codes, conflicting codes, aggregated payers, ambiguous matches, late arrivals, cancelled or expired intentions require review. Ambiguous evidence stays quarantined even if another claimant cancels. Financial state and consumed evidence commit or roll back together.

Until physical receiver verification, authenticated recognized notifications are quarantined as `REVIEW_RECEIVER_UNVERIFIED` and cannot pay an intention. The operator must actually verify the QR, account and device before setting that receiver's `verified` and `enabled` indicators and restarting the process. A heartbeat only proves recent device communication. It does not prove ownership of a bank account.

Five code attempts per minute are enforced durably by payment, subject and receiver across application clients. Restarting the process does not reset the window. Keep keys private, scope each application to its receiver IDs and rotate compromised device/application keys through operator procedures. Do not casually rotate the HMAC secret while payments are pending.

LAB is explicit: `PAYMENT_MODE=lab`, a separate `QR_LAB_DB_PATH` containing `lab`, and its own port. LAB refuses a commercial database. In-memory synthetic CLI examples cannot modify commercial state, and the synthetic simulator rejects Yape intents. No bank transfer or physical Android validation is implied by JVM tests.
