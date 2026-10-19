# STX2CMS Message Sender and STX2BDWH

Sources:
- [STX 2 CMS - Message Sender](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/stx2cms.html)
- [STX 2 BDWH](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/stx2bdwh.html)

## STX2CMS overview

STX2CMS is an AMQP messaging client that sends and receives messages according to Protocol Buffer definitions. Its sender produces messages compliant with the receiver-side Protofile. The source gives the [C7 Collateral Security Data Protofile](https://github.deutsche-boerse.de/stx/gts.cilmsg.c7.collateral/blob/master/proto/src/main/proto/CIL/CIL_v001/C7_Collateral_v001/Messages/GO_110020_CollateralSecurityData.proto) as an example.

The documented application lifecycle includes:
- **Message Sender:** Java fat jar (`STX2CMS`), including required libraries; serializes data read from Oracle and sends an AMQP message.
- **Message Receiver:** The source calls this an “Ajva Application” that receives messages and stores their contents in a log file. It is used to test sender output and queue consumption. Verify the intended runtime/technology; “Ajva” may be a source typo.
- **Control-M workflow:** Schedules sender execution when the data-availability condition is met.
- **Oracle metadata:** Defines internal dependencies and the SQL query/view used to prepare data.

## STX2CMS sender flow

1. Informatica ETL workflows prepare the source data in Oracle.
2. Once data are ready, the documented Control-M resource is freed:
   `EUREX_TP / L_1381_56_COLL_SECURITY_CMS_OUT / EXTP_56_EXTP_MD_COLL_SECURITY`
3. The Control-M component `EUREX_TP -> L_1381_56_COLL_SECURITY_CMS_OUT -> STX_TO_CMS_DATA_SENDER` executes Java application `cms_sender`.
4. The sender reads its data definition from engine metadata. The source identifies `sri_prd.F_SRI_BUSINESS_OBJECT_STATE`, filtered through `D_SRI_BUSINESS_OBJECT_OBJ` for version `C7_Collateral_v001_002_002`.
5. The metadata query/view prepares the data, and the sender serializes and publishes an AMQP message.

The source links an example view definition: [11__SRI_metadata_view_001_002_002_1220_EODCollateralSecurity.sql](https://github.deutsche-boerse.de/stx/STX.SRI/blob/master/Deployment/STX2CMS/02-ORACLE/VIEWS/11__SRI_metadata_view_001_002_002_1220_EODCollateralSecurity.sql).

### Numeric conversion note

Source data includes floating-point values, while the documented Protobuf AMQP representation treats the values as pairs of integers (value and decimal shift). The source says this transformation is performed by database view `EUREX_TP.V_F_EX_TP_COLL_SECURITY`, with no additional transformation in the Java engine code. For numeric serialization issues, inspect the source view and Protofile field definition together.

### Execution behavior

- The sender is restartable and reads the data available in Oracle at the time of that execution.
- A successful execution sends exactly one AMQP message and then exits.
- The optional receiver can run continuously.

**Analysis implication:** Because a restart reads the then-current data and emits a message again, investigate whether a retry could publish duplicate or changed data. Confirm idempotency, message identifiers, and receiver behavior from the actual implementation; the source does not specify these guarantees.

## STX2BDWH overview

- STX2BDWH uses the same engine code as the regular SRI engine.
- Data is processed one by one and acknowledged at the end with an **End of Stream** message.
- The receiver runs continuously (24/7); the source says it can be restarted at any time.
- Git repository: [STX.SRI](https://github.deutsche-boerse.de/stx/STX.SRI).
- Data-preparation view definitions: [Deployment/STX2BDWH/02-ORACLE/VIEWS](https://github.deutsche-boerse.de/stx/STX.SRI/tree/master/Deployment/STX2BDWH/02-ORACLE/VIEWS).

## Log analysis clues

These are investigation directions, not confirmed root causes:
- **CMS sender did not start:** check ETL completion, the Control-M data-availability resource, and the sender job chain.
- **Sender ran but no message arrived:** verify the metadata-selected view/query, Oracle data available at execution time, AMQP publish outcome, and receiver queue/log.
- **Numeric values differ in CMS:** compare source values, `EUREX_TP.V_F_EX_TP_COLL_SECURITY`, and the relevant Protofile representation (integer value plus decimal shift).
- **Repeated CMS messages after restart:** correlate Control-M retries and sender executions with broker message identifiers and receiver-side handling; do not assume exactly-once delivery from the guide.
- **BDWH stream incomplete:** check per-record processing, receiver logs, and whether the End of Stream acknowledgement was emitted/received.
