# Business Requirements Document — Phase 1
## **EaglesEye** — Fleet Telematics Platform

| Field | Value |
|---|---|
| Document | BRD — Phase 1 (Core Tracking Platform) |
| Version | 0.1 — Draft for review |
| Date | 03 August 2026 |
| Owner | Moataz — Product & Engineering |
| Status | Draft |
| Related | Product Roadmap Phases 1–4 |

> **Naming note:** The platform is named **EaglesEye** — *"we see you, wherever you are."* Chosen 06 Aug 2026, replacing the draft codename "Marsad" (مرصد — observatory).

---

## 1. Executive Summary

EaglesEye is a hardware-agnostic fleet telematics platform targeting small and mid-sized fleets (10–500 vehicles) in Egypt, the Gulf, and the wider MENA region.

Incumbent platforms — Samsara, Motive, Verizon Connect — bundle proprietary hardware with proprietary software. Customers cannot switch software without replacing every device in every vehicle. Regional operators, meanwhile, already own low-cost Chinese and Lithuanian GPS units and are locked into weak, aging local tracking portals.

**Phase 1 delivers the smallest platform that can carry a real fleet in production**: multi-protocol device ingestion, live tracking, trustworthy trip history, geofencing, alerting, and device health monitoring — delivered as a multi-tenant SaaS.

Phase 1 deliberately excludes maintenance, DVIR, fuel analytics, safety scoring, dispatch optimisation, mobile apps, and video. Those are Phase 2+ and depend on the data spine built here.

**Phase 1 is complete when two paying fleets run their daily operations on EaglesEye and stop using their previous system.**

---

## 2. Business Context

### 2.1 Problem

| Stakeholder | Pain today |
|---|---|
| Fleet owner | Locked into one hardware vendor; switching software means re-fitting every vehicle |
| Fleet owner | Local portals are unreliable, Arabic support is poor, no API, no integration with their ERP |
| Operations manager | Distance and trip reports don't match the odometer, so reports aren't trusted |
| Operations manager | Alerts are noisy or absent; no way to know a vehicle left a site after hours |
| Installer / reseller | Sells hardware but has no white-labelable platform to sell alongside it |
| IT / ERP owner | No API, no webhooks — data lives in a silo, re-keyed manually into Odoo/SAP |

### 2.2 Positioning

**"Bring your own hardware."** EaglesEye supports devices customers already own. This is the commercial wedge, and it defines the Phase 1 architecture: protocol handling must be pluggable from day one, never assumed to be a single vendor.

Secondary differentiators (built in Phase 1, monetised later): a real public API with webhooks, native Arabic/RTL interface, and regional data residency.

### 2.3 Target customer for Phase 1 pilot

- Fleet size 20–150 vehicles
- Sectors: distribution and last-mile delivery, construction equipment, contracted passenger transport
- Already has GPS devices fitted (preferably Teltonika or Concox family)
- Has an operations person who looks at a tracking screen daily

---

## 3. Goals and Success Metrics

### 3.1 Business goals

| ID | Goal |
|---|---|
| BG-01 | Two paying pilot fleets in production, minimum 200 vehicles combined |
| BG-02 | One installer/reseller partner actively fitting devices and referring customers |
| BG-03 | Prove that a new device protocol can be added in under two weeks of effort |
| BG-04 | Establish a data spine that Phase 2 modules can build on without re-architecture |

### 3.2 Success metrics

| ID | Metric | Target |
|---|---|---|
| SM-01 | Pilot fleets fully migrated off previous platform | 2 |
| SM-02 | Reported trip distance vs. vehicle odometer, monthly | Within 2% |
| SM-03 | Device-to-map latency (position visible on live screen) | ≤ 5 seconds, p95 |
| SM-04 | Platform availability, ingestion path | ≥ 99.5% monthly |
| SM-05 | Vehicles reporting normally on any given day | ≥ 97% |
| SM-06 | Support tickets requiring engineer intervention | ≤ 5 per 100 vehicles per month |
| SM-07 | Sustained ingestion capacity, load-tested | ≥ 2,000 messages/second |
| SM-08 | Distinct device protocols supported in production | ≥ 3 |

---

## 4. Scope

### 4.1 In scope — Phase 1

