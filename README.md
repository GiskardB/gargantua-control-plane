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

- **Java 25 (LTS) + Spring Boot 4.1** (web, validation, actuator, Jackson YAML).
  Ultima LTS per sfruttare al meglio GC (Generational ZGC) e gestione memoria —
  performance e memoria sono un requisito di piattaforma, non un dettaglio.
- **Modello di dominio condiviso**: i tipi del vocabolario (`WorkloadKind`,
  `Capability`, …) vengono da `io.github.giskardb:agent-core`, il modulo di dominio
  puro (Spring-free) pubblicato dal Runtime. Il Control Plane parla gli **stessi tipi**
  dei componenti che serve invece di rispecchiarli localmente — così Registry e Runtime
  nominano le stesse cose allo stesso modo. Ciò che resta locale è solo la *proiezione*
  del Control Plane: `PublishedBundle` (kind + capability + checksum indicizzati dal
  Registry), distinta dal `BundleDescriptor` di dominio (provenienza: firma, immagine).

```
ai.gargantua.controlplane                (dominio condiviso: ai.gargantua.core.* da agent-core)
├── registry/     Registry (port) + Jdbc/InMemory, RegistryService, ManifestReader,
│                 model/PublishedBundle (proiezione registry), BundleArtifact
├── catalog/      Catalog (port) + Jdbc/InMemory, CatalogService
├── policy/       PolicyManager (port) + Jdbc/InMemory, PolicyService (+ evaluate)
├── deployment/   DeploymentManager (port) + Jdbc/InMemory, DeploymentService
├── storage/      BlobStore (Filesystem | S3) + DocumentStore (JdbcDocumentStore)
└── common/       gestione errori uniforme (ApiError), Clock, ObjectMapper, RootController
```

Ogni sotto-sistema è dietro un **port** (ADR-004). La persistenza è **durevole**:
lo stato (registry, catalog, policy, deployment) vive in un piccolo *document store*
JDBC (aggregati serializzati in JSON, tabella `cp_document`), i byte dei manifest nel
`BlobStore`. I backend si scelgono per profilo senza toccare i chiamanti:

| Cosa | Default (zero-infra) | Profilo `postgres` / `minio` |
|---|---|---|
| Stato (document store) | **H2 embedded** su file (`./data/db`) | **Postgres** (stesso schema) |
| Manifest (blob) | **Filesystem** (`./data/blobs`) | **MinIO / S3** |
| `InMemory*` | — (test double per gli unit test) | — |

## 6. Build ed esecuzione

```bash
mvn test            # 18 test: context, RegistryService, PolicyService, API end-to-end (H2)
mvn spring-boot:run # avvia su http://localhost:8080 (durevole su ./data), override --server.port=NNNN

# Backend di Cave, attivabili senza modifiche al codice:
mvn spring-boot:run -Dspring-boot.run.profiles=postgres        # stato → Postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,minio  # stato → Postgres, blob → MinIO
```

`GET /` restituisce l'indice dei sotto-sistemi; `GET /actuator/health` la salute.
La persistenza è verificata end-to-end: pubblicando dati, riavviando il processo e
ritrovandoli (bundle, promozioni, capability, policy) sopravvivono al restart.

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
| `POST /api/v1/policies` · `GET` · `GET /{id}` · `PUT /{id}` · `DELETE /{id}` | CRUD delle policy di governance. |
| `POST /api/v1/policies/evaluate` | Decide un target contro le policy: `{target}` → `{decision, matched}`. Match esatto o per prefisso (`target:*`); **deny overrides**; nessun match = ALLOW. Definizione, non enforcement (ADR-004). |
| `POST /api/v1/deployments` | Registra un deployment desiderato (valida che il bundle esista → `404` se no); stato iniziale `PENDING`. |
| `GET /api/v1/deployments[?environment=…&bundle=…]` · `GET /{id}` · `PUT /{id}/state` | Elenco (filtrabile per ambiente/bundle), dettaglio e aggiornamento dello stato osservato. |

Gli errori usano un corpo uniforme `ApiError` (`status`, `error`, `message`,
`path`, `timestamp`).

## 8. Cosa resta (post-MVP)

Registry, Catalog, Policy e Deployment sono funzionali e persistenti. In ordine
indicativo restano: verifica dei profili `postgres`/`minio` contro i servizi reali
di Cave (oggi spenti), firma digitale dei bundle, enforcement delle policy ai punti
di applicazione (Runtime/Gateway), orchestrazione reale del rollout (rolling/canary)
nel Deployment Manager, health e SLA nel Catalog.
