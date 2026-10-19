# SRI Logs and Control-M Operations

Source: User-provided **SRI - Document Of Understanding**, revision 1.0 dated 2026-09-01. This is an operational summary; confirm current configuration and procedures with the SRI/Operations teams.

> Restart, scheduling, archive, and cleanup activities are reference information only. ReStat must not execute them. Follow approved change windows and Operations procedures.

## Log locations and naming

The DOU lists these general directory patterns:
- DEV: `/infa_files/Scripts/SRI/Logs`
- Non-DEV: `/infa_files/Scripts/SRI/logs`

Linux paths may be case-sensitive; verify exact path on the target system. The DOU describes DEV logs as instance-named (for example `PRISMA_DEV_XX`) and higher-environment logs as engine-named (`PRISMA_MAIN_ENGINE`, `PRISMA_LIVE_ENGINE`, `PRISMA_SNAP_ENGINE`, `PRISMA_WHATIF_ENGINE`). Do not infer the instance or server from environment alone.

| Log pattern | DOU-described purpose |
|-------------|-----------------------|
| `<process>.log` | Active runtime log. |
| `<process>_info.log` | Startup, informational, and operational status. |
| `<process>_err.log` | Exceptions, failures, and critical errors. |
| `netty_*.log` | Network, broker, and socket activity. |
| `start_*.log` | Process start/restart indicators. |
| `<process>.log.YYYY-MM-DD` | Dated application-log archive. |

For DEV, identify the affected instance first and inspect its matching log set. For Non-DEV, use the log for the relevant engine. Supporting logs named in the DOU include FlushQueue, CIF, and SIMU engine logs; names and availability vary by installation.

### Suggested first log by symptom
| Symptom | Start with |
|---------|------------|
| Runtime behavior | Main `.log` for the identified instance/engine. |
| Exception or failure | Matching `_err.log`, then correlate with the main log. |
| Startup/restart | `start_*.log` and `_info.log`. |
| Broker/network issue | `netty_*.log`. |
| Queue delay/flush | FlushQueue and relevant CIF logs. |
| Simulation/What-If | SIMU and What-If logs. |
| Snapshot awaited by ETL/downstream | **Snap** engine `.log` (e.g. SIT1 `PRISMA_SIT_SNAP.log`); snapshots are processed by the Snap engine. |

## Investigation approach: current vs. historical, and command scoping

These are ReStat working practices derived from live investigations. Keep all commands read-only and bounded.