1. Multi-protocol device ingestion (3 protocol families + generic MQTT/JSON)
2. Device and asset lifecycle management (register, assign, decommission)
3. Live tracking console with map
4. Trip reconstruction and history playback
5. Geofence management and geofence-based events
6. Rules and alerting engine (core event types)
7. Device health and diagnostics console
8. Standard operational reports and exports
9. Multi-tenancy, user management, RBAC
10. Public REST API and webhooks (foundation)
11. Arabic / English bilingual UI with RTL support

### 4.2 Explicitly out of scope — deferred

| Deferred item | Target phase | Rationale |
|---|---|---|
| Maintenance scheduling, service reminders | Phase 2 | Requires stable odometer/engine-hours data from Phase 1 |
| DVIR and driver mobile app | Phase 2 | Separate client surface, separate release train |
| Fuel level analytics, drain/refuel detection | Phase 2 | Requires sensor calibration workflow and field process |
| Driver safety scoring | Phase 2 | Requires accelerometer event stream maturity |
| Routing and dispatch optimisation | Phase 3 | Depends on reliable trip and geofence data |
| ERP connectors (Odoo, SAP) | Phase 3 | Depends on stable public API |
| Regulatory integrations (Wasl, RTA) | Phase 3 | Per-market, sales-driven |
| Video / AI dash cameras | Phase 4 | Hardware, bandwidth, and storage economics |
| Reseller white-label portal | Phase 3 | *Data model must support it now — UI comes later* |

### 4.3 Architectural obligations without Phase 1 features

These are not features but must be designed in now, because retrofitting them is expensive:

| ID | Obligation |
|---|---|
| AO-01 | Tenant hierarchy supports reseller → customer parent/child relationships |
| AO-02 | Tenant record carries branding attributes (logo, colours, domain) even if unused |
| AO-03 | Every domain event is published to the event bus, whether or not a consumer exists yet |
| AO-04 | Protocol decoders are isolated plugins with no coupling to business logic |
| AO-05 | All position and event data is tenant-scoped and enforced at the database level |

---

## 5. Stakeholders and Personas

| Persona | Role | Primary need in Phase 1 |
|---|---|---|
| **Fleet Owner** | Buys the platform | Trustworthy reports; knowing vehicles are being used properly |
| **Operations Manager** | Daily user | Live map, alerts, "where is vehicle X and where has it been" |
| **Dispatcher** | Daily user | Live status of vehicles; geofence arrival/departure |
| **Installer / Technician** | Field partner | Fast device registration; instant confirmation the unit is reporting |
| **Platform Admin (internal)** | EaglesEye team | Tenant provisioning, device health across all fleets, diagnostics |
| **Integrator / ERP developer** | Customer's IT | API access to positions, trips, and events |

---

## 6. Business Requirements

| ID | Requirement | Priority |
|---|---|---|
| BR-01 | The platform shall accept telemetry from GPS devices of multiple manufacturers without requiring proprietary hardware | Must |
| BR-02 | Adding support for a new device protocol shall not require changes to application or business logic | Must |
| BR-03 | Multiple customer organisations shall be served from a single deployment with strict data isolation | Must |
| BR-04 | Reported distance and trip data shall be accurate enough to be used for billing and payroll | Must |
| BR-05 | Operations staff shall be notified of defined operational events without monitoring screens continuously | Must |
| BR-06 | The internal team shall be able to diagnose a non-reporting device remotely, without a site visit, in the majority of cases | Must |
| BR-07 | Customers shall be able to extract their data programmatically | Must |
| BR-08 | The interface shall be usable by Arabic-speaking staff with no English proficiency | Must |
| BR-09 | Historical telemetry shall be retained for a defined period and remain queryable | Must |
| BR-10 | The platform shall degrade safely when devices are offline, and reconcile data on reconnection without loss or duplication | Must |

---

## 7. Functional Requirements

### 7.1 Device Ingestion (FR-ING)

