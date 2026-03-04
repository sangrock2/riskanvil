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
- Paper trading accounts/positions/orders (safe insert)

## Prerequisites

1. `mysql` service is running via Docker Compose.
2. `.env` contains `MYSQL_ROOT_PASSWORD` and `MYSQL_DATABASE`.
3. The target user already exists in `users` table (register once first).

## Run

```powershell
pwsh -File scripts/seed_demo_data.ps1 -UserEmail "your@email.com"
```

Or seed all existing users at once:

```powershell
pwsh -File scripts/seed_demo_data.ps1 -AllUsers
```

## Run Without Docker (direct mysql CLI)

If your MySQL server is already running locally and you are logged in via `mysql` client, you can run seed directly:

```sql
USE stock_ai;
SELECT id, email FROM users;
SET @target_user_id := 1;
SOURCE C:/Users/Sw103/Desktop/stock-ai/scripts/sql/seed_demo_data.sql;
```

Replace `1` with your real user id.

## Notes

- Seed marker: `demo-seed-v1`
- Script is idempotent for seeded rows.
- Existing non-seed data is not deleted.
- If MySQL is not running, the script auto-runs `docker compose up -d mysql`.
- Disable auto-start with:

```powershell
pwsh -File scripts/seed_demo_data.ps1 -UserEmail "your@email.com" -NoAutoStart
```

## Troubleshooting

1. If you see `Docker engine is not running`:
   - Start Docker Desktop first.
2. If you see `Target user not found`:
   - Register/login once to create the user, then run seed again.
