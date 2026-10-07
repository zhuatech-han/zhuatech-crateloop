[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" height="48" alt="ZhiHua Technology logo">

# CrateLoop · Reusable Crate and Pallet Movement Ledger

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

**0.1.0 · Public source for learning / non-commercial use. Commercial use requires prior written authorization.** Own code uses [LICENSE](LICENSE); third-party components/assets retain their licenses, listed in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).

## From dispatch to return

Java 21 / Spring Boot, Vue 3, MySQL and Flyway provide quantity conservation, partner isolation, handoffs and independent quality review. CrateLoop serves distribution warehouses and manufacturing coordinators managing their own **quantity-based crates, pallets and bins without individual serial numbers**. Each pool belongs to one department/type; each movement has one pool, one partner and an integer quantity.

For background on reuse, inspection and repair, see the [CHEP process reference](https://www.chep.com/ca/en/node/150131). This application records equipment the deploying organization is authorized to use. It is not integrated with any pooling operator and does not provide rental/deposit/compensation pricing or ownership certification. People verify actual quantities and evidence.

### Closing the quantity loop

```text
Asset addition → independent approval → available
Issue request → independent approval/reservation → outbound → partner receipt → partner custody
Return request → reserve partner custody → inbound → warehouse receipt → independent inspection
                                                                    → available
                                                                    → repair → independent release/classification
                                                                    → retired
Transit shortage → independent evidence review → recovered / still at partner / lost
```

Pool total equals ten buckets: available, issue reserved, outbound transit, partner custody, return reserved, inbound transit, pending inspection, repair, lost and retired. Lost/retired equipment remains in historical total. Partner subledgers and unfinished handoffs separately reconcile custody, reservations, transit and inspection quantities. Each posting checks nonnegative quantities and conservation; failure rolls back the complete transaction.

Drafts reserve nothing. Issue approval reserves available stock. Return submission reserves only that partner's custody. Adjustment approval rechecks current balances; repair proposals reserve nothing beforehand. Dispatched movements cannot be canceled and must resolve actual receipt/shortage evidence. Disabled pools/partners reject new issues but allow returns.

A balance statement freezes current partner custody and its pool/partner ledger cutoff, requiring no unfinished physical handoff. Another authorized actor confirms. Later relevant postings invalidate confirmation: retain the dispute, cancel and recreate. This is a quantity reconciliation, not financial settlement or an arbitrary historical-date inventory report.

## Business and administrative roles

| Module | Implemented operations |
|---|---|
| Handoffs | ISSUE/RETURN drafts, edits, submission, independent approve/reject, reserve, dispatch, actual receipt, independent shortage resolution/inspection, predispatch cancellation; search, state/direction filter, pagination/sort |
| Pools/partners | Zero-balance pools, type dictionaries, department ownership, versioned names/enabled state; unreferenced directories may be deleted, foreign keys protect historical references |
| Adjustments | Asset addition, repair release and available-stock retirement; draft/edit/submit, independent approve/reject and cancel |
| Statements | Current custody and complete partner-ledger snapshot, confirmation/dispute, internal cancellation and cutoff checks |
| Evidence/reporting | Appended postings/events and scoped JSON evidence without promotional content |
| Statistics | Authorized movement states, shortages and bucket quantities; partners see only own custody/return reservations |
| Administration | Accounts, roles, 19 registered permissions, 14 registered menus, departments, equipment dictionary, settings and scoped audit |
| Identity/pages | Login/logout, own password, BCrypt cost 12, CSRF, login limits, live authorization/disablement, Chinese/English and narrow layouts |

Initial roles are administrator, coordinator, independent reviewer, warehouse and partner. Coordinators prepare records; reviewers approve/inspect; warehouse members dispatch/receive; partners request issue/return, acknowledge receipts and reconcile statements.

Internal receiver differs from dispatcher; approver differs from creator; shortage reviewer differs from creator, dispatcher and receiver; inspector differs from receiver. Multiple accounts of the same role support independence. Partner accounts bind to a same-department partner. **Binding overrides ALL:** even an accidentally assigned administrator role cannot expose all pool buckets, other partners' documents or administration. Internal roles use ALL/DEPARTMENT/SELF (created records). Hiding menus does not replace API authorization.

### Actual running pages

Isolated acceptance records are marked TEST without real customer data; empty installation creates no equipment balances or business examples.

| Login | Partner workspace |
|---|---|
| ![Login](docs/screenshots/login.png) | ![Partner workspace](docs/screenshots/partner-home.png) |

Login: session authentication. Partner home: only own custody and return-reserved balances.

| Handoff evidence/quantities | Pool and partner balances |
|---|---|
| ![Handoff details](docs/screenshots/handoff.png) | ![Pool](docs/screenshots/pool.png) |

Handoff: receipt, shortage and inspection evidence. Pool: authorized bucket quantities and partner subledgers.

| Accounts | Quantity statistics |
|---|---|
| ![Accounts](docs/screenshots/users.png) | ![Statistics](docs/screenshots/dashboard.png) |

Accounts: departments, roles and partner bindings. Statistics: authorized document states and equipment quantities.

| Roles and permissions | System settings |
|---|---|
| ![Roles](docs/screenshots/roles.png) | ![Settings](docs/screenshots/settings.png) |

Roles: registered interface permissions/scopes. Settings: workspace name and supported resource limits.

## Architecture and directory structure

Browser → same-origin Nginx → Spring Boot → JPA/MySQL. Flyway versions the schema; backend validates without automatically modifying it. A global write lock, versions and UUID payload hashes protect state and quantities. See [Architecture](docs/架构说明.md); detailed linked manuals are currently in Chinese.

| Layer | Version/convention |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Security, JPA, Flyway and MariaDB JDBC for MySQL |
| Frontend | Vue 3.5.40, Vite 8.1.5, Node 24.19.0+, npm 11 and Lucide |
| Data/proxy | MySQL 8.4, Nginx 1.29 and Docker Compose v2 |
| Acceptance | Python 3.10+; H2 MySQL-mode integration plus separate actual MySQL checks |

```text
backend/                  API, domain, quantity rules, identity and migrations
  src/main/resources/db/migration/
  src/test/               Quantity unit and HTTP/JPA tests
frontend/                 Vue pages, forms, permission actions and tests
  public/brand/           Original logo and Chinese contact assets
scripts/                  Private configuration, actual HTTP acceptance, release checks
docs/                     Architecture, deployment, API, operations, screenshots and notices
compose.yaml              Independent database/backend/frontend
.env.example              Names only; actual .env ignored
```

## Requirements and installation

Python 3.10+, Docker Desktop/Engine, Compose v2 and access to public dependencies/official images. At the repository root:

```sh
python3 scripts/init-env.py
docker compose -p crateloop config --quiet
docker compose -p crateloop up -d --build --wait
```

The generator creates ignored `.env` with mode 0600 and refuses to overwrite existing files. If configuration already exists, start directly. Username: `admin`; read `ADMIN_PASSWORD` from your own generated file. No shared fixed demo password or preset asset balance exists. Initial transaction creates headquarters, five roles, 19 permissions, 14 menus, three equipment types and three settings. Administrator password hashes use BCrypt. Business examples are created only by explicit isolated acceptance.

- Interface/API: [http://127.0.0.1:8127/](http://127.0.0.1:8127/).
- Health: [http://127.0.0.1:8127/actuator/health](http://127.0.0.1:8127/actuator/health).
- MySQL/backend have no published host ports.
- Override: `WEB_PORT=18127 docker compose -p crateloop up -d`, or your own local `WEB_PORT`.

### Configuration

| Name | Meaning |
|---|---|
| `DATABASE_PASSWORD` / `MYSQL_ROOT_PASSWORD` | Independent application/admin database passwords, no weak defaults |
| `ADMIN_PASSWORD` | Empty-database administrator only; at least 12 characters with upper/lowercase/digits, at most 72 UTF-8 bytes; later use authorized password reset/own change |
| `WEB_PORT` / `BIND_ADDRESS` | Defaults 8127 / 127.0.0.1; external entry needs trusted proxy/HTTPS |
| `COOKIE_SECURE` | false for local HTTP, true for HTTPS |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_CATALOG` | Backend process JDBC/account/catalog overrides; Compose uses internal MySQL, external mapping must be configured explicitly |
| `TEST_URL` | Isolated acceptance target, default local 8127; can select separate recovery |

See [.env.example](.env.example). Real configuration, passwords and deployment credentials stay private. Changing `ADMIN_PASSWORD` does not reset an existing account.

### Source development

Node 24.19.0+ / npm 11 and Java 21 / Maven 3.9. Provide dedicated MySQL and controlled `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DATABASE_CATALOG` and `ADMIN_PASSWORD`, then at the root:

```sh
mvn -f backend/pom.xml spring-boot:run
```

In another root terminal:

```sh
cd frontend
npm ci
npm run dev
```

Vite defaults to port 5173 and proxies `/api`/health to backend port 8080. An example connection is `jdbc:mariadb://127.0.0.1:3306/zhuatech_crateloop`; use a dedicated development database or private loopback port override. Host source execution does not automatically read `.env`. Never target production with acceptance. See [Deployment](docs/部署说明.md).

## Database, upgrades and recovery

`V1__identity.sql` creates identity/system directories; `V2__reusable_pool.sql` adds pools, partners, movements, adjustments, ledgers, statements, commands and evidence. Preserve executed migrations; append new versions. Before upgrading, pause writes, back up the complete database, retain application versions and verify restoration. Start backend/Flyway/health before frontend traffic. Do not delete historical migrations or actual volumes to fix errors.

Backups contain account hashes/business data; save them outside public source in a restricted location with mode 0600, without printing passwords/content. Restore into a new Compose project, separate MySQL volume and different web port using matching validated images (or rebuild them). Start MySQL, import the full backup, then start backend/frontend. Verify migrations, health, role logins, bucket conservation, partner balances, original documents/events and private response snapshots. See [Deployment and restoration](docs/部署说明.md).

External hosting requires authorization, trusted HTTPS, secure cookies, network isolation, least privilege, backups and monitoring. Named volumes/strong passwords do not automatically provide backup or HA. `docker compose -p crateloop down` retains the database. **Remove volumes only for explicitly disposable test resources after checking the project identity.**

## Validation

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p crateloop config --quiet
git diff --check
python3 scripts/release-check.py
```

Backend unit/integration tests cover quantities and HTTP/JPA. Integration credentials are dynamically generated in isolated H2. Frontend tests cover permissions, classification inputs, integer payloads and actual CSRF handling. Docker backend builds execute all tests. A fresh actual MySQL environment separately verifies deployment:

```sh
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
python3 scripts/smoke.py --verify
```

Explicit writes require this project's isolated disposable database and create TEST records/random accounts. After page operations, capture private current state in ignored `output/qa-state.json`; never publish it. Verify compares business, directory and permission responses after restart or independent restore selected by `TEST_URL`.

Checks include issue/return, shortages, repair/retirement, canceled reservations, changed/disputed statements, concurrent excessive approvals, exact retries, stale versions, fractional rejection, department/SELF/partner isolation, export and account revocation. Real pages, migration/health, persistence/restoration and private-credential exclusion are separate release gates.

## Limits, security and troubleshooting

Each primary type defaults to 1,000 resources, adjustable through `maxMovements` from 100 to 1,000. Directory reads are bounded at 10,000 and pages at 100. Single-organization serial writes/application login limits are for learning deployment. Large concurrency, external integration and HA are unverified. No serial tracking, scanning/RFID/GPS, rental billing, payments, goods inventory, notifications, file uploads or multi-tenancy. No mandatory AI/external accounts or simulated-success business mode.

Sessions are not stored in persistent browser storage; passwords never appear in API/business evidence. Password changes invalidate old sessions. Writes require CSRF/live authorization, queries use parameters and JSON export inherits detail scope. See [SECURITY](SECURITY.md). Keep `.env`, snapshots, backups and unredacted logs out of source.

| Symptom | Check |
|---|---|
| Initial health fails | This project's MySQL/backend logs, configuration, registries and migrations; preserve original volumes |
| Port occupied | Override `WEB_PORT` and leave other projects running |
| Approval unavailable | Role, state and independent actors; partner bindings never grant internal access |
| Insufficient/mismatched quantity | Current/reserved balances and actual receipt, without direct bucket edits |
| Cannot confirm statement | Finish handoffs; cancel/recreate a stale frozen statement after new postings |
| Migration validation fails | Trusted published files/history and new-version migration |
| Existing password unchanged by env | Initialization-only; own password change/authorized reset |

## License, feedback and contact

See [Operations](docs/操作手册.md), [API](docs/接口说明.md) and [CONTRIBUTING](CONTRIBUTING.md). Submit redacted reproducible issues and privately report security concerns. The ledger does not establish actual equipment location, ownership, transport delivery or financial responsibility. The operator verifies evidence, quantities, physical safety and deployment.

Own code uses [ZhuaTech Non-Commercial Source License 1.0](LICENSE), permitting personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** Enterprise private deployment, paid delivery/services, SaaS, resale and in-depth customization require separate authorization. Preserve attribution, website, copyright, license and licensing contacts. Third-party licenses remain separate. This is publicly readable non-commercial source, not an OSI-approved license; software is provided as is with no unverified production-readiness claim.

For commercial licensing, in-depth custom development, deployment or system integration, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