| ID | Requirement | Priority |
|---|---|---|
| FR-ING-01 | Accept and decode Teltonika Codec 8 / 8E over TCP and UDP | Must |
| FR-ING-02 | Accept and decode one Chinese-family protocol (Concox/Jimi GT06 family) | Must |
| FR-ING-03 | Accept and decode one additional protocol (Queclink or Ruptela) | Should |
| FR-ING-04 | Accept generic JSON telemetry over MQTT for modern and custom devices | Must |
| FR-ING-05 | Authenticate incoming connections by device identifier (IMEI) against registered devices | Must |
| FR-ING-06 | Reject and log telemetry from unregistered device identifiers without disrupting service | Must |
| FR-ING-07 | Handle buffered/offline backlogs: accept out-of-order historical records and place them correctly in the timeline | Must |
| FR-ING-08 | Deduplicate repeated records idempotently using device ID + timestamp | Must |
| FR-ING-09 | Persist raw undecoded payloads for a defined retention window for debugging | Must |
| FR-ING-10 | Support device-initiated reconnect storms without message loss | Must |
| FR-ING-11 | Send acknowledgements to devices per protocol specification so devices clear their buffers | Must |

### 7.2 Device and Asset Management (FR-DEV)

| ID | Requirement | Priority |
|---|---|---|
| FR-DEV-01 | Register a device by IMEI, protocol type, model, and SIM details | Must |
| FR-DEV-02 | Assign a device to a vehicle or asset; maintain assignment history over time | Must |
| FR-DEV-03 | Maintain a vehicle/asset record: plate number, make, model, year, type, group | Must |
| FR-DEV-04 | Group vehicles into fleets, branches, or categories for filtering and permissions | Must |
| FR-DEV-05 | Bulk import vehicles and devices via spreadsheet | Should |
| FR-DEV-06 | Unassign or decommission a device without deleting its historical data | Must |
| FR-DEV-07 | Record installer, installation date, and installation notes per device | Should |
| FR-DEV-08 | Show installation confirmation: live indicator that a newly registered device is reporting valid GPS | Must |

### 7.3 Live Tracking (FR-TRK)

| ID | Requirement | Priority |
|---|---|---|
| FR-TRK-01 | Display all tenant vehicles on a map with current position and status | Must |
| FR-TRK-02 | Update positions in near real time without page reload | Must |
| FR-TRK-03 | Show per-vehicle state: moving, idling, stopped, offline, no GPS fix | Must |
| FR-TRK-04 | Display vehicle detail panel: speed, heading, last update time, ignition, battery/voltage, address | Must |
| FR-TRK-05 | Reverse-geocode coordinates to a human-readable address | Must |
| FR-TRK-06 | Filter and search the map by group, status, plate number, or driver | Must |
| FR-TRK-07 | Cluster markers when vehicle density is high | Should |
| FR-TRK-08 | Follow-vehicle mode that keeps a selected vehicle centred | Should |

### 7.4 Trips and History (FR-TRP)

| ID | Requirement | Priority |
|---|---|---|
| FR-TRP-01 | Automatically segment raw positions into trips and stops using ignition and movement rules | Must |
| FR-TRP-02 | Snap trip paths to the road network (map matching) to correct GPS noise | Must |
| FR-TRP-03 | Calculate per trip: start/end time, start/end location, distance, duration, max speed, average speed, idle time | Must |
| FR-TRP-04 | Calculate per stop: location, arrival, departure, duration | Must |
| FR-TRP-05 | Replay a vehicle's history on the map for a selected date range with playback controls | Must |
| FR-TRP-06 | Display a daily summary per vehicle: total distance, driving time, idle time, stop count | Must |
| FR-TRP-07 | Recompute trips retroactively when late-arriving buffered data is received | Must |
| FR-TRP-08 | Allow configurable trip detection parameters per tenant (idle threshold, minimum trip distance) | Should |

### 7.5 Geofencing (FR-GEO)

| ID | Requirement | Priority |
|---|---|---|
| FR-GEO-01 | Create circular and polygon geofences by drawing on the map | Must |
| FR-GEO-02 | Name, categorise, and colour-code geofences (customer site, depot, restricted zone) | Must |
| FR-GEO-03 | Assign geofences to specific vehicles or vehicle groups | Must |
| FR-GEO-04 | Detect and record entry and exit events with accurate timestamps | Must |
| FR-GEO-05 | Calculate dwell time inside a geofence | Must |
| FR-GEO-06 | Apply time-window rules (e.g. only trigger outside working hours) | Should |
| FR-GEO-07 | Bulk import geofences from coordinates or GeoJSON | Could |

### 7.6 Rules and Alerting (FR-ALT)

