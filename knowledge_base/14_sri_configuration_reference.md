# SRI Configuration Reference

Source: User-provided **SRI - Document Of Understanding**, revision 1.0 dated 2026-09-01.

> The values below are a dated documentation baseline, not guaranteed live settings. Configuration differs by release and environment. Verify current files and metadata with the owner before drawing conclusions or recommending a change. Credential values, wallet details, secret-file paths, hosts, individual users, and notification-channel names are intentionally excluded.

## Configuration groups

| Group / example properties | DOU-described purpose |
|----------------------------|------------------------|
| `repository.*` | Oracle repository connectivity and pooling. Password/credential properties are secret-bearing and are not included here. |
| `logging.*` | StoreRequest, SRI request, and file logging. |
| `snapLive.*` | Limits parallel snapshot processing (for example `snapLive.MaxSnapParallelRequest`). |
| `jdbc.*` | JDBC reconnection behavior (for example `jdbc.jdbcReconnectionRetryCount`). |
| `email.*` | Notification rate/time limits and script configuration. |
| `watchdog.*` | BOQL/GPB timeout and long-running request detection. |
| `broker.*` | Queue subscriptions, message acknowledgement, response routing. |
| `environment.*` | Environment behavior, class initialization, and model-version validation. |
| `monitoring.*` | Health and request-throughput monitoring intervals. |
| `hdfs.*`, `hive.*` | Hadoop/Hive integration and storage/housekeeping configuration. |
| `api.server.*` | Optional API server activation, environment name, and base port. |

## DOU comparison values

The source gives these values for DEV, SIT2, ACC, DR, SIM, and PROD. They are recorded for triage context only; units are not specified for every timeout, and the source has multiple tables with incomplete or inconsistent cells.

| Setting | DEV | SIT2 | ACC | DR | SIM | PROD |
|---------|-----|------|-----|----|-----|------|
| Connection pooling | Off | On | On | On | On | On |
| Maximum parallel Snap requests | 5 | 1 | 1 | 1 | 1 | 1 |
| BOQL watchdog threshold (source value) | 5000 | 1000 | 1000 | 1000 | 5000 | 6000 |
| GPB normal timeout (source value) | 1000 | N/A | 1000 | 1000 | 2000 | 2000 |
| GPB additional timeout (source value) | 40 | N/A | 60 | 60 | 60 | 60 |
| JDBC reconnection retry count | 10 | 2 | 10 | 10 | 10 | 10 |
| API server | On | Off | Off | Off | Not configured | Not configured |
| Environment constant | `DEV_ENV` | `NON_DEV_ENV` | `NON_DEV_ENV` | `NON_DEV_ENV` | `NON_DEV_ENV` | `NON_DEV_ENV` |
| Class initialization allowed | Yes | No | No | No | No | No |
| Model-version check | Yes | Yes | Yes | Yes | Yes | Yes |
| AMQP acknowledgement | Yes | Yes | Yes | Yes | Yes | Yes |
| AMQP protocol version | `amqp10` | `amqp10` | `amqp10` | `amqp10` | `amqp10` | `amqp10` |
| Consumer receive timeout (source value) | 10000 | 60000 | N/A | N/A | 60000 | 60000 |
| Monitoring interval (source value) | 150000 | 2000000 | 150000 | 150000 | 4000000 | 4000000 |
| Request monitoring timeframe (source value) | 200000 | 4000000 | 400000 | 400000 | 8000000 | 8000000 |
| HDFS enabled | No | No | No | No | No | No |
| JDBC DML synchronization | No | N/A | N/A | N/A | N/A | Yes |

The DOU separately describes monitoring profiles as short for DEV, medium for ACC/DR, long for SIT2, and longest for SIM/PROD. Exact profiles and units should be checked in the active configuration.

Other DOU baseline values: email count limit is 3 for DEV and 1 elsewhere; email time limit is 30 for DEV and 60 elsewhere. The DOU's environment behavior table reports ProtoX user category `general` for all six environments; account identities are deliberately omitted.

