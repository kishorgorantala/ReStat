# SRI Metadata and Model Reference

Source: User-provided **SRI - Document Of Understanding**, revision 1.0 dated 2026-09-01, plus the [SRI Oracle documentation](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/oracle.html).

> Table names and descriptions below are documentation references, not a verified live schema. Confirm exact object names, columns, synonyms, and environment before querying. Do not read or reveal credential-bearing configuration values.

## Request and response tracking

| Table | Request type / documented use | Diagnostic value |
|-------|-------------------------------|------------------|
| `D_SRI_REQUEST` | BOQL / SRI requests; select operations and request lifecycle records. | Correlate request ID, transaction ID (TID), status, error, timing, and SQL-related diagnostics where available. |
| `D_SRI_PRISMA_STORE_REQUEST` | GPB StoreRequests; incoming write requests and their status/error information. | Correlate request ID (RID) and relevant log timestamps/errors. |

The DOU contains the misspelling `D_SRI_RQUEST` once; the Oracle reference and QA query use `D_SRI_REQUEST`. Use the latter unless the live schema indicates otherwise. Do not copy sample transaction IDs, request IDs, or connection strings from the DOU into reports.

For today's failures, see the example queries in [11_sri_qa_runbook.md](11_sri_qa_runbook.md). The DOU's examples may need column/schema adjustments for the target environment.

## Snapshot and resource status

The DOU describes snapshot data as potentially spanning multiple messages, with opening and closing markers identifying the snapshot. It says `CCTL_RESOURCE_STATUS` holds opening/closing timestamps and the closing marker includes a record count that can be compared against received records. Confirm the exact table owner and column names in the target environment before querying.

## Metadata object inventory

The DOU groups metadata objects by role. Names are preserved as written in its table inventory; confirm spelling and current use before relying on them.

### Model, instance, and queue
| Object | DOU-described role |
|--------|--------------------|
| `D_SRI_MODEL_OBJ` | Model definitions, including model code/ID. |
| `D_SRI_MODEL_QUEUE_TOBEREM` | Legacy model queue configuration. |
| `F_SRI_MODEL_STATE` | Model state, versions, descriptions, and model ID. |
| `F_SRI_MODEL_AMQP_QUEUE` | AMQP queues assigned to model handling. |
| `F_SRI_MODEL_BIZ_OBJ_ASN` | Model-to-business-object assignment. |
| `F_SRI_INSTANCE_STATE` | Instance state and assigned model ID. |
| `SV_SRI_MODEL_INSTANCE` | View of model/instance associations. |
| `SV_SRI_LAST_IDS` | Last/current maximum model ID. |
| `V_REP_MODEL_BO_MR` | Reporting view of model versions and business objects. |
| `V_SRI_OVERVIEW` | Overview of models, instances, and connections. |

### Business objects, actions, and attributes
| Object | DOU-described role |
|--------|--------------------|
| `D_SRI_BUSINESS_OBJECT_OBJ` | Versioned business-object definitions and engine-type association. |
| `D_SRI_BIZ_OBJ_ACTION_TYPE` | Catalog of business-object action types. |
| `D_SRI_BUSINESS_OBJ_ATTR_TYPE` | Available business-object attribute data types. |
| `F_SRI_BUSINESS_OBJECT_STATE` | Business-object state and SELECT/view definitions. |
| `F_SRI_BUSINESS_OBJ_ATTR` | Attribute definitions, column mappings, and data types. |
| `F_SRI_BIZ_OBJ_ACTION` | Business-object action to SQL execution mapping. |
| `F_SRI_MODEL_BIZ_OBJ_ASN` | Assignment of business objects to models. |
| `SV_SRI_BIZ_OBJ_ACTION` | View of business-object actions and SQL details. |
| `SV_SRI_BUSINESS_OBJECT` | View of business-object definitions and SELECT statements. |

### SQL, mappers, and physical database objects
| Object | DOU-described role |
|--------|--------------------|
| `D_SRI_SQL_OBJ` | SQL object references associated with business objects. |
| `D_SRI_SQL_TYPE` | SQL statement type catalog. |
| `D_SRI_MAPPER_CLASS_OBJ` | Versioned mapper class definitions. |
| `D_SRI_MAPPER_CLASS_TYPE` | Mapper class type categories. |
| `F_SRI_SQL_STATE` | SQL statements and mapper-class references. |
| `F_SRI_MAPPER_CLASS_STATE` | Mapper implementation/class references. |
| `F_SRI_BIZ_OBJ_MAP_CLASS_ASN` | Mapper-to-business-object assignments. |
| `F_BUSINESS_OBJECT_TABLE_ASN` | Business-object to physical table mapping. |
| `F_SRI_DB_OBJECT` | Database object mapping to entities/engine types. |
| `SV_SRI_MAPPER_CLASS` | View of mapper classes and output formats. |
| `V_MODEL2BIZOBJSTATE` | View of business objects grouped by model. |
| `V_VALID_DB_OBJECT` | View of valid database objects and business-object codes. |
| `XV_SQL_STATE` | Extended view of SQL statements and action types. |
| `SV_FK_CONSTRAINT` | Foreign-key constraint definitions. |

### Engine, connection, and monitoring configuration metadata
The DOU also lists `D_SRI_AMQP_CONNECTION_OBJ`, `D_SRI_CONNECTION_OBJ`, `D_SRI_ENGINE_INSTANCE_OBJ`, `D_SRI_ENGINE_TYPE`, `D_SRI_INSTANCE_OBJ`, `D_SRI_OUTPUT_FORMAT`, `F_SRI_ENGINE_INSTANCE_ASN`, `F_SRI_ENGINE_INSTANCE_STATE`, `F_SRI_ENGINE_CONFIG`, `F_SRI_ENGINE_MONITOR_CONFIG`, `F_SRI_INSTANCE_AMQP_CON_ASN`, and `F_SRI_INSTANCE_HDFS_CONFIG` as configuration/catalog objects.

It lists `FV_SRI_CONNECTION`, `F_SRI_CONNECTION_STATE`, `F_SRI_SERVER_CONNECTION_STATE`, and `F_SRI_AMQP_BROKER_CONNECTION` as connection-related configuration objects. These may contain credential or authentication details. ReStat must not inspect or disclose their values; use approved secure operational procedures.

## Model version and runtime relationship

The DOU describes `D_SRI_MODEL_OBJ` as the model object/catalog entry and `F_SRI_MODEL_STATE` as holding registered model versions (including Main and SRI version fields) and model state. `F_SRI_INSTANCE_STATE` associates an engine instance with a model, and `F_SRI_MODEL_AMQP_QUEUE` contains model queue assignments.

This is useful when investigating model-version mismatch, missing queue assignments, or an engine starting with an unexpected model. Treat any corrective database change as an operator-owned deployment action, not a log-analysis recommendation.

## Engine-to-queue lookup chain

The user-provided **SRI Engines_instance_introduction.pptx** documents this diagnostic mapping:
1. Engine to server/user: `../cfg/possible_engines.cfg`.
2. Engine ID to instance ID: `F_SRI_ENGINE_INSTANCE_ASN`.
3. Instance ID to instance name and assigned model: `F_SRI_INSTANCE_STATE`.
4. Model to AMQP queues: `F_SRI_MODEL_AMQP_QUEUE`.

Use this as a lookup guide when correlating an engine log with its instance, model, and queues. Verify the actual configuration path and object definitions for the deployed version; do not expose connection credentials from config or metadata.
