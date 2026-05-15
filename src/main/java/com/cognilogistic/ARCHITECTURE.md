# CogniLogistic Backend — Architecture Overview

## What this product is

CogniLogistic (a.k.a. Bhoomihaar Express, BE-OM V3.6) is a multi-tenant logistics
order-management SaaS. Each tenant is a Transport Provider (**TP**) account that owns
its customers, branch offices, users, and freight orders. Orders move through a fixed
lifecycle — `CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED`, with
`CANCELLED` reachable only before transit starts. All tenant data is isolated by
`tp_account_id`; cross-tenant reads must surface as `ORDER_NOT_FOUND`, not `FORBIDDEN`.

## Module map

Top-level packages under `com.cognilogistic`:

| Package              | Owns                                                                 |
|----------------------|----------------------------------------------------------------------|
| `auth`               | Phone-OTP login, PIN setup, JWT issuance/validation, refresh tokens. |
| `order`              | Order lifecycle, customers/companies, GR/LR docs, status audit log.  |
| `tender`             | Post-UAT scaffold for tenders/bids/TP-assignments (placeholder).     |
| `fleet`              | Post-UAT scaffold for fleet_orders, vehicles, drivers (placeholder). |
| `user`               | TP accounts, branch offices, user-office assignments.                |
| `integrationclient`  | External integrations: Vahan (RTO), GST, Sarathi (driving licence).  |
| `notificationclient` | Post-UAT scaffold for WhatsApp/push notifications (placeholder).     |
| `reports`            | Post-UAT scaffold for read-only counts/dashboards (placeholder).     |
| `platform`           | Cross-cutting: `BaseEntity` (audit timestamps), API kernel/error.    |
| `config`             | Directory containing JWT filter and security config (files declare package `com.cognilogistic.auth.security`). |

`CogniLogisticApplication` is the Spring Boot entry point and enables JPA auditing
and `@Async` execution.

## Request flow (single HTTP request)

```
HTTP request
   │
   ▼
JwtAuthFilter ────────────────────► populates SecurityContext with AuthPrincipal
   │  (parses Bearer, validates JWT)    (claim-based: userId, role, tpAccountId)
   ▼
@RestController                       e.g. OrderController
   │  binds DTO, resolves @CurrentUser
   ▼
@Service                              e.g. OrderService
   │
   ├──► OrderStateMachine.requireTransition(...)  (BR-01, BR-02)
   │
   ├──► JpaRepository<Order, Long>     (Spring Data JPA → Hibernate → MySQL)
   │
   └──► ApplicationEventPublisher      (in-process; will fan out to Event Hubs post-UAT)

Cross-cutting concerns wrap every request:
  • ApiException ──► GlobalExceptionHandler ──► ApiResponse<T> envelope
  • BaseEntity + @EnableJpaAuditing ──► auto-fills created_at / updated_at
  • Domain events published via ApplicationEventPublisher
```

The response envelope (`ApiResponse<T>`) is the only success/error shape the API ever
emits. `ApiException(ErrorCode, ...)` is the only way services signal a failure;
the global exception handler maps it to the right HTTP status and JSON body.

## Multi-tenancy rule

**Every query that touches tenant data MUST filter by `tpAccountId`.** The canonical
pattern is `OrderService.loadOrderForTp` (see `order/service/OrderService.java`):

1. Resolve the entity by id.
2. Compare its `tpAccountId` to the caller's `AuthPrincipal#tpAccountId()`.
3. On mismatch, throw `ORDER_NOT_FOUND` (not `FORBIDDEN`) — the caller must not be
   able to probe the existence of orders belonging to another tenant.

Repository methods that take `tpAccountId` as a parameter are preferred over
post-filtering in Java, but when bulk APIs (`findAllById`) are used, the service
must filter before returning anything to the controller.

## Cross-module decoupling — the FleetConfirmGuard pattern

`OrderService` enforces BR-10 (Vahan consent) without depending on the
`integrationclient` module. The dependency arrow points the right way:

```
   order  ──── declares interface FleetConfirmGuard
                                          ▲
                                          │ implements
                                          │
   integrationclient.vahan.VahanService ──┘
```

`FleetConfirmGuard` is declared as a nested interface inside `OrderService`. The
`VahanService` bean implements it; Spring wires it into `OrderController`, which
threads it down into `OrderService.confirmFleet`. The `order` package therefore
compiles without `integrationclient` on the classpath, and tests that need to skip
the consent check can pass `FleetConfirmGuard.NOOP`.

This is the pattern to replicate whenever a "downstream" module (here: order)
needs functionality from a peer module that should not be a compile-time dependency.

## Where to read first

For a new joiner, read in this order:

1. `order/model/OrderStatus.java` — the lifecycle enum.
2. `order/statemachine/OrderStateMachine.java` — BR-01 / BR-02.
3. `order/service/OrderService.java` — the canonical multi-tenant CRUD + transitions.
4. `order/controller/OrderController.java` — REST shape of the lifecycle.
5. `platform/api/ApiResponse.java` + `ErrorCode.java` — response envelope and error codes.
6. `auth/security/AuthPrincipal.java` + `JwtAuthFilter` (in `config/`) — auth model.

See `GLOSSARY.md` for acronyms (TP, FTL, PTL, GR/LR, Vahan, etc.) and the full
plain-English text of business rules BR-01 through BR-10.
