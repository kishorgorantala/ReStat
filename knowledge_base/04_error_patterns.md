# SRI Error Patterns

| Pattern / Signature | Component | Meaning | Typical Action |
|---------------------|-----------|---------|----------------|
| `ORA-00942` with `ORA-02063` (remote database-link error) | BOQL / Oracle remote query | A referenced table or view may be missing, inaccessible to the remote account, or resolved through an unexpected schema/link. | Verify the object name and owner, grants, synonym, and database-link target with the DBA; inspect the generated SQL and business-object metadata. Do not infer which cause applies from the error alone. |
| `ORA-12801` with `ORA-01502` (index or index partition unusable) | Oracle query/processing | Oracle's parallel query wrapper reports an unusable index or index partition. | Capture the affected index and partition from the full error; ask the DBA to check index/partition status and recent DDL/load activity. Do not attempt rebuilds or DDL without approval. |
| `ORA-01017` with `ORA-02063` (remote database-link error) | Oracle connection / remote DB link | Authentication to the remote database was rejected. | Ask the DBA to verify the configured wallet/credential mapping, account status, and remote-link configuration through approved secret-management procedures. Never request, log, or share the password. |

Source: user-provided `Presentation.pptx`, which contains historical log examples dated 2025. These entries preserve the reusable Oracle error signatures, not the original request IDs, transaction IDs, or stack traces. Treat each interpretation as an investigation lead, not a confirmed root cause.

## Context-dependent status-column diagnostic

Source: user-provided ACC `PRISMA_ACC.log` thread excerpt dated 2026-10-06, received 2026-10-07.

Observed sequence for a `ResourceStatus` BOQL SELECT with GPB output:
- `columnName: STATUS`, `columnExists: false`, and `Tools.columnExistsAndIsNotNull: false`.
- `this error can be ignored because columnName is not in query. Issue when processing status :ORA-17006: Invalid column name`.
- Processing continues with streamed replies and `Request query response has 1 RECORDS and 16 BYTES`.

In this excerpt, the application explicitly treats the absent result-column diagnostic as ignorable and continues replying. Do not classify this isolated ERROR-level line as a failed request. Confirm response status and client outcome before concluding success; the excerpt omits the XML bodies and client receipt. Conversely, do not ignore all `ORA-17006` errors: an unexpected missing required column or an aborted mapper/request requires investigation.
