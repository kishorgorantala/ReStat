# SRI Environments

> The environment list and schema examples below come from the user-provided DOU revision 1.0 dated 2026-09-01. They are not authoritative live connection instructions. Verify names and mappings against the current environment overview before use.

## Environment names
The DOU lists Development, SIT1, SIT2, POPE, Acceptance (ACC), Simulation (SIM), Conversion, Dress Rehearsal (DR), and Production (PROD). Dress Rehearsal is described as a temporary production-like environment used around release testing.

## Access method and deployment ownership
Latest user-provided environment update:
| Environment | Access method / account | Deployment ownership note |
|-------------|-------------------------|---------------------------|
| DEV | `sri1`, `sri2`, or `sri3` | |
| SIT1 / SIT2 / DR | `infa64` | Deployments are performed by the SRI team. |
| ACC / SIMU / PROD | CyberArk with each person's own username | Deployments are performed by DevOps/Operations colleagues. |

These are account/access-method names only, not credentials. Follow the organization's current access policy; never store passwords or CyberArk secrets here.

## Metadata schema examples
| Environment | Schema/user named in DOU | Notes |
|-------------|--------------------------|-------|
| Development | `SRI_TST` | Metadata schema example. |
| SIT1 | `SRI_SIT` | DOU metadata-schema value. |
| SIT2 | `SRI_SIT2` | DOU metadata-schema value. |
| ACC | `SRI_ACC` | |
| DR | `SRI_SIT` | Confirm current mapping before querying. |
| SIM | `SRI_SIM` | |
| PROD | `SRI_PRD` | |
| POPE / Conversion | Not specified | Confirm with environment owner. |

The latest user-provided update separately lists these application data DB/schema names and aliases; these are not the Metadata schemas above:
| Environment / data | Application data DB/schema or alias |
|---------------------|-------------------------------------|
| DEV | `DWHPRISDEV` |
| ACC / Acceptance | `DTBPRISPRIMA` (schema) |
| ACC CIF data | `CIFACC` (alias/schema; confirm which form the connection expects) |
| PROD | `DTPRIMA` |
| SIMU | `DTBSIM` |
| SIT1 / SIT2 / DR | Not provided in this update |

The user confirmed that the ACC metadata export owner `STX_DEV` maps to Acceptance application schema `DTBPRISPRIMA`. `CIFACC` is a separate CIF alias/schema and is not represented by that export.

## Server host inventory
The following hostnames are listed in the user-provided DOU revision 1.0 dated 2026-09-01. Treat them as a documentation snapshot, not a guarantee of current routing or access.

| Environment / purpose | Host(s) listed in DOU | Notes |
|-----------------------|----------------------|-------|
| Development | `dwhdsri01.stx.dbgtst.de`, `dwhdsri02.stx.dbgtst.de`, `dwhdsri03.stx.dbgtst.de` | DOU's development server inventory. |
| DR | `dwhdas25.stx.dbgtst.de` | Dress Rehearsal. |
| SIMU | `dwhsas33` | Standard SIMU host; listed without a domain suffix. |
| ACC | `dwhdas26` | Current standard ACC host. |
| ACC (old) | `dwhdas27-old` | Superseded host name provided for reference. |
| ACC (CIF) | `dwhaas03-cif` | CIF host. |
| PROD | `dwhpas27`, `dwhpas46` | Standard PROD and PROD CIF, respectively. |
| SIT1 | `dwhdas24.stx.dbgtst.de` | |
| SIT2 | `dwhdas28.stx.dbgtst.de` | |
| SIT deployment | `dwhpdeploy1` | Deployment server; environment hostname suffix not provided. |
| POPE / Conversion | Not specified | Confirm with environment owner. |

The latest user-provided update supersedes the DOU's old ACC host `dwhdas27`. DEV hostnames match the DOU. Earlier operations notes also mention `dwhdas56` as a DEV alias/server; verify the current alias and environment mapping before connecting or operating an engine. Service names, wallet aliases, and credentials remain excluded; use approved internal authentication instructions.