## Oracle configuration metadata

The DOU describes configuration as coming from both server-side configuration files and Oracle metadata. It lists these configuration object groups:
- Connection-related settings and server/broker connection state.
- Engine settings and Prisma engine tuning.
- Engine monitoring intervals and error tracking.
- Engine-instance assignments and AMQP connection associations.
- Per-instance HDFS/Hive settings.

The object inventory is in [13_sri_metadata_model_reference.md](13_sri_metadata_model_reference.md). Some connection objects may hold credentials or authentication settings; do not inspect or expose those values through ReStat.

## JVM runtime and JDK (confirmed facts)

Confirmed from the ACC MAIN engine on `dwhdas26`, 2026-10-12. Reconfirm per host/engine; JDK selection can differ by engine and release.

- **ACC MAIN (`PRISMA_ACC`) runs Azul Zulu `11.0.28+6-LTS`** (from the ZGC log `gc,init` "Version:" line). ZGC is active, so any claim of Java 8 for this engine is wrong — **`-XX:+UseZGC` cannot start on Zulu 8** (ZGC is JDK 11+). Zulu 8 is also installed on the host **for HDFS compatibility only**, which is a common source of "we use Java 1.8" confusion.
- **JDK selection is PATH-based:** `sri_env.sh` / `sri_env.sh_sri_<env>` set `JAVA_LOCATION=java` (not an absolute path), so the engine uses whatever `java` resolves to on the launching user's (`infa64`) PATH. A different login (e.g. a personal CyberArk account) may have **no `java` on PATH** — that is why `java -version` fails in an interactive shell even though the engine runs fine. Prefer verifying via the running process or the gc log, not the interactive shell.
- **How to confirm the real JDK (non-privileged):** the engine enables `-Xlog:gc*:file=./log/PRISMA_ACC_gc.log` (in `/infa_files/Scripts/SRI/logs`, with `.0`–`.4` rotations). `grep -m1 "Version:" PRISMA_<ENGINE>_gc.log` gives the exact release. `/proc/<pid>/exe` is usually **permission-denied** to other users (engine runs as `infa64`/group `STX_ACC`).
- **ZGC memory behavior:** ZGC colored-pointer multi-mapping makes RSS appear ~3× `Xmx`. `-XX:+AlwaysPreTouch` commits that upfront at startup; removing it makes RSS grow on demand with the working set. ACC MAIN actual heap peak is ~2.6 GB against `Xmx32G`, so the heap is heavily over-provisioned relative to use.
- **Observed ACC MAIN JVM flags:** `-XX:+UseZGC -XX:ConcGCThreads=8 -XX:ZCollectionInterval=15 -XX:ZAllocationSpikeTolerance=5 -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError`; `-XX:+AlwaysPreTouch` was **removed 2026-10-09**. `-XX:SoftMaxHeapSize` is **not valid on JDK 11** (needs 13+); Generational ZGC needs 21+.
- Config files: `sri_env.sh_sri_<env>` holds `JAVA_VM_OPTIONS_<ROLE>` and `MAX_MEMORY_<ROLE>` (e.g. `MAX_MEMORY_MAIN`). ACC `sri_env.sh_sri_acc` / CIF `sri_env.sh_cif_acc`. Edits here are **state-changing** (require engine restart) and ACC/PROD are **DevOps/AccOps-owned** — propose via deployment request with rollback, never apply ad hoc.

## ACC engine tuning baseline (`dwhdas26`, confirmed 2026-10-12)

Non-secret values read from `sri_env.sh_sri_acc`, `engine.cfg_sri_acc`, `connectionPooler.cfg`, and the MAIN gc log. Credentials, wallet alias, `secretPath`, emails, and proxy values were present but are intentionally excluded. These are a dated baseline; reconfirm before acting.

