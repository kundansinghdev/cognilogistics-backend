# CogniLogistic Glossary

A single place to look up acronyms, domain terms, and business-rule (BR) text.
If a term shows up in the code or the V3.6 spec and is not here, add it.

## Acronyms and domain terms

- **TP — Transport Provider.** A tenant in the platform. Each TP is one logistics
  company; their users, offices, customers, and orders are all scoped to a single
  `tp_account_id`.
- **TP_ADMIN.** The privileged role within a TP account — the company's owner /
  admin user (legacy name `TP_PRIMARY`). Some operations (e.g. order reassignment,
  BR-05) are restricted to this role.
- **TP_TRANSPORT_MANAGER.** A non-privileged TP user, scoped to one or more branch
  offices via `user_office_assignments`. Can act on orders for offices they are
  assigned to (legacy name `TP_BRANCH`).
- **PTL — Part Truck Load.** A shared-vehicle order type. Multiple PTL orders may
  travel on the same vehicle. Vehicle registration is not required at fleet
  confirmation.
- **FTL — Full Truck Load.** A dedicated-vehicle order type. Fleet confirmation
  requires a vehicle registration (BR-09) and a Vahan consent record (BR-10).
- **GR — Goods Receipt.** A printable HTML document generated for an order;
  exposed via `GET /api/v1/orders/{id}/gr`.
- **LR — Lorry Receipt.** The vehicle-level shipping document, post-UAT scope of
  the `fleet` module.
- **Vahan.** The Indian Ministry of Road Transport & Highways' national vehicle
  registration database. We perform consent-gated lookups against it for FTL
  orders to verify a vehicle and its driver. See `integrationclient/vahan`.
- **Sarathi.** The corresponding national driving-licence database (driver
  verification). See `integrationclient/sarathi`.
- **GST.** Goods and Services Tax — the GSTIN-based business identity lookup used
  during company onboarding. See `integrationclient/gst`.
- **Shadow customer (BR-04).** A customer record that the platform creates
  automatically when a TP user enters an order against a phone number that has no
  existing customer row. The shadow customer has the WhatsApp phone set but null
  name / email; it is upgraded to a real customer when that customer self-onboards
  through the customer portal.
- **Customer portal.** The customer-facing surface (`/api/v1/portal/...`) where a
  customer logs in via OTP to view and act on their own orders. Backed by
  `order/service/portal/PortalAuthService` and `PortalOrderService`.
- **Tender.** Post-UAT concept (`tender` module): a TP-PRIMARY puts an order out
  to bid across partner TPs; the winning bid creates a TP assignment.
- **Office (a.k.a. Branch).** A physical or logical branch of a TP account. Orders
  are assigned to exactly one office once acknowledged. Modeled as
  `user/model/Office.java`.

## Release line — V3.6

V3.6 is the current backend release (`BE-OM V3.6`) covered by this codebase. Key
differences vs. earlier lines that a junior is most likely to bump into:

- **`ASSIGNED` status was removed.** In V3.6 there is no separate `ASSIGNED` state.
  Setting `office_id` is an attribute operation, performed as part of the combined
  `POST /orders/{id}/acknowledge` step. The state machine goes
  `CREATED → ACKNOWLEDGED` directly; the office is set on the same call when the
  order has no office yet.
- **`FLEET_PENDING` is not a status.** It is a computed query filter (BR-07) — see
  `order/model/OrderStatus.java`.
- **In-process events only.** Domain events are published through Spring's
  `ApplicationEventPublisher`; Event-Hubs / Kafka fan-out is post-UAT.

## Business rules (BR-01 through BR-10)

Listed in the order they appear in `OrderService` and `OrderStateMachine`.

- **BR-01 — No skipping states.** An order can only move to the next status in the
  forward path; jumps such as `CREATED → IN_TRANSIT` are rejected as
  `INVALID_TRANSITION`. Enforced by `OrderStateMachine.requireTransition`.
- **BR-02 — Cancellation cutoff.** An order may be cancelled from `CREATED`,
  `ACKNOWLEDGED`, or `FLEET_CONFIRMED`, but not from `IN_TRANSIT` or `DELIVERED`.
  Violation surfaces as `CANCELLATION_NOT_ALLOWED`.
- **BR-03 — ACKNOWLEDGED is the mandatory gate.** No order may move directly from
  `CREATED` to `FLEET_CONFIRMED` — `ACKNOWLEDGED` is always the intermediate step
  (no admin override, no shortcut endpoint), so a branch office must explicitly
  review the order before any fleet is committed; bypass attempts are rejected
  as `INVALID_TRANSITION` (422).
- **BR-04 — Shadow customer on create.** When an order is created against a
  WhatsApp phone with no existing customer record, the platform synchronously
  creates a "shadow" customer (phone only, name/email null) and links the order
  to it. See `OrderService.create` and `CustomerService.findOrCreateShadow`.
- **BR-05 — Reassignment requires TP_ADMIN.** Only users with the
  `TP_ADMIN` role can move an order to a different branch office via
  `POST /orders/{id}/reassign`. Reassignment is also blocked once the order is in
  `FLEET_CONFIRMED` or later.
- **BR-06 — Audit every status change.** Every status transition, including the
  initial `CREATED` (with `from_status = NULL`), must produce a row in
  `order_status_log`. Enforced by `OrderService.writeLog` calls in every
  transition method.
- **BR-07 — `FLEET_PENDING` is a computed filter, not a status.** The "pending
  fleet" view is the SQL predicate `status = 'ACKNOWLEDGED' AND (order_type =
  'PTL' OR vehicle_id IS NOT NULL)`. There is no `FLEET_PENDING` enum value.
- **BR-08 — Editable only before FLEET_CONFIRMED.** Once an order reaches
  `FLEET_CONFIRMED`, `IN_TRANSIT`, `DELIVERED`, or `CANCELLED`, no field on the
  order is editable. Attempts return `FORBIDDEN`.
- **BR-09 — FTL requires a vehicle registration.** When confirming the fleet for
  an FTL order, `vehicleRegistration` is mandatory. PTL orders may confirm fleet
  with no vehicle attached.
- **BR-10 — Vahan consent before FTL fleet confirmation.** Before an FTL order
  can move to `FLEET_CONFIRMED`, a positive Vahan consent log row for the same
  `(orderId, vehicleRegistration)` pair must exist. Enforced via the
  `FleetConfirmGuard` SPI implemented by `VahanService`.
