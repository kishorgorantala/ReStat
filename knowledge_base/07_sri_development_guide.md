# SRI Development Guide: Prisma Model Deployment

Source: [SRI Development Guide](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/sri_development.html)

> **Operational caution:** This is a knowledge reference transcribed from the supplied guide, not authorization to deploy, restart engines, or execute SQL. Verify current branches, model versions, environment schemas, scripts, and approvals with the responsible team before acting. Values such as model versions, engine IDs, hostnames, paths, and credentials in examples are not production defaults. The source's `ChangeMeNot` value has been omitted; never store or reuse passwords from examples.

## Deploy a new model in DEV

### 1. Prepare a local working copy
- Copy the project library from [STX.SRI](https://github.deutsche-boerse.de/stx/STX.SRI/) (or the previous source).
- Use the model branch specified by PRISMA, typically in its email.

### 2. Apply PRISMA-requested model changes
Use the Protofile, previous model, and PRISMA request to determine the exact change. The source guide gives these examples:

| Change | Action described in source guide |
|--------|-----------------------------------|
| Entity added (example: `STFmbSelection`, extension 4158) | Add it to `message_table_type.xml`, carrying forward the prior model's relevant entry. |
| Entity removed (example: `ErsAccount`, extension 2053 SNAP) | Comment out its entry in `message_table_type.xml`. |
| Field added (example: `BusinessDayWideConfig.ir_load_statistix`) | No manual action stated; ProtoC generates it. |
| AMQP queue removed, edited, or added | Use the new `model_vXXX_insert_amqp_queues.sql` supplied by PRISMA. |
| View added (example: `ProductFMBSelection`, extension 4215) | Add its definition under `STX.PRISMA_DISTRIBUTION/Resources/STX/next_model/proto_views/` and transform Protofile SQL into the SRI view SQL form. |

### View transformation pattern
The supplied `ProductFMBSelection` example illustrates the transformation:
- Map Protofile entity names to `D_STA_<Entity>` table names.
- Add validity-window predicates using `TO_TIMESTAMP($scope-timestamp$, 'YYYY-MM-DD HH24:MI:SS.FF') BETWEEN <alias>.VALID_FROM_PT AND <alias>.VALID_TO_PT` for each participating table.
- Retain the join conditions and wrap the transformed query with its expected view alias.

The full ProductFMBSelection SQL is in the source guide. Treat it as a model-specific blueprint, not a generic query to copy without checking the current Protofile and schema.

For a new view, add a `message_table_type.xml` entry like the source example, but verify its attributes and quoting against the current project XML. The pasted source contains `snap=""""`; that appears malformed and must not be copied without validation.

Additional model-change notes from the source:
- If a view refers to a table not present in DEV (the guide describes this as a prefixed table), use the project's Viewtable convention and add it to `Resources/STX/next_model/proto_views_NonDev` so it is deployed to higher environments (ACC, SIM, PRD). The user-provided `SRI_Knowledge.xlsx` says `type="VT"` for Viewtable, while the supplied prose guide says `type="V"` and describes it as Viewtable. This is an unresolved source conflict; check the current branch's XML/schema before selecting a type.
- For views based on mocked tables, consult `Resources/STX/next_model/standard_objects`.
- Update the `<messages version="vXXX_XXX_XXX">` version to the target model version.
- If the Protofile adds entity types, update `Resources/STX/next_model/create_types.sql`.
- If the Protofile adds a Varray, the source guide points to `STX.PRISMA_PROTOX/resources/xsl/protox2java.xsl`; verify the supported generation changes with the ProtoX maintainers.

### 3. Generate DEV deployment artifacts with Jenkins
The source guide says to use the `STX.PRISMA_PROTOX` Jenkins job from Citrix, select **Build with Parameters**, provide the Protofile, `message_table_type.xml`, and `insert_amqp_queues.sql`, then select `install_metadata` and build. ProtoX generates deployment scripts in `STX.PRISMA_DISTRIBUTION` for later higher-environment deployment.

Jenkins endpoint in source: `http://dwhdas12:8080/jenkins/job/STX.PRISMA_PROTOX/`.

### 4. Install first on DEV engine 05
The source guide's sequence is:
1. Run `STX.PRISMA_DISTRIBUTION` Jenkins job with the model conversion and engine-assignment input files. The supplied example refers to `database_model_conversion118.txt` and `database_engine_assignments_empty.txt`; validate the files, target schema, and all parameters before use.
2. Copy `VERSION_MAIN` and `VERSION_SRI` from the previous model in the relevant `F_SRI_MODEL_STATE` record to the new model. Confirm target records and values before changing database state.
3. On the appropriate application server, navigate to `/home/infa64/infa_files/Scripts/SRI.PRISMA` and restart the target engine using the project's restart procedure. The source example is `./restart.sh restart PRISMA_DEV_05`.
4. Verify the engine log and process state. The source examples are `tail ../../Logs/SRI.PRISMA/PRISMA_DEV_05.log` and `ps -fu sri1`.
5. After PRISMA testing and approval, distribute to the remaining engines. Restart them one at a time and monitor CPU/memory after each restart.

Jenkins endpoint in source: `http://dwhdas12:8080/jenkins/job/STX.PRISMA_DISTRIBUTION/`.

## Uninstall a model in DEV
The source guide lists model-versioned artifacts under `STX.PRISMA_DISTRIBUTION/Resources/STX/next_model/` and `distribution_scripts/`, including model metadata, table creation, cleanup, types, and views scripts. It then points to the versioned `cleanup_<version>.sql` under `Resources/STX/<version>/` for execution on the respective environment database as the corresponding `SRI_XXX` user.

This cleanup can fail if the model was already distributed to an engine. The source guide describes a recovery involving resetting `F_SRI_INSTANCE_STATE.sri_model_id` from the new model ID to the previous model ID, then rerunning the final deletes. The example IDs `299` and `300` are illustrative only. This is a potentially destructive database operation: inspect the actual cleanup SQL and affected rows, confirm the model/engine state, and obtain database/operator approval before any execution. Never reuse the example IDs without verification.

Example artifact names for source-guide model `011_150_001`:
- `model_metadata_011_150_001.sql`
- `create_tables_011_150_001.sql`
- `cleanup_011_150_001.sql`
- `createTables_011_150_001`
- `createTypes_011_150_001`
- `createViews_011_150_001`

## Deploy a new model in NonDEV
The source guide describes NonDEV deployment through Gradle using two descriptor scripts:
1. A metadata script, which amends metadata on the database and can generally be deployed ahead of the conversion.
2. A conversion-day script, which performs the model switch.

Example descriptor files in the source:
- `SRI_metadata.csv`
- `SRI_conversionDay.csv`

### Metadata descriptor
- `branch`: the Git branch in `STX.SRI` used for the deployment.
- `schema`: `SRI_XXX`, where `XXX` is the target environment short name.
- Scripts 01/03/04/05/42/43/51/26: source guide says to update the model version in the corresponding scripts.
- Script 02: copy the matching branch's generated NonDEV metadata views SQL from `STX.PRISMA_DISTRIBUTION/Resources/STX/<model>/10_NonDev_MetaViews_<model>.sql`.
- Script 25: copy the matching branch's model queue insert SQL, e.g. `model_vXXX_insert_amqp_queues.sql`.

### Conversion-day descriptor
- Script 61: copy the generated ProtoX conversion SQL from the matching model branch.
- Script 65: update the model version in the corresponding scripts.
- Use the **data schema**, not the metadata schema, for the specified data-scheme scripts; the second schema value remains the metadata schema. The source's ACC example uses `EUREX_PRISMA` and `SRI_ACC` respectively; confirm actual environment mappings in `03_environments.md` and current deployment configuration.
- Update the environment and model version in the model-switch script. The source example references `Deployment/02-ORACLE/ACC/44__update_f_sri_instance_state_switch_model.sql`.
- If engine scripts changed, the source guide lists `PRISMA_LIVE_ENGINE.sh`, `PRISMA_MAIN_ENGINE.sh`, `PRISMA_SNAP_ENGINE.sh`, and `PRISMA_WHATIF_ENGINE.sh` for file-copy deployment. Verify target, branch, owner, permissions, and approval before deployment.

## Deployment preparation pipeline
The source guide points to the `PrepareSTX.SRIforDeployment` Jenkins pipeline, accessible through Citrix, as an automation path for the described preparation. It notes exceptions that still require manual version checks:
- `prisma_main-<version>.jar`
- `sri-<version>.jar`
- If jar versions change, update the corresponding `classpath_names.sh` deployment descriptor.
- For newer SRI versions, deploy the one-time metadata script `88_fill_model_versions.sql` with the correct version.

Source endpoints and paths are historical examples. Check that jobs, branches, files, versions, and process have not changed before use.

## Troubleshooting clues for log analysis
These are investigation leads, not confirmed root causes by themselves:
- New model starts with class or mapper errors: compare deployed model jar, common `sri.jar`, Protofile branch/version, and generated Metadata versions.
- Model is deployed but no messages are processed: verify the queue insert artifact and model rows in `F_SRI_MODEL_AMQP_QUEUE`.
- NonDEV view or metadata deployment fails: inspect whether the matching generated NonDEV view SQL (descriptor Script 02) and conversion SQL (Script 61) were deployed in the intended phase and schema.
- Model switch state appears inconsistent: inspect the environment-specific model-switch SQL and corresponding `F_SRI_INSTANCE_STATE` records; do not suggest running corrective SQL without operator review.
- Failure occurs after engine restart: compare startup logs, deployed jar/classpath versions, target engine, and one-at-a-time restart/health checks.
