# SRI Oracle Metadata, Connectivity, and Data Pump

Source: [General - StatistiX Knowledge Base 4.3.2: Oracle](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/oracle.html)

> **Operational caution:** These examples are references, not authorization to connect, export, import, or modify a database. Confirm the target environment, schema, data classification, change approval, and current procedures with the database/Operations team. Never put passwords, tokens, or other secrets on a command line or in this knowledge base. Use the organization's approved credential mechanism.

## First: identify the database the failing query actually runs on

Before running `EXPLAIN PLAN`, AWR (`DBA_HIST_*`), statistics, or index checks for a slow/timeout query, confirm **which database and schema the SRI engine actually executes the query against** — do not assume it is the database you (or the reporter) happen to be logged into. Application/metadata accounts are often on a different DB or PDB than where the query runs, which makes diagnostics return nothing or the wrong plan.

Get the execution target from the **engine log's JDBC connection string**, which the engine logs per request, e.g.:

```
wallet connection requested: ... user: EUREX_PRISMA, connectionString: jdbc:oracle:thin:/@EUREX_PRISMA_PRISMA_PRD
JDBCConnection getClientInfo: {OCSID.MODULE=PRISMA SRI Engine, OCSID.CLIENTID=PRISMA_PROD}
```

That gives the **owning schema/user** (here `EUREX_PRISMA`) and the **service/alias** (here `EUREX_PRISMA_PRISMA_PRD` → database `PRISMA_PRD`). Then, once connected, verify you are in the right place before diagnosing:

```sql
-- confirm DB/service and that the owning schema is local
SELECT SYS_CONTEXT('USERENV','DB_NAME') db, SYS_CONTEXT('USERENV','SERVICE_NAME') svc,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') cur_schema FROM dual;
SELECT username FROM all_users WHERE username = '<OWNER_FROM_LOG>';   -- must return a row

-- confirm the query really executed here (also yields the SQL_ID)
SELECT sql_id, plan_hash_value, parsing_schema_name
FROM   gv$sql
WHERE  sql_text LIKE '%<distinctive literal from the logged SQL>%'
AND    parsing_schema_name = '<OWNER_FROM_LOG>';
```

If the owning schema is not a local user, or the SQL is absent from `gv$sql`/AWR and only your own session's queries match, you are on the wrong DB/PDB — reconnect to the service the engine uses (the alias from the log) before continuing. Keep all of this read-only; never run the heavy query itself to "test."

## Important SRI metadata tables

SRI metadata is stored at database level. The source guide says to connect to the relevant environment using its `SRI_XXX` schema/user; confirm the correct environment mapping in `03_environments.md` before querying.

| Table | Purpose described by the source |
|-------|---------------------------------|
| `D_SRI_MODEL_OBJ` | Lists installed models. |
| `F_SRI_MODEL_STATE` | Metadata used to start SRI engines. |
| `D_SRI_PRISMA_STORE_REQUEST` | Log of StoreRequests (insert/update). |
| `D_SRI_REQUEST` | Log of Requests (select). |
| `F_SRI_BUSINESS_OBJECT_STATE` | Definitions of views. |
| `F_SRI_SQL_STATE` | Definitions of inserts and updates. |

These table descriptions are source-guide summaries. Verify columns, retention, and current usage against the live schema or current database documentation before relying on them for diagnosis.

The DOU provides a broader metadata-object inventory, grouped by model/instance, business object/action, SQL/mapper, and engine configuration. See [13_sri_metadata_model_reference.md](13_sri_metadata_model_reference.md); that inventory is also documentation-derived and requires live-schema verification.

## Test database connectivity

The source documents this PowerShell check for testing reachability from IntelliJ's machine to the database endpoint:

```powershell
Test-NetConnection -ComputerName dwhdx4-scan.deutsche-boerse.de -Port 1522
```

A successful TCP connection confirms network reachability to that host and port only; it does not confirm Oracle listener health, authentication, schema permissions, or IntelliJ configuration.

## Data Pump: ACC to DEV example

The source guide describes using `dwhdas11` or `dwhdas12` because `/fls/` is mounted on both. The Data Pump directory alias is `PRISMA_IMPORT`, mapped in the source to `/fls/EXADATA_IMPORT`. Verify the directory object, filesystem path, permissions, and dump-file availability with the database/Operations team before use.

The source example exports `EUREX_PRISMA.D_STA_CALENDAR` from `DWHDTBPRISPRIMA.WORLD`, then imports it via `DWHPRISDEVPRIMA.WORLD`, remapping schema `EUREX_PRISMA` to `PRISMA_DEV_149` and appending if the target table exists.

The source used inline password syntax; it is intentionally omitted here. Use the approved credential mechanism and replace the placeholders only in an approved execution environment:

```text
expdp <approved-authentication>@DWHDTBPRISPRIMA.WORLD \
  tables=EUREX_PRISMA.D_STA_CALENDAR \
  directory=PRISMA_IMPORT \
  dumpfile=<approved_dump_name>.dmp \
  logfile=<approved_export_log>.log

impdp <approved-authentication>@DWHPRISDEVPRIMA.WORLD \
  tables=EUREX_PRISMA.D_STA_CALENDAR \
  directory=PRISMA_IMPORT \
  dumpfile=<approved_dump_name>.dmp \
  logfile=<approved_import_log>.log \
  REMAP_SCHEMA=EUREX_PRISMA:PRISMA_DEV_149 \
  TABLE_EXISTS_ACTION=APPEND
```

Before running an import, verify the target schema and table, whether append behavior is intended (including duplicate/data-integrity impact), data classification and masking requirements, dump provenance, and rollback/recovery plan. Do not treat this illustrative single-table example as a general-purpose production procedure.
