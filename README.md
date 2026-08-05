# Gargantua Control Plane

Control Plane della piattaforma **Gargantua AI Operating System**. Gestisce lo
stato *desiderato* della piattaforma — cosa deve esistere, chi può fare cosa,
cosa è disponibile — e lo espone a tutti gli altri componenti.

> Repository non ancora avviato al livello di implementazione. Questo documento
> ne fissa lo scope prima di scrivere la prima riga di codice.

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

## 5. Stato

Non avviato. Da fare, in ordine indicativo: modello dati Registry/Catalog,
API di pubblicazione bundle, API di discovery A2A lato Catalog, RBAC nel
Policy Manager, orchestrazione rollout nel Deployment Manager.
