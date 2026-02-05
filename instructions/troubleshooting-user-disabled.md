# Troubleshooting: “Login failed: User is disabled”

## Symptom
When trying to sign in, the UI/API returns an error like:
- `User is disabled`

In Spring Security terms, this means `UserDetails.isEnabled()` returned `false`.

## Why it occurs (root cause)
This project recently added an `active` flag to the `users` table and wired it into Spring Security:
- In code: `UserDetailsImpl.isEnabled()` returns the user’s `active` value.
- If `active` is `false` (or effectively false), Spring Security blocks authentication and surfaces “User is disabled”.

This commonly happens right after adding the column because **existing rows in MySQL may have `active = 0` or `NULL`**, depending on how Hibernate created/altered the schema (`spring.jpa.hibernate.ddl-auto=update` does not reliably backfill defaults for existing data).

So: older accounts created before the `active` column existed can end up disabled by default.

## How to confirm
### Option A — Check in MySQL
Run one of these (depending on your column type):

```sql
SELECT id, username, active
FROM users
WHERE username = 'mani';
```

If `active` is `0` (or `NULL`), the account will be blocked.

### Option B — Check via Admin API
If you can log in as an ADMIN:
- `GET /api/admin/users`
- Look for the user and see the `active` field.

## Fix options

### Fix 1 (fastest): Enable the user in MySQL
Enable one user:

```sql
UPDATE users
SET active = 1
WHERE username = 'mani';
```

Enable all disabled/NULL users (use carefully):

```sql
UPDATE users
SET active = 1
WHERE active IS NULL OR active = 0;
```

Then try signing in again.

If you did this and it still says disabled, double-check you are updating the same database your app is using (`spring.datasource.url`) and the same username.

### Fix 2: Enable the user via Admin API
If you have an ADMIN token:

1. List users
   - `GET /api/admin/users`
2. Activate the user
   - `PATCH /api/admin/users/{userId}/active`
   - Body:

```json
{ "active": true }
```

If you cannot log in as ADMIN because there is no admin account (or it is also disabled), use Fix 4 to bootstrap an admin.

### Fix 3 (recommended long-term): Ensure DB default is enabled
Hibernate’s `ddl-auto=update` may not add a proper default for existing deployments. You can enforce a DB-level default:

```sql
ALTER TABLE users
  MODIFY active TINYINT(1) NOT NULL DEFAULT 1;
```

Then backfill old rows:

```sql
UPDATE users SET active = 1 WHERE active IS NULL;
```

(If your column is `BIT(1)` instead of `TINYINT(1)`, use `DEFAULT b'1'`.)

### Fix 4 (project feature): Bootstrap an ADMIN account (dev/local)
This project includes an optional bootstrap that creates/enables an admin account on app startup.

Add these to `src/main/resources/application.properties` (choose your own values):

```properties
app.bootstrap.admin.enabled=true
app.bootstrap.admin.username=admin
app.bootstrap.admin.email=admin@example.com
app.bootstrap.admin.password=Admin@12345
```

Restart the application.

Then:
- Sign in as that admin
- Call `GET /api/admin/users`
- Activate your user with `PATCH /api/admin/users/{userId}/active` and body `{ "active": true }`

## Notes / prevention
- For production-style systems, use Flyway/Liquibase migrations instead of relying on `ddl-auto=update`.
- After adding new non-null security flags (like `active`), always backfill existing user rows.

## Where this behavior is implemented
- `User.active` field is in: `src/main/java/com/securefilesharing/entity/User.java`
- Security check is in: `src/main/java/com/securefilesharing/security/services/UserDetailsImpl.java` (`isEnabled()`)
- Admin enable/disable endpoint is in: `src/main/java/com/securefilesharing/controller/AdminUserController.java`
