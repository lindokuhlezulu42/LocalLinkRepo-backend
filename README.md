# LocalLink backend

Spring Boot API for LocalLink. It provides account registration and login, provider listings, enquiries, and administrator moderation.

## Requirements

- Java 21

Make sure `JAVA_HOME` points to a JDK 21 installation.

Ensure `JAVA_HOME` points to your JDK 21 installation before starting the API.

```powershell
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Confirm the output reports version 21, then run the commands below in the same terminal.

## Run locally

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`. By default, it uses an H2 database stored under `data/`; this local database is ignored by Git.

Run backend tests with:

```powershell
.\mvnw.cmd test
```

The LocalLink frontend development server proxies API requests to this service. Its default origin is `http://localhost:5173`.

## Configuration

### PostgreSQL with pgAdmin 4

The PostgreSQL profile is configured for the local server at `localhost:5432`, database `locallink_database`, and role `postgres`. The PostgreSQL server must be running.

1. In pgAdmin, connect to your local PostgreSQL server.
2. Right-click **Databases** and select **Create > Database…**.
3. Enter `locallink_database` as the database name and `postgres` as the owner, then save.
4. In a PowerShell terminal opened in the `backend` folder, set the database password and start the API:

```powershell
$securePassword = Read-Host "PostgreSQL password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:DATABASE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $env:SPRING_PROFILES_ACTIVE = "postgresql"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    java -version
.\mvnw.cmd spring-boot:run
```

PowerShell prompts for the password without displaying it. These settings apply only to that terminal session; do not commit real passwords. Spring Boot creates or updates the application tables when it connects.

To use different connection details, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` in the same terminal. Set `FRONTEND_ORIGIN` when the frontend is served from a different origin.

The default configuration continues to use the local H2 database unless the `postgresql` profile is activated.

To bootstrap an administrator on startup, configure `LOCALLINK_ADMIN_EMAIL` and `LOCALLINK_ADMIN_PASSWORD`; the password must be at least 12 characters. For HTTPS deployments, set `SESSION_COOKIE_SECURE=true`. Do not commit credentials.

The administrator dashboard lists provider listings in every status and system-wide enquiries. It refreshes automatically while the page is visible. Providers can edit a published listing and submit proposed changes directly. The last approved version remains public while an administrator reviews the proposal. Approval publishes the update; rejection requires a reason and keeps the existing approved version public. Only administrator accounts can access the management endpoints.

Administrators can mark enquiries as read and permanently delete them after review. The API prevents deletion of unread enquiries. Providers can request permanent removal of their own listings; administrators review those requests before confirming deletion. Confirming permanently deletes the listing and its related enquiries.

### Email verification

New resident and provider accounts must verify their email before signing in. The verification link expires after 10 minutes; the verification page can request a replacement link. Existing accounts are kept verified when the database schema is updated.

Gmail SMTP defaults are configured in `application.properties` using `smtp.gmail.com:587` and STARTTLS. Set `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` in the environment before starting the API. Keep credentials out of tracked property files and source control.

To supply the App Password in PowerShell for the current terminal session:

```powershell
$securePassword = Read-Host "Gmail App Password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:MAIL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
```

Other SMTP settings can be overridden with environment variables:

- `MAIL_HOST` and `MAIL_PORT` — SMTP server and port
- `MAIL_USERNAME` and `MAIL_PASSWORD` — SMTP credentials, if required
- `MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` — set to `true` when required by the provider
- `MAIL_FROM` — sender address accepted by the SMTP provider
- `FRONTEND_ORIGIN` — frontend origin used to construct the verification link

The default mail host is `localhost` on port `25`; configure a reachable SMTP server to deliver messages. If sending fails, registration returns an error rather than creating an unusable account. Never commit SMTP credentials.

Password recovery email delivery is not configured.