### Ask current-vs-past up front
- Ask whether the issue is **happening now** or is a **past occurrence**.
  - If **past**, ask for the **date/time + timezone**, or any related log excerpt, so the search targets the correct dated archive (`<log>.YYYY-MM-DD`).
  - If the user gives **no timing**, default to treating it as a **current** issue: search the **active** `<log>` first (which holds today's entries), then widen to recent dated archives only if needed.
- A business/pinned test date (common in SIT) is **not** the processing date. Snapshot rows can carry an old `PK_BUSINESS_DATE` while `ITS` shows the real processing time — scope logs by the processing time, not the business date.

### Select the correct engine-role log before grepping
- Map the symptom to the engine role, then pick that role's log on the confirmed host (see `03_environments.md` per-environment topology). Do not grep every engine log by default.
- Snapshot/resource_status → Snap; store/select for Main/What-If → Main/What-If; Snap/Live inserts → Snap/Live; broker attach/connection → `netty_*` plus the role `.log`.

### Error-grep hygiene (avoid false positives)
- The `|MONITORING|` line contains the word `ERRORS:` (a counter). A plain `ERROR` grep matches it even when the count is `0`. To find real error-level entries, match the log-level token instead:
  ```sh
  grep -n -E "\-ERROR +:" <role>.log | head -n 30
  ```
- To judge whether an engine is healthy *now*, check the latest monitoring counter:
  ```sh
  grep -n "|MONITORING|" <role>.log | tail -n 5     # REQUEST COUNT > 0 and recent timestamp = consuming
  ```

### Process-liveness check (logs alone are not enough)
- A log can look fine while the JVM is dead, or an engine can be up but idle. Always confirm the **process is actually running** with `ps`/`pgrep`; never infer liveness from log contents alone.
- SRI engines run as Java processes. Two complementary matches:
  - **Environment prefix** (`PRISMA_ACC`, `PRISMA_SIT`, `PRISMA_DEV`, `PRISMA_CIF`) lists **all** engines for that environment at once.
  - **`-DsriLogfile=<engine>.log`** JVM arg pins a single engine (the `.log` suffix stops `PRISMA_ACC.log` from matching `PRISMA_ACC_SNAP.log`).
- **Live process inventory** (based on the operator's higher-env practice `ps -ef | grep PRISMA_<ENV> | grep java | awk '{print $1,$2,$5,$7,$8}'`), improved to label each row with its engine via the `-DsriLogfile` token:
  ```sh
  ps -ef | grep -E "PRISMA_<ENV>" | grep java | grep -v grep | \
    awk '{ eng="?"; for(i=1;i<=NF;i++) if($i ~ /DsriLogfile=/) eng=$i; print $1, $2, $5, $7, eng }'
  # columns: USER  PID  STIME(start)  TIME(cpu)  -DsriLogfile=<engine>.log
  ```
  `STIME` reveals a recent restart (e.g. an engine that came up this morning); a missing engine row means it is not running.
- **Completeness + health pass** — confirm every *expected* engine is UP (not just whichever happen to appear) and attach log health:
  ```sh
  for e in <engine-log-basenames>; do
    echo "===== $e ====="
    pid=$(ps -ef | grep -F "DsriLogfile=$e.log" | grep -v grep | awk '{print $2}' | tr '\n' ' ')
    [ -n "$pid" ] && echo "PROCESS: UP (pid $pid)" || echo "PROCESS: DOWN"
    grep "|MONITORING|" "$e.log" | tail -n 1
    grep -n -E "\-ERROR +:" "$e.log" | tail -n 5
  done
  ```
- Expected engine counts: standard higher env = 4 (Main/Live/Snap/What-If); CIF = 3 (Main/Snap/Live); DEV = the numbered instances present + FlushQueue. `checkstatus.sh` may summarize status but still confirm with `ps`.
- Verdict: **process UP + recent monitoring + no fresh errors = healthy**; **DOWN**, or UP with stale/zero monitoring, or fresh errors = investigate.

### Broker/AMQP connectivity symptoms
- Signatures: `Failed to connect to remote at: amqp://<broker-host>:<port>` and `error while listening to amqp connection: (... <queue> ...)`. These usually appear at **startup/restart** on the `[main]` / `QpidJMS Connection Executor` / `ForkJoinPool` threads.
- If the queue names already carry the correct prefix (e.g. `broadcast.STSTX_PRISMA.StoreRequestSnap.*`), the problem is **broker reachability**, not a name mismatch — check broker availability with the messaging/OPS team and the `netty_*` log; do not change queue config.
- Confirm whether the failure is a transient startup blip (errors stop, monitoring resumes with count > 0) or ongoing (errors continue, count stays 0). Credentials in these lines appear masked (`jms.password=***`); never unmask or echo secrets.

### Standard engine-health check command (save & reuse)
Operator-approved standard for **higher environments** (single host; all engines local). Swap the env prefix/engine list: ACC `PRISMA_ACC`, SIT1 `PRISMA_SIT`, CIF `PRISMA_CIF`.
```sh
cd /infa_files/Scripts/SRI/logs
# A) Live process inventory (all engines at once, labeled by -DsriLogfile)
ps -ef | grep -E "PRISMA_ACC" | grep java | grep -v grep | \
  awk '{ eng="?"; for(i=1;i<=NF;i++) if($i ~ /DsriLogfile=/) eng=$i; print $1, $2, $5, $7, eng }'
# B) Completeness (UP/DOWN per expected engine) + log health
for e in PRISMA_ACC PRISMA_ACC_LIVE PRISMA_ACC_SNAP PRISMA_ACC_WHATIF; do
  echo "===== $e ====="
  pid=$(ps -ef | grep -F "DsriLogfile=$e.log" | grep -v grep | awk '{print $2}' | tr '\n' ' ')
  [ -n "$pid" ] && echo "PROCESS: UP (pid $pid)" || echo "PROCESS: DOWN"
  grep "|MONITORING|" "$e.log" | tail -n 1
  grep -n -E "\-ERROR +:" "$e.log" | tail -n 5
done
```
Engine lists: standard higher env = `PRISMA_<ENV> PRISMA_<ENV>_LIVE PRISMA_<ENV>_SNAP PRISMA_<ENV>_WHATIF` (4); CIF = `PRISMA_CIF_MAIN PRISMA_CIF_LIVE PRISMA_CIF_SNAP` (3). Verified working on ACC `dwhdas26` 2026-10-12.

**DEV is the exception — it is multi-host.** DEV instances are distributed across `dwhdsri01`/`dwhdsri02`/`dwhdsri03` (users `sri1`/`sri2`/`sri3`), while `/infa_files/.../Logs` is a **shared filesystem**. Consequences for the morning check:
- `ps -ef` on one host only lists that host's processes, so instances running on the other two hosts show as **false DOWN**. Run the Part A inventory on **all three** DEV hosts and combine.
- Because logs are shared, a **recent `|MONITORING|` line is the reliable cross-host liveness signal** — if an instance marked DOWN locally still has a monitoring heartbeat from moments ago, it is alive on another host, not down.
- Only treat a DEV instance as genuinely down when it is absent from `ps` on **all three** hosts **and** its log monitoring has gone stale.

## Retention notes

The DOU reports daily application-log archives kept for five days in DEV/ACC/SIT and seven days in SIMU/PROD. It reports four rollover files for start logs and 30 for JDBC logs, while Netty logs have no configured retention in the described setup. JDBC logs may be on-demand and absent when not enabled.

Separately, the DOU describes Control-M log archival jobs processing files up to a 200-day threshold into designated archive locations. This is a separate archive/housekeeping process from active log rotation; verify actual retention and archive destinations for the environment.

## Control-M schedule groups

The DOU identifies two schedule groups:
- `00_ENGINE_RESTART`: engine restarts, post-restart status checks, and maintenance such as log archiving.
- `01_WEEKLY_CLEANUP`: scheduled database cleanup of obsolete PRISMA data.

### Restart and status jobs
The DOU lists paired restart and status-check jobs for Main, Live, Snap, What-If, and FlushQueue. Each restart job is connected to a corresponding status check by Control-M conditions. Success/failure notifications go to the configured PRISMA alarm destination.

| Component | Restart job | Verification job |
|-----------|-------------|------------------|
| Main | `PRISMA_ENGINE_RESTART_MAIN` | `STATUS_CHECK_MAIN` |
| Live | `PRISMA_ENGINE_RESTART_LIVE` | `STATUS_CHECK_LIVE` |
| Snap | `PRISMA_ENGINE_RESTART_SNAP` | `STATUS_CHECK_SNAP` |
| What-If | `PRISMA_ENGINE_RESTART_WHATIF` | `STATUS_CHECK_WHATIF` |
| FlushQueue | `PRISMA_ENGINE_RESTART_FLUSHQUEUE` | `STATUS_CHECK_FLUSHQUEUE` |

The DOU's Control-M status colors are: Ended OK = green, Ended Not OK = red, Executing = yellow, Wait Condition = blue, Wait Schedule = white, Wait Resource/Host = purple, Held/Disabled = gray.

### Log archive jobs
The DOU names `LOG_MAINTENANCE` as the entry point and lists `PRISMA_LOG_ARCHIVE`, `OTCFX_LOG_ARCHIVE`, `STX2BDWH_LOG_ARCHIVE`, and `STX2CMS_LOG_ARCHIVE` for application log archiving.

### Weekly cleanup
The DOU lists `WEEKLY_RISKFACTOR_CLEANUP`, which calls stored procedure `SP_CLEANUP_RISKFACTOR` to invalidate and remove obsolete risk-factor data. This can modify data; never recommend running manually without owner confirmation, impact review, and approved change control.