## Engine topology
- DEV is described as multiple instances of a common processing engine distributed across development servers.
- Higher environments are described as using separate Main, Live, Snap, and What-If engine roles, each with queue assignments.
- Verify the current topology and instance-to-engine mapping before troubleshooting or restarting.

Model queue assignments are stored in metadata object `F_SRI_MODEL_AMQP_QUEUE` (verify in the live schema). For model-specific deployment context see [06_model_development_deployment.md](06_model_development_deployment.md).

### User-confirmed DEV topology (host `dwhdsri01`, 2026-10-09)
Snapshot supplied by the user from `sri1@dwhdsri01`. This is a saved directory/filename reference, not live health evidence. Reconfirm host and instance for each new incident and verify files still exist.

- The user states DEV does **not** use a standard/CIF split and does **not** use the four Main/Live/Snap/What-If engine roles seen in higher environments. Instead, DEV runs **numbered engine instances** `PRISMA_DEV_NN` plus a `PRISMA_FlushQueueEngine`.
- Observed instance numbers: `01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 14` (numbers `11, 12, 13` were not present in this listing — confirm whether unused or on another host).
- **Multi-host distribution (confirmed 2026-10-12):** DEV instances run spread across `dwhdsri01`/`dwhdsri02`/`dwhdsri03` under users `sri1`/`sri2`/`sri3`, while `/infa_files/.../Logs` is shared across them. A `ps -ef` on one host only sees that host's instances, so a single-host process check reports instances on the other hosts as false DOWN. Check all three hosts, and use recent shared-log `|MONITORING|` lines as the cross-host liveness signal. See the DEV exception in [12_sri_logs_controlm.md](12_sri_logs_controlm.md).
- Paths on `dwhdsri01`:
  - Logs: `/infa_files/Scripts/SRI/Logs/` (note capital `L`; differs from the ACC inventory path `/infa_files/Scripts/SRI/logs`).
  - Scripts: `/infa_files/Scripts/SRI/scripts`
  - Config: `/infa_files/Scripts/SRI/cfg`

| Per-instance purpose | Observed file pattern (example) |
|----------------------|----------------------------------|
| Instance runtime | `PRISMA_DEV_NN.log` (e.g. `PRISMA_DEV_01.log`) |
| Instance errors | `PRISMA_DEV_NN_err.log` |
| Instance info/operational | `PRISMA_DEV_NN_info.log` |
| Instance startup | `start_PRISMA_DEV_NN.log` |
| Instance broker/network | `netty_PRISMA_DEV_NN.log` |
| Instance runtime archives | `PRISMA_DEV_NN.log.YYYY-MM-DD` (observed `.2026-10-06`..`.2026-10-08`) |
| Aggregated across instances | `ALL_err.log`, `ALL_info.log` |
| Flush-queue engine | `PRISMA_FlushQueueEngine`, `FlushQueue_err.log`, `FlushQueue_info.log`, `netty_PRISMA_FlushQueueEngine`, `start_PRISMA_FlushQueueEngine` |
| Per-instance config | `cfg/engine.PRISMA_DEV_NN` |
| Thread-count history | `getThreadCountLog.txt` (shared scope; verify instance before interpreting) |

Archive dates (`.2026-10-06`..`.2026-10-08`) describe available files only, not the incident date or retention guarantee. For a DEV instance investigation, start with that instance's `PRISMA_DEV_NN.log`, `PRISMA_DEV_NN_err.log`, and `start_PRISMA_DEV_NN.log`.

