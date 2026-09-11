# Gargantua Control Plane

[![License](https://img.shields.io/github/license/GiskardB/gargantua-control-plane?style=flat-square&color=blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-007396?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)

**The fleet manager for the Gargantua platform** — where agents get published,
discovered, governed, and deployed. If [Gargantua](../gargantua) runs *one* agent, the
Control Plane is what you point at when you're running many.

> **Optional, not required.** A single agent needs nothing here — a hand-written
> `manifest.yaml` + `SKILL.md` and the Runtime jar is a complete setup on its own. The
> Control Plane exists for the case where several agents, several environments, or
> several teams need one shared source of truth for what's published and what's allowed.

> **Status: working MVP.** All four subsystems below exist, are wired together, and are
> covered by unit + end-to-end API tests. Persistence is durable (JDBC document store +
> blob store), not in-memory — publish something, restart the process, it's still there.

## Role in the platform

```
Gargantua Studio  →  desired state  →  Control Plane  →  Runtime state
```

The Control Plane **defines** desired state and policy. The [Gargantua Runtime](../gargantua)
**applies** it, but never owns it as the source of truth — it reaches the Control Plane
over API, or receives what it needs inside a bundle. This separation is **ADR-004** in the
Runtime repo and is binding — see
[`gargantua/docs/architecture/runtime-decisions.md`](https://github.com/GiskardB/gargantua/blob/main/docs/architecture/runtime-decisions.md).

```
                     +--------------------------------+
                     |        Gargantua Studio         |
                     +----------------+---------------+
                                      |
                               desired state
                                      |
                     +----------------v---------------+
                     |    Gargantua Control Plane      |   ← this repository
                     |----------------------------------|
                     | Registry | Catalog                |
                     | Policy Manager | Deployment Manager|
                     +----------------+---------------+
                                      |
                         API / bundle distribution
                                      |
                     +----------------v---------------+
                     |        Gargantua Runtime         |
                     +----------------------------------+
```

Guiding principle: **Configuration State** (agent definitions, bundles, versions) is
managed here, by the Registry. **Runtime State** (sessions, conversations, memory,
checkpoints) stays in the Runtime — the Control Plane never touches it.

## The four subsystems

### Registry
The repository of immutable artifacts.
- Storage, versioning, environment promotion (`DEV` → `STAGING` → `PRODUCTION`), rollback, bundle download.
- Holds: the manifest, the runnable `.gbundle` zip, SHA-256 checksums, per-version metadata.
- **Holds no runtime state** — no sessions, no conversations.

### Catalog
The discovery system, populated by running Runtimes over A2A.
- **No code or bundles here**, only metadata.
- Holds: published capabilities, ownership, description, endpoint, running versions.

### Policy Manager
Centralized governance: RBAC, security, compliance, cost control, privacy. Deny-overrides
evaluation with exact and prefix matching. The Runtime applies the policies it receives
from here; local enforcement at the individual workload level is still open on the Runtime
side (see that repo's platform-handoff doc).

### Deployment Manager
Deployment lifecycle: register a desired deployment, launch it (spawns the Runtime
container via a configurable command template, tracks its assigned port), observe and
update state (`PENDING` → `PROGRESSING` → `HEALTHY`/`FAILED`), undeploy.

## Stack and structure

- **Java 25 (LTS) + Spring Boot 4.1** (web, validation, actuator, Jackson YAML).
- **Shared domain model**: vocabulary types (`WorkloadKind`, `Capability`, …) come from
  `io.github.giskardb:agent-core`, the Spring-free domain module published by the Runtime
  repo. The Control Plane speaks the **same types** as the components it serves instead of
  mirroring them locally — so Registry and Runtime name the same things the same way. What
  stays local is only the Control Plane's own *projection*: `PublishedBundle` (kind +
  capability + checksum, indexed for the Registry), distinct from the domain
  `BundleDescriptor` (provenance: signature, image).

```
ai.gargantua.controlplane                (shared domain: ai.gargantua.core.* from agent-core)
├── registry/     Registry (port) + Jdbc-backed impl, RegistryService, ManifestReader,
│                 model/PublishedBundle (registry projection), BundleArtifact
├── catalog/      Catalog (port) + Jdbc-backed impl, CatalogService
├── policy/       PolicyManager (port) + Jdbc-backed impl, PolicyService (+ evaluate)
├── deployment/   DeploymentManager (port) + Jdbc-backed impl, DeploymentService,
│                 RuntimeLauncher (spawns the Runtime process for a deployment)
├── storage/      BlobStore (Postgres-backed, or filesystem via a profile) + JdbcDocumentStore
└── common/       uniform error handling (ApiError), Clock, ObjectMapper, RootController
```

Every subsystem sits behind a **port** (ADR-004). Persistence is **durable**: state
(registry, catalog, policy, deployment) lives in a small JDBC document store (JSON
aggregates in a `cp_document` table); manifest and bundle bytes live in the `BlobStore`.
Backends are chosen per Spring profile without touching any caller:

| What | Default (zero-infra) | `postgres` profile |
|---|---|---|
| State (document store) | **embedded H2** on disk (`./data/db`) | **Postgres** (same schema) |
| Manifest/bundle blobs | `PostgresBlobStore` (in `cp_document`, works on H2 too) | `PostgresBlobStore` |
| Filesystem blobs | opt-in via the `filesystem` profile | opt-in via the `filesystem` profile |

## Build and run

```bash
mvn test              # unit + end-to-end API tests (registry, catalog, policy, deployment)
mvn spring-boot:run    # http://localhost:8080, durable on ./data — override with --server.port=NNNN

# Cave-backed profile, no code changes needed:
mvn spring-boot:run -Dspring-boot.run.profiles=postgres   # state + blobs → Postgres
```

`GET /` returns an index of the subsystems and their base paths; `GET /actuator/health`
reports health. Persistence is verified end-to-end: publish something, restart the
process, and bundles, promotions, capabilities and policies are all still there.

The fastest way to run it as part of the full platform is
[`gargantua-compose`](../gargantua-compose), which wires this service to Postgres and
[Gargantua Studio](../gargantua-studio) in one `docker compose up`.

## API (v1)

| Method & path | What it does |
|---|---|
| `POST /api/v1/registry/bundles` | Publish a bundle from a `gargantua.ai/v1` manifest (YAML/JSON). Validates shape and invariants, computes the SHA-256 checksum, indexes capabilities into the Catalog. Versions are immutable → `409` if already present. |
| `GET /api/v1/registry/bundles` | List every published bundle. |
| `GET /api/v1/registry/bundles/{name}/versions` | Versions of one bundle. |
| `GET /api/v1/registry/bundles/{name}/{version}` | One specific artifact. |
| `GET /api/v1/registry/bundles/{name}/{version}/manifest` | The raw manifest bytes. |
| `GET /api/v1/registry/bundles/{name}/{version}/bundle` | The assembled, runnable `.gbundle` zip. |
| `POST /api/v1/registry/bundles/{name}/{version}/promote` | Promote a version to an environment (`DEV`/`STAGING`/`PRODUCTION`). |
| `DELETE /api/v1/registry/bundles/{name}/{version}` | Remove a bundle version; de-indexes its capabilities from the Catalog. |
| `GET /api/v1/catalog/capabilities` | Published capabilities and who provides them. |
| `GET /api/v1/catalog/capabilities/{name}` | One capability and its providers. |
| `POST /api/v1/catalog/agents` | A2A registration of a running agent (endpoint + capabilities served). |
| `GET /api/v1/catalog/agents[?capability=…]` | Registered agents, optionally filtered by capability. |
| `POST /api/v1/policies` · `GET` · `GET /{id}` · `PUT /{id}` · `DELETE /{id}` | CRUD for governance policies. |
| `POST /api/v1/policies/evaluate` | Decide a target against the policies: `{target}` → `{decision, matched}`. Exact or prefix match (`target:*`); **deny overrides**; no match = ALLOW. Definition, not enforcement (ADR-004). |
| `POST /api/v1/deployments` | Register a desired deployment (validates the bundle exists → `404` if not); starts `PENDING`. |
| `GET /api/v1/deployments[?environment=…&bundle=…]` · `GET /{id}` | List (filterable by environment/bundle), and detail. |
| `POST /api/v1/deployments/{id}/launch` | Starts the Runtime container for this deployment (async — returns immediately in `PROGRESSING`, poll `GET /{id}` for `HEALTHY`/`FAILED`). |
| `PUT /api/v1/deployments/{id}/state` | Update the observed state (and, once known, the assigned port). |
| `DELETE /api/v1/deployments/{id}` | Clear a deployment record. |

Errors use a uniform `ApiError` body (`status`, `error`, `message`, `path`, `timestamp`).

## What's left (post-MVP)

Registry, Catalog, Policy and Deployment are functional and persistent. In rough order:
verifying the `postgres` profile against Cave's real services end-to-end, digital
signing of bundles, policy enforcement at the actual enforcement points (Runtime/Gateway),
real rollout orchestration (rolling/canary) in the Deployment Manager, and health/SLA
tracking in the Catalog.

## Where to next

- [`gargantua`](../gargantua) — the Runtime that applies what this Control Plane defines; see ADR-004 for the contract
- [`gargantua-studio`](../gargantua-studio) — the visual designer that publishes to and reads from this Control Plane
- [`gargantua-compose`](../gargantua-compose) — one command to run this service, Studio and Postgres together
