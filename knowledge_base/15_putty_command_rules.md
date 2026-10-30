# PuTTY / Shell Command Rules and Catalog

Single source of truth for which shell commands ReStat issues over PuTTY and the rules that govern them. The **rules** and the **primitives** (`ps`/`grep`/`tail`/`cat`) are shared by RCA and health-check work, but the **concrete command sets differ** — see [Breadth vs. depth](#breadth-vs-depth), [Health-check command set](#health-check-command-set), and [RCA command set](#rca-command-set).

> **Operational caution:** This is a reference for *read-only* diagnostics, not authorization to change anything. ReStat does not connect to servers or run commands itself — it provides commands; the user runs them on the confirmed host and pastes back sanitized output. Any state-changing action requires the approval gate in [State-changing commands](#state-changing-commands--reference-only).

## Hard rules (apply to every command, RCA or health check)

1. **Read-only by default.** Only inspect/search/list: `ps`, `grep`, `tail`, `head`, `cat`, `free`, directory listing. Never delete, truncate, rotate, move, rewrite, restart, or reconfigure.
2. **Host confirmed first.** Do not issue any command until the environment is known and the user has confirmed the host/engine (see [03_environments.md](03_environments.md)).
3. **Scoped to the confirmed host + engine.** Pick the engine-role log that matches the symptom; do not grep unrelated engine logs by default.
4. **Bounded output.** Prefer exact IDs/signatures with `grep -n -C 5`. Cap initial output to ~30 matching lines per file. Avoid large `tail` dumps (e.g. no 200-line excerpts).
5. **Time-windowed.** Ask for the failure window + timezone and scope the search to it. Do not default to broad tails or assume "today." If timezone is unconfirmed, say so before interpreting timestamps.
6. **Linux shell only** for the remote session. Never give PowerShell syntax for a PuTTY/Linux session.
7. **No secrets.** Log/config lines may carry credentials; ask the user to redact before sharing and never unmask or echo them (`jms.password=***` stays masked).
8. **ReStat does not connect or execute.** It supplies commands; the user runs them.
9. **Diagnosis ≠ remediation.** A successful recovery action does not establish root cause; keep the two separate.
10. **Red marker on state-changing commands.** Any chat block that contains a state-changing command (file edit, `cp`/overwrite, restart/stop/start, deployment, DDL/DML, queue change, `touch <MODEL>.IND`, etc.) must carry a 🔴 marker on the line directly above it. Read-only diagnostic blocks stay unmarked. The marker is in addition to — not a replacement for — the explain-impact-verify-rollback approval gate in [State-changing commands](#state-changing-commands--reference-only).

## Breadth vs. depth

The two modes share the same **primitives** (`ps`, `grep`, `tail`, `head`, `cat`) and the same [Hard rules](#hard-rules-apply-to-every-command-rca-or-health-check), but the **concrete commands differ in practice**: a health check is a broad, no-ID sweep over all engines using the active log only, while RCA is a narrow, ID-/signature-driven dig that often reaches logs a health check never touches (`netty_*`, GC, dated archives, `start_*`).

| Aspect | Health check | RCA |
|--------|--------------|-----|
| Intent | Proactive, current-state | Investigate a specific failure |
| Breadth | Broad sweep across **all** engines | Narrow, one/few role logs |
| Driver | No specific ID — latest monitoring + recent errors | Specific request/transaction ID, error signature, failure window |
| Logs touched | Active `<role>.log` only | Also `netty_*.log`, GC log, `start_*.log`, and **dated archives** `<log>.YYYY-MM-DD` |
| Typical command | Inventory loop + `|MONITORING| tail -1` + `-ERROR : tail -5` | `grep -n -C 5 "<id>"` / `grep -n -C 5 "<signature>"` on the matching log |
| Output cap | A few lines per engine | ~30 matching lines per file, widened only as needed |

## Shared primitives and selection rules

- Primitives: `ps`, `grep` (with `-n`, `-E`, `-F`, `-C`, `-m`), `tail`, `head`, `cat`, `awk` for formatting, directory listing.
- **Select the engine-role log first** (symptom → role): snapshot/resource_status → Snap; store/select for Main/What-If → Main/What-If; Snap/Live inserts → Snap/Live; broker attach/connection → `netty_*` plus the role `.log`.
- **Error-grep hygiene:** match `\-ERROR +:` (the log-level token), never a bare `ERROR`, because the `|MONITORING|` line contains an `ERRORS:` counter that a bare grep falsely matches.
- **Current vs. past:** no timing given → treat as current and search the active `<role>.log`; past occurrence → scope to the dated archive `<log>.YYYY-MM-DD` for that day.

## Health-check command set

> Self-contained mode section. The shared [Hard rules](#hard-rules-apply-to-every-command-rca-or-health-check) and [Shared primitives](#shared-primitives-and-selection-rules) still apply to every command here — this section does not restate them.

Broad, current-state sweep over **all** engines; no specific ID. Swap `<ENV>` prefix / engine list per host (see [03_environments.md](03_environments.md)); DR path uses singular `/infa_files/Scripts/SRI/log`.

**Time span (intake H4) drives the scope.** Set `span` to the user's answer (`30min` / `1hr` / `3hr` / `12hr`); it derives a `cutoff` timestamp, and both the `-ERROR :` scan and the `|MONITORING|` freshness verdict are bounded to it (replaces the old whole-day `today` scan). See the span → command-window mapping in [00_intake_questions.md](00_intake_questions.md).

### Preferred: formatted, sorted summary table

Human-readable output for routine checks — sorted by engine, aligned columns, with a per-engine `VERDICT`. Read-only (`ps`/`grep`/`cut`/`awk`/`date`/`printf`/`column`). Set `engines` to the expected list for the host (standard higher env = 4; CIF = 3; DEV = numbered instances + FlushQueue).

```sh
cd /infa_files/Scripts/SRI/log        # DR uses singular .../log; most hosts use .../logs
# H4 span → cutoff. Set span to the user's answer: 30min | 1hr | 3hr | 12hr
span="1hr"
case "$span" in
  30min) cutoff=$(date -d '-30 min'   '+%Y-%m-%dT%H:%M:%S') ;;
  1hr)   cutoff=$(date -d '-1 hour'   '+%Y-%m-%dT%H:%M:%S') ;;
  3hr)   cutoff=$(date -d '-3 hours'  '+%Y-%m-%dT%H:%M:%S') ;;
  12hr)  cutoff=$(date -d '-12 hours' '+%Y-%m-%dT%H:%M:%S') ;;
  *)     cutoff=$(date -d '-1 hour'   '+%Y-%m-%dT%H:%M:%S') ;;
esac
engines="PRISMA_<ENV> PRISMA_<ENV>_LIVE PRISMA_<ENV>_SNAP PRISMA_<ENV>_WHATIF"
{
  printf "ENGINE|STATUS|PID|START|LAST_MONITORING|MON_FRESH|ERRORS_SPAN|VERDICT\n"
  for e in $(echo $engines | tr ' ' '\n' | sort); do
    row=$(ps -ef | grep -F "DsriLogfile=$e.log" | grep -v grep | head -n1)
    if [ -n "$row" ]; then st=UP; pid=$(echo "$row"|awk '{print $2}'); start=$(echo "$row"|awk '{print $5}')
    else st=DOWN; pid="-"; start="-"; fi
    monline=$(grep "|MONITORING|" "$e.log" 2>/dev/null | tail -n1)
    mon=$(echo "$monline" | cut -c1-28); [ -z "$mon" ] && mon="(none)"
    monts=$(echo "$monline" | cut -c1-19)
    fresh=$(awk -v c="$cutoff" -v m="$monts" 'BEGIN{print (m!="" && m>=c)?"YES":"NO"}')
    errs=$(awk -v c="$cutoff" '/-ERROR +:/ && substr($0,1,19) >= c' "$e.log" 2>/dev/null | wc -l); errs=${errs:-0}
    if [ "$st" = UP ] && [ "$fresh" = YES ] && [ "$errs" -eq 0 ]; then v=OK; else v=CHECK; fi
    printf "%s|%s|%s|%s|%s|%s|%s|%s\n" "$e" "$st" "$pid" "$start" "$mon" "$fresh" "$errs" "$v"
  done
} | column -t -s '|'
# 12hr span may cross midnight — if cutoff is an earlier date than today, also scan the prior archive:
#   awk -v c="$cutoff" '/-ERROR +:/ && substr($0,1,19) >= c' "$e.log.$(date -d yesterday +%F)"
```

- Sorted by engine; aligned via `column -t`. If `column` is unavailable on the host, drop the final `| column -t -s '|'` to get pipe-delimited output.
- `ERRORS_SPAN` counts real `-ERROR :` lines **within the H4 span** (timestamp `>= cutoff`), anchored on the log-level token so the `|MONITORING| ERRORS:` counter never false-matches.
- `MON_FRESH` = `YES` only when the latest `|MONITORING|` timestamp is at/after the cutoff; `NO` means the heartbeat is stale **for the chosen span**.
- `VERDICT` = `OK` only when UP + `MON_FRESH=YES` + zero errors in span; otherwise `CHECK`.
- Lexical timestamp compare is valid because the log format is fixed-width ISO `YYYY-MM-DDThh:mm:ss`. For a 12 hr span crossing midnight, add the prior day's archive as shown in the trailing comment.

### Fallback: raw per-engine loop

Use when a plain, unformatted view is wanted or `column`/`date` behave unexpectedly.

```sh
# A) Live process / liveness inventory (labelled by engine via -DsriLogfile)
ps -ef | grep -E "PRISMA_<ENV>" | grep java | grep -v grep | \
  awk '{ eng="?"; for(i=1;i<=NF;i++) if($i ~ /DsriLogfile=/) eng=$i; print $1,$2,$5,$7,eng }'
# columns: USER  PID  STIME(start)  TIME(cpu)  -DsriLogfile=<engine>.log

# B) Per-engine: latest heartbeat + real ERROR lines within the H4 span
cutoff=$(date -d '-1 hour' '+%Y-%m-%dT%H:%M:%S')   # set per H4 span: -30 min | -1 hour | -3 hours | -12 hours
for e in <engine-log-basenames>; do
  echo "===== $e ====="
  pid=$(ps -ef | grep -F "DsriLogfile=$e.log" | grep -v grep | awk '{print $2}' | tr '\n' ' ')
  [ -n "$pid" ] && echo "PROCESS: UP (pid $pid)" || echo "PROCESS: DOWN"
  grep "|MONITORING|" "$e.log" | tail -n 1          # compare its timestamp to $cutoff: at/after = fresh for the span
  awk -v c="$cutoff" '/-ERROR +:/ && substr($0,1,19) >= c' "$e.log" | tail -n 30
done
```

Extended-depth (reference; only when evidence points to resource pressure, kept bounded):

See [Extended checks (7–12)](#extended-checks-712) below for the full commands and a formatted summary.

### Extended checks (7–12)

Run when the basic table shows `CHECK`, or on a weekly cadence. Per-engine; use the PID/engine from the basic inventory. Paths: most hosts `/infa_files/Scripts/SRI/logs`; **DR singular `/infa_files/Scripts/SRI/log`**. All read-only.

#### Preferred: formatted extended summary table

Sorted, aligned, per-engine roll-up of memory / threads / GC / broker / startup with a `VERDICT`. Handles `/proc` permission-denied gracefully (`n/a`).

```sh
cd /infa_files/Scripts/SRI/log        # DR singular; else .../logs
engines="PRISMA_<ENV> PRISMA_<ENV>_LIVE PRISMA_<ENV>_SNAP PRISMA_<ENV>_WHATIF"
span="1hr"                                   # H4 span: 30min | 1hr | 3hr | 12hr
case "$span" in
  30min) cutoff=$(date -d '-30 min'   '+%Y-%m-%dT%H:%M:%S') ;;
  1hr)   cutoff=$(date -d '-1 hour'   '+%Y-%m-%dT%H:%M:%S') ;;
  3hr)   cutoff=$(date -d '-3 hours'  '+%Y-%m-%dT%H:%M:%S') ;;
  12hr)  cutoff=$(date -d '-12 hours' '+%Y-%m-%dT%H:%M:%S') ;;
  *)     cutoff=$(date -d '-1 hour'   '+%Y-%m-%dT%H:%M:%S') ;;
esac
{
  printf "ENGINE|VmHWM|THREADS|GC_STALLS|NETTY_ERR|STARTUP_ERR*|VERDICT\n"
  for e in $(echo $engines | tr ' ' '\n' | sort); do
    pid=$(ps -ef | grep -F "DsriLogfile=$e.log" | grep -v grep | head -n1 | awk '{print $2}')
    if [ -z "$pid" ]; then printf "%s|-|-|-|-|-|DOWN\n" "$e"; continue; fi
    hwm=$(awk '/VmHWM/{printf "%.0f MB",$2/1024}' /proc/$pid/status 2>/dev/null); [ -z "$hwm" ] && hwm="n/a"
    thr=$(ls /proc/$pid/task 2>/dev/null | wc -l); [ "$thr" = 0 ] && thr="n/a"
    gcs=$(grep -c "Allocation Stall" "${e}_gc.log" 2>/dev/null); gcs=${gcs:-0}   # GC log uses a bracketed [ISO] prefix, so not span-filtered here (ref 14: count is the stat label, not events)
    net=$(awk -v c="$cutoff" '/Failed to connect to remote at|error while listening to amqp/ && substr($0,1,19) >= c' "netty_${e}.log" 2>/dev/null | wc -l); net=${net:-0}
    sup=$(awk -v c="$cutoff" '/-ERROR +:/ && substr($0,1,19) >= c' "start_${e}.log" 2>/dev/null | wc -l); sup=${sup:-0}   # bounded to H4 span
    if [ "$gcs" -eq 0 ] && [ "$net" -eq 0 ]; then v=OK; else v=CHECK; fi
    printf "%s|%s|%s|%s|%s|%s|%s\n" "$e" "$hwm" "$thr" "$gcs" "$net" "$sup" "$v"
  done
} | column -t -s '|'
# host memory check (run once): CHECK if MemAvailable < 10 GB (current baseline)
avail_kb=$(awk '/MemAvailable/{print $2}' /proc/meminfo)
avail_gb=$(awk -v k="$avail_kb" 'BEGIN{printf "%.1f", k/1048576}')
memv=$(awk -v k="$avail_kb" 'BEGIN{print (k/1048576 < 10) ? "CHECK" : "OK"}')
printf "HOST_MEM_AVAILABLE: %s GB  (threshold 10 GB)  VERDICT: %s\n" "$avail_gb" "$memv"
free -h
```

- `VmHWM` = peak resident memory (converted to MB); `n/a` if `/proc/<pid>/status` is permission-denied (engines run as `infa64`/group `STX_<ENV>`). Informational — does not drive the verdict.
- **`NETTY_ERR` and `STARTUP_ERR*` are bounded to the H4 span** (timestamp `>= cutoff`), consistent with the basic table. `GC_STALLS` is a full-log count because the GC log line begins with a bracketed `[ISO]` prefix (offset differs from the engine logs); treat it per [14_sri_configuration_reference.md](14_sri_configuration_reference.md) — the raw count is the stat label, not real stalls.
- **Per-engine `VERDICT`** is driven by **`GC_STALLS` + `NETTY_ERR` + process state**: `CHECK` if either count is non-zero or the process is `DOWN`; else `OK`.
- **`STARTUP_ERR*` is informational only.** Even within the span, start logs accumulate errors across restarts — investigate it only right after a known restart (scope to the latest startup).
- **Host memory `VERDICT=CHECK` when `MemAvailable < 10 GB`** (current agreed baseline; adjust as hosts change).
- The `substr($0,1,19)` compare assumes the line begins with an ISO `YYYY-MM-DDThh:mm:ss` timestamp; for a log with a different prefix, adjust the offset. Drop the final `| column -t -s '|'` if `column` is unavailable.

#### Individual commands (drill-down)

```sh
# Set the cutoff once per H4 span (used by 11 and 12): -30 min | -1 hour | -3 hours | -12 hours
cutoff=$(date -d '-1 hour' '+%Y-%m-%dT%H:%M:%S')

# 7. Free memory (host-level, run once)
free -h ;  grep MemAvailable /proc/meminfo

# 8. Per-process memory (per engine)
grep -E "VmHWM|VmRSS|VmSize" /proc/<PID>/status

# 9. Thread counts (per engine)
ls /proc/<PID>/task | wc -l                 # live count
cd /infa_files/Scripts/SRI/scripts ; ./getThreadCounter.sh   # project script (+ getThreadCountLog.txt)

# 10. GC / allocation stalls (per engine) — real stalls have non-zero ms (GC log uses [ISO] prefix, not span-filtered)
grep -n "Allocation Stall" PRISMA_<ENGINE>_gc.log | tail -n 20
grep -m1 "Version:" PRISMA_<ENGINE>_gc.log   # confirms actual JDK

# 11. Broker / reply path (per engine, Netty) — bounded to H4 span
awk -v c="$cutoff" '/Failed to connect to remote at|error while listening to amqp/ && substr($0,1,19) >= c' netty_<ENGINE>.log | tail -n 30

# 12. Startup health (per engine, after a restart) — bounded to H4 span
awk -v c="$cutoff" '/-ERROR +:/ && substr($0,1,19) >= c' start_<ENGINE>.log | tail -n 30
```

## RCA command set

> Self-contained mode section. The shared [Hard rules](#hard-rules-apply-to-every-command-rca-or-health-check) and [Shared primitives](#shared-primitives-and-selection-rules) still apply to every command here — this section does not restate them.

Narrow, driven by a specific ID / signature / failure window; reaches logs the health check does not. Unlike the health check, RCA scopes to the **failure window** the user gives (intake RCA #2), not the H4 span.

```sh
# Targeted context around a specific request/transaction ID
grep -n -C 5 "<requestID>" <role>.log

# Context around a specific error signature
grep -n -C 5 "InvalidDestinationException" <role>.log

# Broker / reply-path problems
grep -n -C 5 "Failed to connect to remote at" netty_<ENGINE>.log

# Past incident: search the dated archive for that day
grep -n -C 5 "<id-or-signature>" <role>.log.YYYY-MM-DD

# Availability / startup problems
grep -n -E "\-ERROR +:" start_<ENGINE>.log | head -n 30

# GC / allocation stalls (only when resource pressure is suspected)
grep -n "Allocation Stall" PRISMA_<ENGINE>_gc.log | head -n 30
```

See [08_sri_general_operations.md](08_sri_general_operations.md), [12_sri_logs_controlm.md](12_sri_logs_controlm.md), and [14_sri_configuration_reference.md](14_sri_configuration_reference.md) for context on these.

## State-changing commands — reference only

These appear in the knowledge base but are **never** run or recommended for execution without environment/version verification **and** explicit operator approval. Explain the change, its impact, verification, and rollback first; ReStat never executes them. **Always prefix a block that contains any of these with a 🔴 marker on the line above it** (Hard rule 10).

```sh
./restart.sh restart PRISMA_<ENGINE>   # engine restart (08 / 11)
./stop.sh PRISMA_<ENGINE>              # stop (08)
./start.sh PRISMA_<ENGINE>            # start (08)
touch <MODEL>.IND                      # indicator file — signals data availability (11)
```

Database access is separate and also never executed by ReStat: provide a read-only `SELECT` only after verifying the environment catalog, fully qualified `OWNER.OBJECT`, and columns; the user runs it. Never query credentials or secrets.
