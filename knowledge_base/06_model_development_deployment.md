# SRI Model Development & Deployment

Source: [SRI principles](https://pages.github.deutsche-boerse.de/stx/STX.DOC/html/development/applications/sri/sri_principles.html#development-of-new-model)

## Development of a new model
1. Received **Protofile** is converted with Google **ProtoC** to generate basic Java files.
2. The same Protofile plus the local configuration file **`message_table_type.xml`** are input to the Java **ProtoX** tool.
3. Generated mappers + files regenerated from the previous model are put into a Java project.
4. Compiled model jar is stored in Git. The second required jar is the common **`sri.jar`**.

New model generation is run by script from **local git bash**; ProtoC and ProtoX run on the **remote development server**.

### ProtoX generates
1. DDL description for the data schema
2. Metadata SQL
3. SQL inserts with model-relevant data for the data schema
4. Java code called **mappers**
5. Other outputs, e.g. CSV description for the Rica and DevNet teams

## DOU development workflow
The user-provided DOU (revision 1.0, 2026-09-01) adds this broader workflow; exact pipeline behavior and repository process must be confirmed for the target model:
1. PRISMA provides the Protofile, queue definition, change description, and related tracking details.
2. Compare the requested Protofile against the currently deployed/previous model and verify that its changes match the request.
3. Identify whether the change adds an entity, modifies/adds fields, or changes a view. Entity definitions include IDs, fields/types, processing phase (e.g. Live/Snap/EOD), and queue details.
4. Carry forward and update `message_table_type.xml`; structural field generation may be automatic, while entity-to-table/view mapping needs explicit configuration.
5. The DOU describes a Jenkins pipeline that runs ProtoC, ProtoX, a Gradle build, artifact publication, and metadata preparation/installation. Generated outputs include Java Protobuf classes, mappers, SQL, DDL, and metadata registrations.
6. Metadata installation and active data-schema/model distribution are separate concerns. The DOU describes metadata preparation as not necessarily changing the active development DB/model instance; verify the actual pipeline target and impact before each run.
7. Distribute to one approved test instance first, verify model/instance/queue state and engine startup, obtain PRISMA testing confirmation, then proceed to remaining DEV instances using approved distribution files.

The DOU's worked model versions, instance numbers, and file names are examples and are not reusable deployment values. Its Jenkins example uses host `dwhdas22.stx.dbgtst.de` on port `8443`; verify the current pipeline endpoint through approved internal documentation. Follow the current branch, version, approval, and runbook for each change.

## Deployment to development environment
- Copy project jar (e.g. `prisma_v008_110_002.jar`) and common jar (`sri.jar`) to the server.
- Library paths and initial parameters are defined in **startup scripts**. With automation, paths per environment must be correct.
- Load Metadata into the metadata schema (e.g. `sri_tst`) and apply additional fixes.
- **SQL view definitions** are part of the Metadata:
  - DEV: copied from previous model into the new model, then a manual patch (based on the Protofile) is applied.
  - Non-DEV: view definition SQL is generated from DEV data.
- DEV uses system/message date differently, so a **static fix to Metadata** must be applied.
- Load the full set of message queues for the model into the metadata schema table **`F_SRI_MODEL_AMQP_QUEUE`**.

## Deployment to non-development environment
- Generated code contains **`(to be defined)`** as placeholder for Metadata view definitions.
- A script containing the view definitions must be prepared.
- A helper program that copies view definitions from the previous model works on all environments but is **not recommended** for non-DEV (needs more Operations interaction). Prefer an SQL script.

## Log analysis hints
| Symptom | Likely cause to check |
|---------|-----------------------|
| `ClassNotFoundException` / `NoClassDefFoundError` at startup | Wrong jar path in startup script, missing model jar or `sri.jar`, version mismatch |
| No messages consumed / no listener started | Queues missing in `F_SRI_MODEL_AMQP_QUEUE` for the model |
| SQL error referencing a view, or `(to be defined)` in SQL | View definitions not prepared/applied in non-DEV |
| Wrong or unexpected dates in DEV data | Static system/message date Metadata fix not applied |
| Mapper / field mismatch errors | Model jar generated from a different Protofile version than the deployed Metadata |

## Repositories and tools noted in the DOU

Repositories referenced by the DOU:
- [STX.SRI](https://github.deutsche-boerse.de/stx/STX.SRI)
- [STX.PRISMA_PROTOX](https://github.deutsche-boerse.de/stx/STX.PRISMA_PROTOX) – model generation automation
- [STX.PRISMA_DISTRIBUTION](https://github.deutsche-boerse.de/stx/STX.PRISMA_DISTRIBUTION) – model distribution resources
- [STX.SRI_PRISMA](https://github.deutsche-boerse.de/stx/STX.SRI_PRISMA)
- [STX.DEPLOYMENT](https://github.deutsche-boerse.de/stx/STX.DEPLOYMENT)
- [STX.SRI_APPS](https://github.deutsche-boerse.de/dev/STX.SRI_APPS)

Tools listed by the DOU: Toad, PuTTY, WinSCP, Jira, GitHub, Gradle, Java, Control-M, Jenkins, and JFrog. The DOU names an issue-tracker workbook `SRI_Issue_Tracker.xlsx`, but does not provide its location in the supplied text.
