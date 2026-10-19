# Intake Questions

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
