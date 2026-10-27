# Intake Questions

## Step 0 — Ask what the user needs first (triage menu)

At the start of a new conversation, before any intake questions, ask the user what they need and offer the options as a numbered list:

> How can I help you today?
> 1. **Daily routine health check** — proactive check that the SRI engines are up and healthy.
> 2. **Root cause analysis (RCA)** — investigate a reported problem, error, or failure.

Then branch on the answer:
- **Option 1 / health check** → use the **Health-check / daily-routine intake** set.
- **Option 2 / RCA** → use the **Incident / RCA intake** set.

Do not default to RCA. If the user's very first message already makes the goal obvious (e.g. they paste an error log → RCA; they say "morning check / are the engines up" → health check), skip the menu and go straight to the matching set. If it is genuinely ambiguous, show the menu and wait for their choice before asking any intake questions.

## The two intake sets

- **Incident / RCA intake** — use when the user reports a problem, error, failure, or asks for root-cause help. Questions in the first table below.
- **Health-check / daily-routine intake** — use when the user wants a proactive routine check of engine health (e.g. "morning check", "daily health check", "are the engines up"), not an incident. Questions in the second table.

## Incident / RCA intake

Ask these BEFORE analysing a log or root cause. Ask them as separate, ordered questions (this logical order), not one merged blob. Required questions must be answered before analysis begins; optional ones may be skipped or supplied later. Skip any the user already answered.

| # | Order | Question | Required | Used for |
|---|-------|----------|----------|----------|
| 1 | First | In which environment are you seeing the issue? (DEV, SIT1, SIT2, ACC/Acceptance, SIMU, DR, PROD) | Yes | Environment column; drives the server/engine and schema lookup in `03_environments.md` |
| 2 | Second | When is it happening — right now, earlier today, or on past day(s)? Give the date(s), approximate time, and timezone. | Yes | Scopes the log/AWR search window and decides live log vs dated archive (e.g. `PRISMA_PROD.log` vs `.log.YYYY-MM-DD`) |
| 3 | Third | Server confirmation — I look up the documented host/engine for that environment and ask you to confirm it is the affected one (or you tell me the server if you already know it). | Yes (confirm) | Environment column (appended as `ENV (server)`); which engine logs to read |
| 4 | Last | Do you have any error log or message from the client side? (paste it if available) | Optional | Error signature, model/business object, correlation (transaction/request) IDs |

Optional follow-ups (ask only if not already clear; may be skipped):

| # | Question | Required | Used for |
|---|----------|----------|----------|
| 5 | Is there a ticket number? | Optional | Ticket column |
| 6 | Which model / business object or message type is involved (StoreRequest vs BOQL/select)? | Optional | Narrows the engine role and metadata objects to inspect |

Do not start the investigation until #1–#3 are answered and the server is confirmed.

## Health-check / daily-routine intake

Use this set when the user wants a proactive routine health check, not an incident RCA. A health check is current-state by nature, so it does **not** need a client error message or a past failure window. The goal is to pick the correct host(s) and engine list, then run the read-only liveness + monitoring + error-scan workflow in [12_sri_logs_controlm.md](12_sri_logs_controlm.md). Ask the required questions before running any check; skip any the user already answered.

| # | Order | Question | Required | Used for |
|---|-------|----------|----------|----------|
| H1 | First | Which environment(s) do you want to health-check? (DEV, SIT1, SIT2, ACC/Acceptance, SIMU, DR, PROD) | Yes | Host and engine-list lookup in `03_environments.md` |
| H2 | Second | Scope — standard engines only, CIF only, or both? (ACC/PROD have a separate CIF host; DEV/SIT may have none) | Yes | Selects the engine list: standard higher env = 4 (`Main/Live/Snap/What-If`), CIF = 3 (`Main/Snap/Live`), DEV = numbered instances present + `FlushQueue` |
| H3 | Third | Server confirmation — I look up the documented host(s) for that environment/scope and ask you to confirm (DEV is multi-host `dwhdsri01/02/03`; ACC standard = `dwhaas01`, ACC CIF = `dwhaas03-cif`). | Yes (confirm) | Which host(s) to run the read-only check on |
| H4 | Last | Reference "now" time and timezone (so I can judge whether the latest `|MONITORING|` heartbeat is recent). Default: current time. | Optional | Interpreting monitoring recency / staleness |

Optional follow-ups (ask only if not already clear; may be skipped):

| # | Question | Required | Used for |
|---|----------|----------|----------|
| H5 | Depth — basic (process liveness via `ps` + latest `|MONITORING|` + fresh `-ERROR :` scan) or extended (also memory/GC/thread counts, queue/DB connectivity)? Default: basic. | Optional | Which read-only commands to provide |
| H6 | Expected engine count / any engines knowingly stopped for maintenance? | Optional | Avoids a false DOWN verdict for an intentionally stopped engine |

Do not start the check until H1–H3 are answered and the host(s) are confirmed. Keep everything read-only (`ps`/`grep`/`tail` only — no restarts or config changes). If the check surfaces a real problem (DOWN engine, stale/zero monitoring, fresh errors), switch to the incident/RCA intake and collect the failure window, timezone, and any client error before deeper investigation.
