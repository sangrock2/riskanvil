# Demo Seed Guide

This guide populates sample data for one existing user so you can validate major features without manual input.

## What It Seeds

- Watchlist + tags + notes
- Market cache + report history
- Analysis history
- Backtest history
- Portfolio + positions + dividends
- Screener presets
- Chatbot conversations/messages
- Price alerts
- Usage dashboard logs
- Paper trading accounts plus sample positions/orders for empty paper accounts

## Prerequisites

1. `postgres` service is running via Docker Compose.
2. `.env` contains `POSTGRES_PASSWORD` and `POSTGRES_DB`.
3. The target user already exists in `users` table (register once first).

## Run

```powershell
pwsh -File scripts/seed_demo_data.ps1 -UserEmail "your@email.com"
```

Or seed all existing users at once:

```powershell
pwsh -File scripts/seed_demo_data.ps1 -AllUsers
```

## Run Without Docker (direct psql CLI)

If your PostgreSQL server is already running locally, you can run seed directly with `psql`:

```bash
psql -h localhost -U postgres -d stock_ai -c "SELECT id, email FROM users;"
psql -h localhost -U postgres -d stock_ai -v target_user_id=1 -f scripts/sql/seed_demo_data.sql
```

Replace `1` with your real user id.

## Notes

- Seed marker: `demo-seed-v1`
- Script is idempotent for tagged seed rows.
- Existing non-seed data is not deleted.
- Existing paper-trading positions/orders are preserved instead of being overwritten.
- If PostgreSQL is not running, the script auto-runs `docker compose up -d postgres`.
- Disable auto-start with:

```powershell
pwsh -File scripts/seed_demo_data.ps1 -UserEmail "your@email.com" -NoAutoStart
```

## Troubleshooting

1. If you see `Docker engine is not running`:
   - Start Docker Desktop first.
2. If you see `Target user not found`:
   - Register/login once to create the user, then run seed again.
