# Gargantua Control Plane

Control Plane della piattaforma **Gargantua AI Operating System**. Gestisce lo
stato *desiderato* della piattaforma — cosa deve esistere, chi può fare cosa,
cosa è disponibile — e lo espone a tutti gli altri componenti.

> **Stato: MVP funzionante.** I quattro sotto-sistemi esistono e rispondono via
> HTTP; la persistenza è in-memory più filesystem per i bundle, dietro *port* così
> da poter passare a Postgres/MinIO senza toccare i chiamanti. Vedi §5–§7.

---

## 1. Ruolo nella piattaforma

```
Gargantua Studio → Desired State → Control Plane → Runtime State
```

Il Control Plane **definisce** policy e stato desiderato. Il
[Gargantua Runtime](../gargantua) li **applica**, ma non li possiede mai come
fonte di verità: se ha bisogno di dati del Control Plane li raggiunge via API o
li riceve dentro il bundle. Questa separazione è ADR-004 nel repository
Runtime ed è vincolante — vedi
[`gargantua/docs/architecture/runtime-decisions.md`](../gargantua/docs/architecture/runtime-decisions.md).

Roadmap: **Phase 2** di
[`gargantua/docs/architecture/ai-operating-system.md`](../gargantua/docs/architecture/ai-operating-system.md).

## 2. Cosa dovrà contenere

Quattro sotto-sistemi, secondo la sezione 6 del documento di visione:

### 2.1 Agent Registry
Repository degli artefatti immutabili.
- Storage, versioning, rollback, promotion tra ambienti, download del bundle
- Contiene: `.gbundle`, versioni, metadata, checksum SHA-256, firma digitale
- **Non contiene stato runtime** — nessuna sessione, nessuna conversazione

### 2.2 Agent Catalog
Sistema di discovery, popolato via A2A dai runtime attivi.
- **Non contiene codice o bundle**, solo metadati
- Contiene: capability pubblicate, ownership, descrizione, SLA, tag, health,
  endpoint, versioni in esecuzione
- Sarà l'implementazione di `CatalogRegistrar`, oggi solo previsto lato
  Runtime (vedi handoff del repo Runtime, §6)

### 2.3 Policy Manager
Governance centralizzata: RBAC, sicurezza, compliance, cost control, privacy.
Il Runtime applica le policy che riceve da qui; l'enforcement locale a livello
di singolo workload resta nel Runtime (ancora aperto lì, vedi handoff §6).

### 2.4 Deployment Manager
Lifecycle dei deployment: deploy, rollback, rollout progressivo, canary
release, promotion tra ambienti (staging → produzione).

## 3. Architettura

```
                     +--------------------------------+
                     |       Gargantua Studio         |
                     +----------------+---------------+
                                      |
                               Desired State
                                      |
                     +----------------v---------------+
                     |   Gargantua Control Plane      |   <- questo repository
                     |--------------------------------|
                     | Registry | Catalog              |
                     | Policy Manager | Deploy Manager  |
                     +----------------+---------------+
                                      |
                          API / bundle distribution
                                      |
                     +----------------v---------------+
                     |        Gargantua Runtime        |
                     +----------------------------------+
```

Principio guida (sezione 11 del documento di visione): **Configuration State**
(agent definition, bundle, versioni) è gestito qui dal Registry. Il **Runtime
State** (sessioni, conversazioni, memoria, checkpoint) resta nel Runtime — il
Control Plane non lo tocca mai.

## 4. Riferimenti ad altri repository

| Repository | Relazione |
|---|---|
| [`gargantua`](../gargantua) | Runtime + metà esecutiva dell'AI Kernel. Consuma bundle e policy pubblicati da qui via API, non via accesso diretto allo storage. Vedi `docs/architecture/ai-operating-system.md` e `docs/architecture/runtime-decisions.md` (ADR-001..006) per il contratto completo. |
| [`gargantua-studio`](../gargantua-studio) | Produce lo stato desiderato (agent definition, capability, policy) che questo Control Plane riceve, versiona e distribuisce. |
| [`gargantua-gateway`](../gargantua-gateway) | Interroga il Catalog per il routing per-capability; non conosce staticamente gli agenti. |
| [`gargantua-operator`](../gargantua-operator) | Fase 5. Traduce lo stato del Control Plane in risorse Kubernetes (CRD) e ne applica la riconciliazione. |

