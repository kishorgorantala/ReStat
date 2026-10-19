# SRI Overview

Source: [SRI principles](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/sri_principles.html)

## What is SRI
SRI stands for **StatistiX Realtime Interface**. It runs as a service that:
- connects to a message broker,
- receives available messages and stores them into the database,
- responds to **BOQL requests** (StatistiX own business logic to query the database) and sends the select result back to AMQP.

Currently the **AMQP** message broker is supported. **Kafka** support is possible with some extension.

## Business purpose and communication patterns
SRI is middleware between PRISMA engines and the StatistiX Data Warehouse. It supports both one-way data delivery and request/response communication:
- **Core Engine (CE):** typically sends data to the warehouse through SRI.
- **Analysis Engine (AE):** uses request/response communication with SRI through AMQP.

High-level flow: **PRISMA Engine -> AMQP broker -> SRI -> Oracle / warehouse**, with responses routed back to the originating engine when requested. The DOU describes SRI as supporting risk, margin, market, and portfolio data; it also names Oracle and optional HDFS storage in the processing path.

AMQP producers publish to exchanges; exchanges route messages to queues using bindings. Consumers receive or fetch queue messages. SRI's documented broker connection and queue setup is model/configuration-driven.

## Snapshot concepts
Snapshot data may span multiple messages. Opening and closing markers identify the snapshot; the closing marker may include a record count for comparison with received records. The DOU identifies `CCTL_RESOURCE_STATUS` as holding opening/closing timestamps. Confirm exact table naming and fields in the target database before querying.

## Engine types and environments
SRI/PRISMA engine modes are **Main, Live, Snap, and What-If**. The DOU describes multiple similar instances in DEV and distinct engine roles in higher environments. Treat the exact topology as environment-specific; consult [03_environments.md](03_environments.md) and current deployment configuration.

## How it works
- SRI is fully configurable. Each configuration corresponds to a **Model**.
- Model name must be **unique** among the Models stored in the same database.
- A Model consists of **source code (jar files)** and **SQL data**. The SQL data are called **Metadata**.
- Data that SRI receives from the broker are called **Data**.
- Each Model is fully described by a **Protofile** together with **local configuration**.
  - Protofile is received from the counter party (e.g. Prisma, OTC, ...).
  - Local configuration contains parameters relevant only to the StatistiX side.

Protofile example: repository [STX.SRI](https://github.deutsche-boerse.de/stx/STX.SRI), any branch, path:
`Codebase/06-JAVA/prisma_vXXX_XXX_XXX/proto/prisma2stx_business_XXX_XXX_XXX.proto`

## Glossary
| Term | Meaning |
|------|---------|
| SRI | StatistiX Realtime Interface |
| Model | One SRI configuration (jars + Metadata), unique name per DB |
| Metadata | SQL data belonging to a Model |
| Data | Business messages received from the broker |
| Protofile | Message definition delivered by counter party |
| BOQL | StatistiX business query language for DB selects via broker |
| StoreRequest | Message type that inserts/updates data |
| BOQL Request | Message type that selects data and replies to AMQP |
| GPB | Google Protocol Buffers; DOU associates it with incoming message payloads and StoreRequest handling |
| CE / AE | Core Engine / Analysis Engine |
| Main / Live / Snap / What-If | SRI/PRISMA engine processing modes |