| ID | Requirement | Priority |
|---|---|---|
| FR-ALT-01 | Support these event types: speeding, prolonged idling, geofence entry, geofence exit, ignition on/off, unauthorised movement (motion without ignition), power disconnect, low battery, SOS/panic button, device offline | Must |
| FR-ALT-02 | Configure rules per vehicle or vehicle group with thresholds and active time windows | Must |
| FR-ALT-03 | Deliver notifications via email and in-app notification centre | Must |
| FR-ALT-04 | Deliver notifications via webhook to customer endpoints | Must |
| FR-ALT-05 | Deliver notifications via WhatsApp or SMS | Should |
| FR-ALT-06 | Suppress duplicate and repeating alerts within a configurable cooldown window | Must |
| FR-ALT-07 | Maintain a searchable alert history with acknowledgement status | Must |
| FR-ALT-08 | Allow per-user subscription to specific alert types | Must |

> **Note:** FR-ALT-06 is a product-critical requirement. An alerting system that generates noise is abandoned by users within two weeks and takes the platform's credibility with it.

### 7.7 Device Health and Diagnostics (FR-HLT)

| ID | Requirement | Priority |
|---|---|---|
| FR-HLT-01 | Dashboard listing all devices with last-seen timestamp, sorted by staleness | Must |
| FR-HLT-02 | Display per device: GSM signal strength, GPS satellite count/fix quality, external voltage, internal battery, firmware version, protocol, current configuration profile | Must |
| FR-HLT-03 | Classify non-reporting causes where determinable: no network, no GPS fix, power disconnected, device silent | Must |
| FR-HLT-04 | Alert internal team when a device stops reporting beyond a configurable threshold | Must |
| FR-HLT-05 | Show connection history per device (connects, disconnects, decode errors) | Must |
| FR-HLT-06 | Cross-tenant health view for the internal support team | Must |
| FR-HLT-07 | Flag devices whose live configuration has drifted from their assigned profile | Should |
| FR-HLT-08 | Track SIM data consumption per device where available | Could |

> **Note:** This module carries no customer-facing glamour and is the single highest-leverage investment in Phase 1. Without it, support cost scales linearly with vehicle count and the business does not survive its own growth.

### 7.8 Reporting (FR-RPT)

| ID | Requirement | Priority |
|---|---|---|
| FR-RPT-01 | Trip report per vehicle or group over a date range | Must |
| FR-RPT-02 | Daily/weekly/monthly distance and utilisation summary | Must |
| FR-RPT-03 | Stop and idle report | Must |
| FR-RPT-04 | Speeding violations report | Must |
| FR-RPT-05 | Geofence activity report (visits, dwell time, site arrival compliance) | Must |
| FR-RPT-06 | Vehicle inactivity / non-reporting report | Must |
| FR-RPT-07 | Export any report to Excel and PDF | Must |
| FR-RPT-08 | Schedule reports for automatic email delivery | Should |

### 7.9 Tenancy, Users, and Access Control (FR-SEC)

| ID | Requirement | Priority |
|---|---|---|
| FR-SEC-01 | Provision isolated tenant organisations; no tenant can access another's data under any condition | Must |
| FR-SEC-02 | Support parent/child tenant relationships for future reseller hierarchy | Must |
| FR-SEC-03 | Roles: Platform Admin, Tenant Admin, Manager, Viewer, Installer | Must |
| FR-SEC-04 | Restrict user visibility to assigned vehicle groups | Must |
| FR-SEC-05 | Enforce authentication with password policy and session management | Must |
| FR-SEC-06 | Support two-factor authentication | Should |
| FR-SEC-07 | Maintain an audit log of administrative actions | Must |
| FR-SEC-08 | Enforce tenant isolation at the database layer, not only in application code | Must |

### 7.10 Public API and Integration (FR-API)

| ID | Requirement | Priority |
|---|---|---|
| FR-API-01 | REST API exposing vehicles, devices, positions, trips, geofences, and events | Must |
| FR-API-02 | Token-based API authentication, scoped per tenant, revocable | Must |
| FR-API-03 | Outbound webhooks for alert and geofence events, with retry and failure backoff | Must |
| FR-API-04 | Published OpenAPI specification | Must |
| FR-API-05 | Per-tenant API rate limiting | Must |
| FR-API-06 | Bulk historical data export | Should |

### 7.11 Localisation (FR-LOC)

