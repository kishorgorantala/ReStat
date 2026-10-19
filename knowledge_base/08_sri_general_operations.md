# SRI General Information and Operations Tips

Source: [SRI - General information and tips](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/generalSRI.html)

> **Operational caution:** These notes are a reference, not authorization to run commands or SQL. Confirm the target environment, engine, change window, and operator approval first. Never read, copy, or disclose passwords or other secrets from configuration files. The source uses “Life” in one place; interpreted here as “Live”.

## Workflow and persistence
- SRI does not perform CRUD operations directly in application code. The corresponding Inserts, Updates, and Selects are defined in database-level Metadata.
- High-level flow: **PRISMA → PRISMA AMQP broker → SRI → Oracle and HDFS**.
- An engine connects to the broker first and then to its queue.

## Log and script locations
- Engine logs are on the relevant `dwh*` server under `/infa_files/Scripts/SRI/logs`.
- DEV server noted by the source: `dwhdas56`; aliases `logs` and `scripts` are available there.
- Start/stop/restart scripts are under `/infa_files/Scripts/SRI/scripts`.
- DEV SRI Prisma script path also referenced in the development guide: `/home/infa64/infa_files/Scripts/SRI.PRISMA`.
- Environment-to-server mapping should be verified in `03_environments.md` and with the current environment overview.

## Starting, stopping, and restarting engines
- Avoid using `ALL` to start all DEV engines at once. The source recommends starting engines one at a time while monitoring server `%CPU` (for example, with `top`) in a separate session.
- Source command patterns, to be used only after confirming the target and approval:
  - `./restart.sh restart PRISMA_DEV_*`
  - `./stop.sh PRISMA_DEV_*`
  - `./start.sh PRISMA_DEV_*`
- Restarting may free memory, but monitor resource consumption and service health after each operation.
- For PROD/SIMU Main and Whatif restarts, the source says OPS should schedule the restart between TTSave cycles. Check current resource status and coordinate with OPS; do not infer a safe window from stale data.
- Source query for checking TTSave resource status:
  ```sql
  select * from c_ctl_resource_status
  where pk_resource_name like 'TTSAVE%'
  order by 2 desc;
  ```

## Engine roles and message processing
| Component / engine | Behavior noted in source |
|--------------------|---------------------------|
| CoreEngine / DataStorage | CoreEngine sends StoreRequests processed by Snap and Live. For Snap/Live data, inserts occur after data is received and processed, then the message is acknowledged. |
| AnalysisEngine | Handles StoreRequest and Request for Main and Whatif. Source says Main starts a new thread after receipt, acknowledges the data, then passes it to Oracle for processing. |
| Snap | Source lists 200 data connections and 200 Metadata connections. |
| Main and Live | PROD TTSave processing is described as handled by Main and Live. |
| Whatif | `EUREX_PRISMA_SRI` is identified as its queue. |

- **GPB (Google Protocol Buffer)** supports insert, update, delete, and select operations.
- **BOQL** is described as business logic for select operations only.
- TTSave: PRISMA data is processed and placed into a staging table by SRI; Resource Status messages are also processed.
- IND files are used by Informatica to check whether data is available.

## Queue startup and metadata diagnostics
- At startup, SRI iterates through the AMQP queue list serially. The source warns that a single incorrect queue among a large list (example: 300) can prevent recovery/startup. When investigating startup failures, check each configured queue and the first failing connection in logs.
- `F_SQL_STATE` records insert statements used. Source mapping: `8` = SNAP/LIVE, `4` = Main update, `2` = Main insert. Verify this mapping against current metadata and code before acting on it.
- To test modified insert statements without deleting messages from AMQP, the source suggests disabling the acknowledgement flag in `engine.cfg`. This changes message-processing behavior and can cause queue buildup or redelivery; only do this under an approved test plan with an explicit restore step.
- Source query for last-modified table metadata:
  ```sql
  select * from dba_tab_modifications
  where TABLE_NAME='F_SRI_INSTANCE_HDFS_CONFIG';
  ```

## Environment and resource troubleshooting clues
These are investigation leads, not confirmed diagnoses:
- **DEV overload:** source suggests assigning the DB to engine 13 or setting validity to `sysdate`. Verify engine load, routing semantics, and validity requirements with the SRI/DB owners before changing assignments or data.
- **ACC test data:** source points to `dwhfs10/fls` and `/fls/EXADATA_IMPORT/PRISMA_EXP_IMP` for development dumps. Confirm data currency and access controls before using dumps.
- **Java process memory:** source suggests inspecting `/proc/<pid>/status`; `VmHWM` is the high-water mark to note.
- **Free memory:** source commands are `cat /proc/meminfo` and `free -h`.
- **Thread counts / engine memory:** source points to `./getThreadCounter.sh` in the scripts directory.
- **Java versions:** `/STXDATA/CFG/infa/Libraries/SRI` is noted as a location to check recent jar versions. On DEV, the Main Prisma jar path is noted as `/home/sri1/infa_files/Libraries/SRI.PRISMA/prisma_main/`.
- **DEV engine configuration:** source notes `/home/infa64/infa_files/Scripts/SRI.PRISMA.CFG/engine_versions.cfg`.
- **Sensitive configuration:** the source identifies Metadata configuration directories for NonDEV, ACC, and DEV as locations where credentials may be configured. Treat these directories as secret-bearing; do not inspect or output credentials. Use approved credential-management procedures.

## Queue associations
- DCTools uses the **HEDGEHOG** queue.
- `EUREX_PRISMA_SRI` is used by the Whatif engine.

## Control-M
- Source reference for PROD engine restarts: `EUREX_PRISMA_MAINT/00_ENGINE_RESTART/EUREX_PRISMA_MAINT/PRISMA_ENGINE_RESTART_*`.
- Coordinate Control-M changes and restarts with Operations and follow current scheduling/approval procedures.