**Unresolved conflicts to confirm (do not treat as fact yet):**
- CIF artifacts are present on this DEV host despite the "no CIF in DEV" statement: `cfg/engine.PRISMA_CIF_LIVE`, `cfg/engine.PRISMA_CIF_MAIN`, `cfg/engine.PRISMA_CIF_SNAP`, `scripts/PRISMA_CIF_LIVE`, and `scripts/sri_env.sh_cif_dev`. Confirm whether these are legacy/leftover or active.
- Main/Live/Snap scripts are present despite the "no four engine roles in DEV" statement: `scripts/PRISMA_SRI_MAIN.sh`, `PRISMA_SRI_MAIN_4.sh`, `PRISMA_SRI_MAIN_75.sh`, `PRISMA_SRI_LIVE.sh`, `PRISMA_SRI_LIVE_OLDER.sh`, `PRISMA_SRI_SNAP.sh`, `PRISMA_SRI_OLDER.sh`. Confirm whether these are shared/templated launch scripts reused by numbered instances, or legacy.
- Log directory case differs from the ACC inventory (`Logs` vs `logs`); confirm the correct case per host before scripting paths.

### User-confirmed SIT1 topology (host `dwhdas24`, 2026-10-09)
Snapshot supplied by the user from `infa64@dwhdas24`. Saved directory/filename reference, not live health evidence. Reconfirm host, engine role, and file existence for each new incident.

- Unlike DEV, SIT1 **does use the four engine roles** Main / Live / Snap / What-If (plus FlushQueue). There is **no standard/CIF split** operating on this SIT1 host (CIF-named entries in the listing are artifacts — see conflicts below).
- Paths on `dwhdas24`: logs `/infa_files/Scripts/SRI/logs` (lowercase `l`), scripts `/infa_files/Scripts/SRI/scripts`, config `/infa_files/Scripts/SRI/cfg`.

| SIT1 engine role | Runtime log | Errors | Info/startup | Broker/network | Startup |
|------------------|-------------|--------|--------------|----------------|---------|
| Main | `PRISMA_SIT.log` | `PRISMA_SIT_err.log` | `PRISMA_SIT_info.log` | `netty_PRISMA_SIT.log` | `start_PRISMA_SIT.log` |
| Live | `PRISMA_SIT_LIVE.log` | `PRISMA_SIT_LIVE_err.log` | `PRISMA_SIT_LIVE_info.log` | `netty_PRISMA_SIT_LIVE.log` | `start_PRISMA_SIT_LIVE.log` |
| Snap | `PRISMA_SIT_SNAP.log` | (see note) | (see note) | `netty_PRISMA_SIT_SNAP.log` | `start_PRISMA_SIT_SNAP.log` |
| What-If | `PRISMA_SIT_WHATIF.log` | `PRISMA_WHATIF_err.log`* | `PRISMA_WHATIF_info.log`* | `netty_PRISMA_SIT_WHATIF.log` | `start_PRISMA_SIT_WHATIF.log` |

- Runtime archives use `PRISMA_SIT*<role>.log.YYYY-MM-DD` (observed June–October 2026, with gaps on idle days).
- **Snapshot processing → `PRISMA_SIT_SNAP.log`.** This is the correct starting log when an ETL/downstream team is awaiting a snapshot (`C_CTL_RESOURCE_STATUS` / resource_status) for SIT1.
- Scripts: `PRISMA_MAIN_ENGINE.sh`, `PRISMA_LIVE_ENGINE.sh`, `PRISMA_SNAP_ENGINE.sh`, `PRISMA_WHATIF_ENGINE.sh`, `restart.sh`, `start.sh`, `stop.sh`, `checkstatus.sh`, plus `*_amqp01.sh` variants. SIT1 env file: `sri_env.sh_sri_sit`.
- Config: `engine.cfg`, `engine.cfg_sri_sit`, `engine_Wallet.cfg_sri_sit`, `possible_engines.cfg`.