| ID | Requirement | Priority |
|---|---|---|
| FR-LOC-01 | Full Arabic and English UI with correct RTL layout | Must |
| FR-LOC-02 | Per-user language preference | Must |
| FR-LOC-03 | Per-tenant timezone; all timestamps displayed in tenant local time | Must |
| FR-LOC-04 | Arabic content in reports and exported PDFs renders correctly | Must |
| FR-LOC-05 | Metric units throughout (km, litres, °C) | Must |

---

## 8. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Throughput | Sustain 2,000 telemetry messages/second with headroom to 5,000 under burst |
| NFR-02 | Latency | Position visible on live console within 5 seconds of device transmission (p95) |
| NFR-03 | Availability | 99.5% monthly for the ingestion path; ingestion must survive console/API outages |
| NFR-04 | Durability | Zero acknowledged-message loss; acknowledge to device only after durable persistence |
| NFR-05 | Scalability | Ingestion layer horizontally scalable without device reconfiguration |
| NFR-06 | Data retention | 12 months of positions online and queryable; archive thereafter |
| NFR-07 | Query performance | 30-day history for one vehicle returns in under 3 seconds |
| NFR-08 | Security | TLS in transit; encryption at rest; secrets never in source control |
| NFR-09 | Data residency | Deployable to a customer-specified region for regulatory requirements |
| NFR-10 | Observability | Metrics, structured logs, and tracing on the ingestion path; alerting on pipeline lag |
| NFR-11 | Recovery | Documented backup and restore; RPO ≤ 15 minutes, RTO ≤ 4 hours |
| NFR-12 | Browser support | Current Chrome, Edge, Safari, Firefox; responsive down to tablet |
| NFR-13 | Maintainability | New protocol decoder integrates without modifying core services |

---

## 9. Assumptions

| ID | Assumption |
|---|---|
| AS-01 | Pilot customers already own compatible GPS hardware, or will purchase through our installer partner |
| AS-02 | Physical installation, wiring, and device configuration are performed by third-party installers, not by us |
| AS-03 | SIM cards and data plans are procured by the customer or installer; we consume connectivity, we do not resell it |
| AS-04 | Devices are capable of buffering data offline and retransmitting on reconnect |
| AS-05 | Phase 1 requires no CAN bus or J1939 data; ignition, GPS, and voltage from the device's own inputs are sufficient |
| AS-06 | Self-hosted routing, map-matching, and geocoding services are viable at pilot scale |
| AS-07 | Development is performed by a small team; scope reflects that constraint |

---

## 10. Dependencies

| ID | Dependency | Risk if unmet |
|---|---|---|
| DP-01 | Physical device lab (3–5 units, live SIMs) available before ingestion development begins | Decoders cannot be validated; protocol bugs surface only in production |
| DP-02 | Installer/workshop partner identified and engaged | No path to installation; pilot cannot be fitted |
| DP-03 | Pilot customers committed with signed pilot agreement | Building without a feedback loop |
| DP-04 | Cloud infrastructure and regional hosting provisioned | Delivery blocked |
| DP-05 | Map tiles, geocoding, and routing services deployed and licensed appropriately | Map and trip features non-functional |
| DP-06 | Legal review of licensing for all incorporated open-source components | Commercial and legal exposure |

---

## 11. Constraints

| ID | Constraint |
|---|---|
| CN-01 | Backend is Java on Quarkus; protocol decoding must be achievable on the JVM |
| CN-02 | Preference for permissively licensed open-source components over commercial services |
| CN-03 | Phase 1 must not adopt a design that prevents Phase 2–4 modules from being added |
| CN-04 | Infrastructure cost per vehicle per month must remain low enough to support regional price points |
| CN-05 | Device-side firmware is not modifiable; the platform must adapt to device behaviour, not the reverse |

---

## 12. Risks

