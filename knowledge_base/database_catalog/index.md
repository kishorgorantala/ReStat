# Database Catalog Index

Catalog files contain metadata facts only. They contain no query instructions or business-row data. They are exports received on 2026-10-06; the actual extraction timestamp is not embedded in every file. Last DDL dates describe object DDL, not export freshness.

## ACC export labelled `ACC`

Reported owner in `ACC_Data.csv`: `STX_DEV`. The user confirmed this export corresponds to the Acceptance application schema `DTBPRISPRIMA`. `CIFACC` is a separate CIF alias/schema; this export is not a CIF catalog.

- `ACC_STX_DEV_objects.csv`: 228 objects; all are tables and all reported VALID; object DDL dates 2024-02-06 through 2026-08-17.
- `ACC_STX_DEV_columns.csv`: 6,811 column records across the same 228 table names.
- `ACC_STX_DEV_constraints.csv`: 16 constraint records, all primary-key constraints, across 6 tables. This is not a complete index/constraint inventory.
- The export contains no views or view definitions.

## DEV export labelled `Dev`

Reported owner: `SRI_TST`, consistent with the DEV metadata schema previously supplied.

- `DEV_SRI_TST_objects.csv`: 76 objects: 49 tables and 27 views. 74 VALID; 2 INVALID (`V_REP_DBID_CONN`, `V_VALIDATE_BO_METADATA`). Object DDL dates 2018-07-06 through 2024-04-17.
- `DEV_SRI_TST_columns.csv`: 683 column records across all 76 listed objects, including view columns.
- `DEV_SRI_TST_constraints.csv`: 110 records: 63 primary-key, 28 foreign-key, and 19 unique constraints.
- `DEV_SRI_TST_comments.csv`: 3 table comments, for `D_SRI_SERVER_CONNECTION_OBJ`, `F_SRI_DB_OBJECT`, and `F_SRI_SERVER_CONNECTION_STATE`.
- The object export names the views but does not include view SQL definitions.

### DEV diagnostic table columns present in this export

- `SRI_TST.D_SRI_REQUEST`: `SRI_REQUEST_ID`, `TYPE`, `STATUS_CODE`, `STATUS_NAME`, `ERROR`, `REQUEST_RECEIVED`, `PREPROCESSING_DURATION`, `EXECUTION_DURATION`, `FETCH_DURATION`, `REQUEST_DURATION`, `TID`, `RID`, `SOURCE_NAME`, `OUTPUT_FORMAT`, `TARGET_TYPE`, `TARGET_TRANSFER`, `QUERY_TIMEOUT_VALUE`, `FETCH_TIMEOUT_VALUE`, `CUSTOM`, `SCOPE`, `SQL`, `ITS`, `UTS`, `FETCH_LIMIT_VALUE`.
- `SRI_TST.D_SRI_PRISMA_STORE_REQUEST`: `SRI_PRISMA_STORE_REQUEST_ID`, `BUSINESS_DATE`, `VERSION`, `SOURCE`, `TYPE`, `STATUS_CODE`, `STATUS_NAME`, `ERROR`, `REQUEST_RECEIVED`, `DESERIALIZATION_DURATION`, `STORE_DURATION`, `TA_COMPLETION_DURATION`, `RESULT_CREATION_DURATION`, `RID`, `ENTITY_COUNT`, `ITS`, `UTS`, `ENTITY_NAME`, `INSTANCE`.
- `SRI_TST.F_SRI_MODEL_STATE`: `SRI_MODEL_ID`, `VALID_FROM`, `VALID_TO`, `MODEL_NAME`, `DESCR`, `MODIFYING_USER`, `ITS`, `UTS`, `VERSION_MAIN`, `VERSION_SRI`.
- `SRI_TST.F_SRI_INSTANCE_STATE`: `SRI_INSTANCE_ID`, `VALID_FROM`, `VALID_TO`, `SRI_MODEL_ID`, `SRI_CONNECTION_ID`, `INSTANCE_NAME`, `DESCR`, `MODIFYING_USER`, `ITS`, `UTS`, `INSTANCE_TYPE`, `USE_HDFS`, `HDFS_ENTITY_NO`.
- `SRI_TST.F_SRI_ENGINE_INSTANCE_ASN`: `SRI_ENGINE_INSTANCE_ID`, `SRI_INSTANCE_ID`, `VALID_FROM`, `VALID_TO`, `DESCR`, `MODIFYING_USER`, `ITS`, `UTS`.
- `SRI_TST.F_SRI_MODEL_AMQP_QUEUE`: `SRI_MODEL_ID`, `VALID_FROM`, `VALID_TO`, `AMQP_CONNECTION_NAME`, `QUEUE_NUMBER`, `INITIAL_CONTEXT_FACTORY`, `CONNECTION_STRING`, `DESTINATION_NAME`, `ITS`, `UTS`, `QUEUE_DIRECTION`.
- `SRI_TST.F_SRI_BUSINESS_OBJECT_STATE`: `SRI_BUSINESS_OBJECT_ID`, `VALID_FROM`, `VALID_TO`, `BUSINESS_OBJECT_NAME`, `SELECT_CONTAINS_WHERE_IND`, `SELECT_STATEMENT`, `DESCR`, `MODIFYING_USER`, `ITS`, `UTS`, `BUSINESS_OBJECT_TYPE`.
- `SRI_TST.F_SRI_SQL_STATE`: `SRI_SQL_ID`, `VALID_FROM`, `VALID_TO`, `SRI_SQL_TYPE_ID`, `SQL`, `DESCR`, `MODIFYING_USER`, `ITS`, `UTS`, `SRI_MAPPER_CLASS_ID`.

`CONNECTION_STRING` is a credential-bearing/configuration value. Its column name is cataloged, but never export or request its row contents. Likewise, use `SQL`/`SELECT_STATEMENT` definitions only when directly relevant and review them before sharing.

This DEV catalog is an older structural snapshot by DDL dates. Verify current deployed objects if the RCA depends on a recent model or metadata change.

## SIT2 export labelled `SIT2`

Reported owner: `EUREX_PRISMA_SIT2_SRI`. This export contains synonyms only, not base tables, columns, or view definitions.

- `SIT2_EUREX_PRISMA_SIT2_SRI_synonyms.csv`: 1,178 synonyms; 215 VALID and 963 INVALID in the export. DDL dates range 2019-03-14 through 2026-09-14.
- The export does not include synonym target owner/name. It cannot alone establish which base table/view a synonym resolves to. Ask for the synonym target mapping and the target schema's object/column catalog when needed.

## Usage rules for ReStat

- Select a catalog by both environment and reported owner/schema; do not treat an environment label as the schema name.
- Use raw catalog CSVs to confirm exact object names and columns before proposing an investigation query.
- Use the ACC `STX_DEV` catalog for Acceptance schema `DTBPRISPRIMA`. Do not apply it to CIF data; ask for a separate `CIFACC` catalog when the issue is CIF-specific.
- The SIT2 export contains synonyms only; ask for target-owner/name mapping and target object metadata before recommending a query through a synonym.
- If environment/owner mapping is ambiguous, the catalog is stale, an object is missing, or a synonym is invalid/unresolved, ask the user for the specific current metadata needed.
- Never infer object validity or base-object availability solely from a synonym's name.