**SIT1 listing artifacts to ignore (not SIT1 engines):**
- Semicolon-named files such as `PRISMA_CIF_SNAP;sri3;dwhaas03;PRISMA_SRI_SNAP.sh;cif;sri_env.sh_cif_*.log` and `PRISMA_SNAP_ENGINE;infa64;dwhdas28;...` are malformed filenames (an engine-descriptor string used as a filename). They reference **other hosts** (`dwhaas03` CIF, `dwhdas28`) and are not the active SIT1 engine logs.
- Generic-named logs (`PRISMA_MAIN_ENGINE_err.log`, `PRISMA_SNAP_ENGINE_err.log`, `PRISMA_SRI_MAIN_err.log`, etc.) also appear; for SIT1 investigation use the `PRISMA_SIT*` role logs, not these. *(The What-If `_err.log`/`_info.log` exact base name should be confirmed on the host; `PRISMA_WHATIF_*` and `PRISMA_WHATIF_ENGINE_*` variants both appear.)*

### User-confirmed SIT2 topology (host `dwhdas28`, 2026-10-12)
Snapshot supplied by the user from `infa64@dwhdas28`. Confirmed via a live morning check (all four engines UP, started Oct 10). This matches the DOU SIT2 host `dwhdas28`.

- SIT2 uses the four engine roles with a **`PRISMA_SIT2`** prefix (distinct from SIT1's `PRISMA_SIT`): `PRISMA_SIT2` (Main), `PRISMA_SIT2_LIVE`, `PRISMA_SIT2_SNAP`, `PRISMA_SIT2_WHATIF`.
- Paths on `dwhdas28`: logs `/infa_files/Scripts/SRI/logs`, scripts `/infa_files/Scripts/SRI/scripts`, config `/infa_files/Scripts/SRI/cfg`. Runtime archives `PRISMA_SIT2*.log.YYYY-MM-DD` (observed `.2026-10-08`..`.2026-10-11`); matching `netty_PRISMA_SIT2*.log` and `start_PRISMA_SIT2*.log`.
- SIT2 metadata owner (from the database catalog) is `EUREX_PRISMA_SIT2_SRI`.
- **Health-check engine list:** `PRISMA_SIT2 PRISMA_SIT2_LIVE PRISMA_SIT2_SNAP PRISMA_SIT2_WHATIF`.
- Artifacts present on this host but not the active SIT2 engines: generic `PRISMA_MAIN_ENGINE_*`/`PRISMA_LIVE_ENGINE_*`/`PRISMA_SNAP_ENGINE_*`/`PRISMA_WHATIF_ENGINE_*` logs, and CIF scripts/config (`PRISMA_CIF_*.sh`, `sri_env.sh_cif`, `engine.cfg_cif_acc`, `possible_engines.cfg_precif`). Confirm before assuming CIF runs here.

> Environment ≠ host reminder: `PRISMA_SIT*` (SIT1 on `dwhdas24`) and `PRISMA_SIT2*` (SIT2 on `dwhdas28`) are different. Pick the engine prefix from the host you are actually logged into — running the SIT1 engine list on `dwhdas28` yields false DOWN + "No such file" on the logs.

### User-confirmed DR configuration (host `dwhdas25`, 2026-10-15)

Source: user-provided `engine.cfg` from `yo328_t2@dwhdas25`. Saved configuration snapshot, not live health evidence. Reconfirm host and engine for each new incident.

- **Connection type:** `development` (not wallet — differs from ACC/PROD which use wallet).
- **DB service name:** `dwhdrprima`
- **DB hosts (JDBC):** `dwhdx6vmc1-scan.deutsche-boerse.de:1521` (primary) and `dwhdx4-scan.deutsche-boerse.de:1521` (failover), with `CONNECT_TIMEOUT=2`, `TRANSPORT_CONNECT_TIMEOUT=1`, `RETRIES=1`.
- **Metadata schema/user:** `SRI_SIT` (confirmed — matches DOU).
- **JDBC logging:** `true` (differs from ACC which has `false`).
- **Slack channel:** `stx_sri_status_tst`.
- **Environment constant:** `NON_DEV_ENV`.
- **Connection pooling:** On, cfg `./cfg/connectionPooler.cfg`.
- **`debugMessageContent`:** `true`.
- **HDFS:** disabled (`isUsed=false`).
- **API server:** Off.
- **Watchdog:** BOQL 1000 ms, GPB normal 1000 ms, GPB additional 60 s.
- **Monitoring:** interval 150000, request timeframe 400000.
- **Email:** count limit 1, time limit 60.
- **`secretPath`:** references `ACC_secret.properties` — may be intentional or a leftover from ACC config copy. Do not store the path or its contents.
- **Login user observed:** `yo328_t2` (CyberArk — consistent with ACC/SIMU/PROD access model).
- **`protox.user`:** `general`.

### SIT2 engine configuration (from `sri_env.sh_SRI_SIT2`, 2026-10-15)

Source: user-provided `cat sri_env.sh_SRI_SIT2` from `infa64@dwhdas28`. Saved configuration snapshot, not live health evidence.

**Per-engine memory / config:**
| Engine | Xms | Xmx | Map class | Config file |
|--------|-----|-----|-----------|-------------|
| MAIN (`PRISMA_SIT2`) | 4G | 10G | `PrismaEngine` | `engine_Wallet.cfg_sri_sit2` |
| SNAP (`PRISMA_SIT2_SNAP`) | 2G | 4G | `PrismaEngine_Step1` | `engine_Wallet.cfg_sri_sit2` |
| LIVE (`PRISMA_SIT2_LIVE`) | 2G | 4G | `PrismaEngine_Step1` | `engine_Wallet.cfg_sri_sit2` |
| WHATIF (`PRISMA_SIT2_WHATIF`) | 4G | 8G | `PrismaEngine` | `engine_Wallet.cfg_sri_sit2` |

- **JAVA_LOCATION:** `$JAVA_HOME/bin/java` (Zulu 11).
- **ALLOWED_USER:** `infa64`, **ALLOWED_HOST:** `dwhdas28`.
- **LIBRARY_PREFIX:** `model`.
- **DB_CONNECTION_FILE_NAME:** `.connect_sri_sit2_stx.sql`.
- **Slack channel:** `stx_sri_status_tst`.
- **DEV_EMAILS:** jan.psota, ondrej.simanek, marek.vogel, tomas.mikulenka, michal.karasek, michal.guldan, juraj.skupy @deutsche-boerse.com.

**SIT2 restart commands confirmed (2026-10-15):**
The restart/stop/start scripts use generic engine names with the default `sri_env.sh`:
```
./restart.sh restart PRISMA_MAIN_ENGINE
./restart.sh restart PRISMA_LIVE_ENGINE
./restart.sh restart PRISMA_SNAP_ENGINE
./restart.sh restart PRISMA_WHATIF_ENGINE
```
These resolve to actual engines `PRISMA_SIT2`, `PRISMA_SIT2_LIVE`, `PRISMA_SIT2_SNAP`, `PRISMA_SIT2_WHATIF` via `sri_env.sh`.

Confirmed mapping from restart output:
| Command | Launch script | Actual engine |
|---------|--------------|---------------|
| PRISMA_MAIN_ENGINE | PRISMA_SRI_MAIN.sh | PRISMA_SIT2 |
| PRISMA_LIVE_ENGINE | PRISMA_SRI_LIVE.sh | PRISMA_SIT2_LIVE |
| PRISMA_SNAP_ENGINE | PRISMA_SRI_SNAP.sh | PRISMA_SIT2_SNAP |
| PRISMA_WHATIF_ENGINE | PRISMA_SRI_MAIN.sh | PRISMA_SIT2_WHATIF |

Do NOT pass `PRISMA_SIT2` directly to `restart.sh` — it fails with "file missing".
Note: WHATIF also uses `PRISMA_SRI_MAIN.sh` as its launch script (same as MAIN).
