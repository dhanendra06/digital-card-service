# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Build & Test

```bash
# Build (skip tests, javadoc, GPG signing)
mvn install -DskipTests=true -Dmaven.javadoc.skip=true -Dgpg.skip=true

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=DigitalCardServiceImplTest

# Run a single test method
mvn test -Dtest=DigitalCardServiceImplTest#testGetDigitalCard

# Sonar analysis
mvn -Psonar verify

# Build Docker image
docker build -f Dockerfile .
```

**Requirements:** JDK 21.0.3, Maven 3.9.6

## Architecture Overview

This is a **single-module Spring Boot service** (port `9092`, context path `/v1/digitalcard`) that auto-generates a password-protected PDF digital identity card after a registration packet is processed. It is entirely **event-driven via MOSIP WebSub** — there are no batch jobs or scheduled triggers for card generation.

### Card Generation Flow

```
ID Repo (identity create/update)
  → WebSub IDENTITY_CHANGED topic
    → /idCreateEventHandle/callback/notifyStatus  (DigitalCardController)
    → initiateCredentialRequest()                 (DigitalCardServiceImpl)
      → Credential Service (requests VC for RID)
        → WebSub credential topic
          → /credential/callback/notifyStatus     (DigitalCardController)
          → generateDigitalCard()                 (DigitalCardServiceImpl)
            1. Download encrypted VC from DataShare URL
            2. Decrypt VC via Cryptomanager API
            3. Optionally verify VC via vcverifier
            4. PDFCardServiceImpl.generateCard()
               a. Extract biometric photo (CBEFF → ISO → base64 PNG)
               b. Build Velocity template attributes from credentialSubject
               c. Generate QR code (ZXing)
               d. Render HTML template via TemplateGenerator
               e. Convert to PDF via kernel-pdfgenerator
               f. Sign PDF via kernel signature API
            5. Upload PDF to DataShare (no partner encryption)
            6. Save DataShare URL to DB (digitalcard_transaction table)
            7. Publish CREDENTIAL_STATUS_UPDATE event via WebSub
```

The `GET /{rid}` endpoint allows Resident/Admin service to retrieve the DataShare PDF URL for a given RID.

### Key Classes

| Class | Role |
|-------|------|
| `DigitalCardController` | Three WebSub callback endpoints + `GET /{rid}` |
| `DigitalCardServiceImpl` | Main orchestrator: credential request, card generation trigger, DB state machine |
| `PDFCardServiceImpl` | PDF rendering: photo extraction, template population, QR code, PDF signing |
| `DigitalCardInitializer` | On `ApplicationReadyEvent`: subscribes to 3 WebSub topics with periodic re-subscription |
| `WebSubSubscriptionHelper` | Wraps kernel WebSub `SubscriptionClient` and `PublisherClient` |
| `EncryptionUtil` | Calls Cryptomanager REST API to decrypt the encrypted VC |
| `DataShareUtil` | Uploads/downloads PDF bytes to/from DataShare service |
| `TemplateGenerator` | Fetches Velocity HTML template from config server; renders with attributes |
| `CredentialUtil` | Sends credential issuance request to Credential Request Generator |
| `RestClient` | Central HTTP client wrapping `kernel-core` REST utilities |

### Database

Single table: `digitalcard.digitalcard_transaction` in database `mosip_digitalcard`.  
Tracked states: `NEW` → `AVAILABLE` / `ERROR`.  
`DigitalCardTransactionRepository` uses custom `@Query` methods (`findByRID`, `updateTransactionDetails`, `updateErrorTransactionDetails`).

DB scripts are in `db_scripts/` (fresh install) and `db_upgrade_scripts/` (migration from previous versions).

### WebSub Topics (configured externally)

| Property | Direction |
|----------|-----------|
| `mosip.digitalcard.generate.identity.create.websub.topic` | subscribe |
| `mosip.digitalcard.generate.identity.update.websub.topic` | subscribe |
| `mosip.digitalcard.generate.credential.websub.topic` | subscribe |
| `mosip.digitalcard.websub.publish.topic` (`CREDENTIAL_STATUS_UPDATE`) | publish |

### External Service Dependencies

All called via `RestClient` using `ApiName` constants:
- **Cryptomanager** — decrypt VC (`CRYPTOMANAGER_DECRYPT`)
- **DataShare** — upload PDF, download VC (`DATASHARECREATEURL`, etc.)
- **Kernel PDF Signer** — sign the generated PDF (`PDFSIGN`)
- **Credential Request Generator** — initiate VC issuance
- **Config Server** — loads identity mapping JSON and Velocity templates at runtime
- **WebSub Hub** — event delivery (`websub.hub.url`, `websub.publish.url`)

### Configuration

Runtime config is fetched from the **MOSIP Config Server** (Spring Cloud Config). Key files:
- [`digital-card-default.properties`](https://github.com/mosip/mosip-config/blob/master/digital-card-default.properties)
- [`application-default.properties`](https://github.com/mosip/mosip-config/blob/master/application-default.properties)

Important properties:
- `mosip.digitalcard.verify.credentials.flag` — toggle VC verification before PDF generation
- `mosip.digitalcard.credentials.request.initiate.flag` — if `true`, auto-initiates credential request on `GET /{rid}` when no record exists
- `mosip.digitalcard.pdf.password.enable.flag` — toggle PDF password protection
- `mosip.digitalcard.uincard.password` — pipe-separated demographic field names used to derive the PDF password (first 4 chars of each, uppercased)
- `mosip.digitalcard.datashare.partner.id` / `policy.id` — DataShare upload policy
- `mosip.digitalcard.credential.request.partner.id` / `credential.type` — VC issuance config

