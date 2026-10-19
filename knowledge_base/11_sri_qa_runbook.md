# SRI QA Runbook: Engine Restart and Metadata Queries

Source: [SRI QA](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/sri_qa.html)

> **Operational caution:** Commands below are transcribed as references, not authorization to execute them. Verify the environment, server, engine, maintenance window, and current runbook with Operations first. Creating an indicator file or restarting an engine changes runtime behavior.

## Manually restart an engine

The source guide's example restarts `PRISMA_MAIN_ENGINE` from the SRI scripts directory:

```sh
cd /infa_files/Scripts/SRI/scripts/
restart.sh restart PRISMA_MAIN_ENGINE
```

Confirm the correct engine name and environment before restarting. Follow the relevant monitoring and staged-restart guidance in [08_sri_general_operations.md](08_sri_general_operations.md).

## Create an indicator file

The source guide gives this example for `EUREX_PRISMA_STRESSTESTING_0.IND`:

```sh
cd /infa_files/Libraries/SRI/srcFilesDir
touch EUREX_PRISMA_STRESSTESTING_0.IND
```

This creates a file and may signal data availability to downstream processing. Do not create it unless the responsible workflow owner confirms it is required, the target directory/environment is correct, and duplicate/reprocessing implications are understood.

## Metadata queries for today's failures

The source labels these as useful queries in the `sri_dev` Metadata schema. Confirm the environment and schema before running them.

### Non-OK requests

```sql
select *
from D_SRI_REQUEST
where its > trunc(sysdate)
  and status_code <> 'OK';
```

This checks `D_SRI_REQUEST` records from today with a non-OK status. This table is described in [09_sri_oracle_metadata_datapump.md](09_sri_oracle_metadata_datapump.md) as the log of Requests (select); it is not specifically the StoreRequest table.

### StoreRequests with errors

```sql
select *
from D_SRI_PRISMA_STORE_REQUEST
where request_received > trunc(sysdate)
  and error is not null;
```

This checks today's StoreRequest records with a populated error field.

## Find metadata views that still need definitions

The source says these views are new in the model and need manual definition. The example filters version `008_110_002` and `SELECT_STATEMENT` containing `to be defined`:

```sql
select
    x.SRI_BUSINESS_OBJECT_ID,
    x.VALID_FROM,
    x.VALID_TO,
    x.BUSINESS_OBJECT_NAME,
    x.SELECT_CONTAINS_WHERE_IND,
    x.SELECT_STATEMENT,
    bos.BUSINESS_OBJECT_CODE,
    bos.VERSION
from F_SRI_BUSINESS_OBJECT_STATE x,
     D_SRI_BUSINESS_OBJECT_OBJ bos
where x.SRI_BUSINESS_OBJECT_ID = bos.SRI_BUSINESS_OBJECT_ID
  and bos.VERSION like '%008_110_002%'
  and x.SELECT_STATEMENT like '%to be defined%'
order by x.SRI_BUSINESS_OBJECT_ID;
```

The source includes an optional commented filter `x.SELECT_CONTAINS_WHERE_IND = 0`. Add it only if that is the intended filter for the investigation. Replace the example model version with the target version. Review results and define views through the approved model deployment process; do not directly modify production metadata based only on this query.