**Per-engine memory / GC (`sri_env.sh_sri_acc`):**
| Engine | Xms | Xmx | GC | AlwaysPreTouch | Map class |
|--------|-----|-----|----|----------------|-----------|
| MAIN (`PRISMA_ACC`) | 16G | 32G | ZGC | removed 2026-10-09 | `PrismaEngine` |
| SNAP (`PRISMA_ACC_SNAP`) | 4G | 8G | ZGC | **still enabled** | `PrismaEngine_Step1` |
| LIVE (`PRISMA_ACC_LIVE`) | 4G | 8G | default (G1 on JDK11) — `JAVA_VM_OPTIONS_LIVE` commented out | n/a | `PrismaEngine_Step1` |
| WHATIF (`PRISMA_ACC_WHATIF`) | 4G | 8G | default (G1) — VM options commented out | n/a | `PrismaEngine` |

Common ZGC flags (MAIN/SNAP): `-XX:+UseZGC -XX:ConcGCThreads=8 -XX:ZCollectionInterval=15 -XX:ZAllocationSpikeTolerance=5 -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError`, gc logging `-Xlog:gc*:file=./log/PRISMA_ACC[_SNAP]_gc.log:...:filecount=5,filesize=100m`. All four engines share `CFG_FILE=./cfg/engine.cfg_sri_acc`.

**`engine.cfg_sri_acc` tuning knobs (non-secret):**
- `snapLive.MaxSnapParallelRequest=1` (snapshots serialized; DEV uses 5).
- Logging: `logging.storeRequest=true`, `logging.sriRequest=true`, `logging.printMessageReplyContent=false`, `repository.jdbcLogging=false`, **`debugMessageContent=true`** (verbose; logs message content).
- `repository.connectionPooling=true`, pool cfg `./cfg/connectionPooler.cfg`.
- Watchdog: `watchdog.boql.timeOverLimit=1000` (ms), `watchdog.gpb.timeoutNormal=1000` (ms), `watchdog.gpb.timeoutAdditional=60` (s).
- Monitoring: `monitoring.intervalMs=150000`, `monitoring.requests.timeframeMs=400000` (hence "last 400 seconds" stats).
- `broker.acknowledgeMessages=true`, `broker.amqpVersion=amqp10`, `environment.constant=NON_DEV_ENV`, `environment.checkModelVersion=true`, `hdfs.isUsed=false`, `api.server.activate=false`.

**`connectionPooler.cfg` (two pools):**
- `repository`: `maxActive=350, initialSize=10, maxWait=10000, minEvictableIdleTimeMillis=120000, removeAbandoned=false`.
- `bo` (business object): `maxActive=340, initialSize=20, maxWait=50000, removeAbandoned=false`.

**GC performance observation:** the MAIN gc log's "Allocation Stall" lines are ZGC's **periodic statistics-table rows** (printed ~every 10 s), not stall events — the values are `0.000 / 0.000 ms` and `0 / 0 ops/s`, i.e. **zero actual allocation stalls** (confirmed 2026-10-12, ~22,451 s uptime). A `grep -c "Allocation Stall"` counts the stat label, not occurrences, and will mislead — to find real stalls, look for `Allocation Stall (<thread>) <n>ms` with a non-zero value. GC is therefore **not** the latency driver here. Request latency from MONITORING is bimodal (~25–50 ms vs ~400–530 ms, bursts to ~736 ms) with zero errors; since GC is clean, the slow windows point to **Oracle (query/fetch durations) or request-type mix**, not the JVM.

## How to use this reference in log analysis

- Compare a reported timeout with the environment-specific baseline only as a hypothesis; first establish the active property, units, and deployed configuration version.
- For startup/connection errors, distinguish repository/JDBC connectivity from AMQP broker/queue connectivity.
- For slow or hanging requests, correlate watchdog/monitoring settings with timestamps and request logs; a threshold is not proof of root cause.
- For queue redelivery or backlog, verify acknowledgement behavior and consumer timeout in the active environment. Do not advise changing acknowledgement settings without an approved test plan.
- Do not use this file to infer a current configuration or reveal how to authenticate to an environment.