| ID | Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|---|
| RK-01 | Trip distance disagrees with odometer, destroying report credibility | High | Medium | Map matching from day one; validate against a real vehicle over one month before pilot |
| RK-02 | Support burden scales with fleet size and overwhelms the team | High | High | Device health module is a Must, not a nice-to-have; classify failure causes automatically |
| RK-03 | Reconnect storms and buffered backlogs overwhelm ingestion | High | Medium | Load test with simulated backlog bursts before pilot; decouple decode from persistence via the event bus |
| RK-04 | Incorrect device sleep configuration drains a customer's vehicle battery | High | Medium | Standard tested configuration profiles per device model; installer checklist; voltage monitoring alerts |
| RK-05 | Alert noise causes users to disable notifications entirely | Medium | High | Cooldown and deduplication mandatory; tune thresholds with pilot users in week one |
| RK-06 | Protocol edge cases from device firmware variants | Medium | High | Persist raw payloads; build a replay harness for regression testing decoders |
| RK-07 | No installer partner secured, blocking deployment | High | Medium | Begin partner conversations before development starts, not after |
| RK-08 | Open-source licence incompatibility discovered late | High | Low | Legal review of the component list before code is written |
| RK-09 | Scope expands into Phase 2 features during pilot pressure | Medium | High | This document is the scope baseline; changes require explicit deferral of a Phase 1 item |

---

## 13. Delivery Milestones

| Milestone | Deliverable | Exit criteria | Indicative |
|---|---|---|---|
| **M0 — Hardware Lab** | Devices procured, SIMs active, raw traffic captured to a local listener | Live byte streams from 3 device models captured and documented | Weeks 1–2 |
| **M1 — Ingestion Spine** | Decoders, event bus, time-series persistence, device registry | 3 protocols decoding; 2,000 msg/s sustained in load test; backlog replay correct | Weeks 3–6 |
| **M2 — Tracking Console** | Multi-tenancy, auth, RBAC, live map, vehicle management | A test fleet is visible and correct on the live map | Weeks 7–9 |
| **M3 — Trips & Geofences** | Trip segmentation, map matching, history replay, geofence CRUD and events | Distance for a real test vehicle within 2% of odometer over 30 days | Weeks 10–12 |
| **M4 — Alerts, Health & Reports** | Rules engine, notification delivery, device health console, report suite | All Must-priority alert types firing correctly; health console diagnosing seeded failures | Weeks 13–15 |
| **M5 — Pilot Hardening** | API, webhooks, Arabic UI, observability, backup/restore | Pilot fleet #1 live in production | Weeks 16–18 |

Timeline is indicative and assumes DP-01 through DP-04 are satisfied on schedule.

---

## 14. Acceptance Criteria — Phase 1 Sign-off

Phase 1 is accepted when **all** of the following hold:

1. Three device protocol families are decoding correctly in production, verified against physical units.
2. Two pilot fleets, totalling at least 200 vehicles, are operating daily on the platform and have discontinued their previous system.
3. Monthly distance reporting agrees with vehicle odometers within 2%, verified on a sample of at least 10 vehicles.
4. All Must-priority functional requirements in Section 7 are implemented and demonstrated.
5. All Non-Functional Requirements in Section 8 are measured and met, with load test evidence.
6. A device that stops reporting is diagnosed to a probable cause from the health console, without a site visit, in at least 80% of cases.
7. Public API and webhooks are documented and consumed successfully by at least one external integration.
8. Arabic UI is verified end-to-end by a native Arabic-speaking pilot user, including exported reports.
9. Backup and restore has been executed successfully in a rehearsal.
10. A new protocol decoder has been added by a developer following documentation alone, in under two weeks.

---

## 15. Open Questions

| ID | Question | Owner | Needed by |
|---|---|---|---|
| OQ-01 | Which two customers are the committed pilots? Are agreements signed? | Business | Before M1 |
| OQ-02 | Which exact device models are in the pilot fleets? This determines protocol priority | Business | Before M0 |
| OQ-03 | Which installer/workshop partner will fit and service devices? | Business | Before M4 |
| OQ-04 | Commercial model — per vehicle per month, tiered, or hardware-bundled? | Business | Before M5 |
| OQ-05 | Primary launch market — Egypt, KSA, UAE? Drives data residency and regulatory sequencing | Business | Before M2 |
| OQ-06 | Is WhatsApp notification required at pilot, or is email sufficient for launch? | Product | Before M4 |
| OQ-07 | Data retention commitment offered to customers — 12 months, or longer? Affects storage cost model | Product | Before M1 |
| OQ-08 | Does any pilot customer require an ERP integration during Phase 1, or can it wait for Phase 3? | Business | Before M5 |

---

## 16. Revision History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1 | 03 Aug 2026 | Moataz | Initial draft |