## 5. Stack e struttura

- **Java 17 + Spring Boot 3.3.5** (web, validation, actuator, Jackson YAML).
- Self-contained: i tipi di dominio (`WorkloadKind`, `Capability`,
  `BundleDescriptor`) sono un mirror locale di quelli di `agent-core`, non una
  dipendenza — `agent-core` compila per Java 21 mentre qui restiamo su Java 17.
  Il contratto condiviso è il *domain model doc*, non un jar. Stessa scelta fatta
  per i tipi TypeScript di `gargantua-studio`.

```
ai.gargantua.controlplane
├── domain/       WorkloadKind, Capability, BundleDescriptor
├── registry/     Registry (port) + InMemory, RegistryService, ManifestReader
├── catalog/      Catalog (port) + InMemory, CatalogService
├── policy/       PolicyManager (port) + InMemory, PolicyService
├── deployment/   DeploymentManager (port) + InMemory, DeploymentService
├── storage/      BlobStore (port) + FilesystemBlobStore
└── common/       gestione errori uniforme (ApiError), Clock, RootController
```

Ogni sotto-sistema è dietro un **port** (ADR-004): l'MVP usa backend in-memory
(e filesystem per i byte del manifest), sostituibili senza cambiare i chiamanti.

## 6. Build ed esecuzione

```bash
mvn test            # 11 test: smoke di contesto, RegistryService, API end-to-end
mvn spring-boot:run # avvia su http://localhost:8080  (override: --server.port=NNNN)
```

`GET /` restituisce l'indice dei sotto-sistemi; `GET /actuator/health` la salute.

## 7. API (v1)

| Metodo & path | Cosa fa |
|---|---|
| `POST /api/v1/registry/bundles` | Pubblica un bundle dal manifest `gargantua.ai/v1` (YAML/JSON). Valida shape e invarianti, calcola il checksum SHA-256, indicizza le capability nel Catalog. Versioni immutabili → `409` se già presente. |
| `GET /api/v1/registry/bundles` | Elenca tutti i bundle pubblicati. |
| `GET /api/v1/registry/bundles/{name}/versions` | Versioni di un bundle. |
| `GET /api/v1/registry/bundles/{name}/{version}` | Un artefatto specifico. |
| `GET /api/v1/registry/bundles/{name}/{version}/manifest` | I byte del manifest originale. |
| `POST /api/v1/registry/bundles/{name}/{version}/promote` | Promuove una versione a un ambiente (`DEV`/`STAGING`/`PRODUCTION`). |
| `GET /api/v1/catalog/capabilities` | Capability pubblicate e chi le fornisce. |
| `GET /api/v1/catalog/capabilities/{name}` | Una capability e i suoi provider. |
| `POST /api/v1/catalog/agents` | Registrazione A2A di un agente attivo (endpoint + capability servite). |
| `GET /api/v1/catalog/agents[?capability=…]` | Agenti registrati, filtrabili per capability. |
| `POST /api/v1/policies` · `GET` · `GET /{id}` · `DELETE /{id}` | CRUD delle policy di governance (stub: definizione, non enforcement). |
| `POST /api/v1/deployments` | Registra un deployment desiderato (valida che il bundle esista → `404` se no); stato iniziale `PENDING`. |
| `GET /api/v1/deployments` · `GET /{id}` · `PUT /{id}/state` | Elenco, dettaglio e aggiornamento dello stato osservato. |

Gli errori usano un corpo uniforme `ApiError` (`status`, `error`, `message`,
`path`, `timestamp`).

## 8. Cosa resta (post-MVP)

Registry/Catalog sono ragionevolmente completi; Policy e Deployment sono stub
volutamente minimi. In ordine indicativo: persistenza durevole (Postgres/MinIO
dietro i port esistenti), firma digitale dei bundle, enforcement/valutazione
delle policy ai punti di applicazione, orchestrazione reale del rollout
(rolling/canary) nel Deployment Manager, health e SLA nel Catalog.
