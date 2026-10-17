# Mall Lost and Found Portal

A Java Swing desktop application for visitors and mall security staff. It uses JDBC with MySQL to manage found items, visitor lost-item reports, claim requests, verification, and archiving.

The first screen is a home page with Visitor and Security/Admin entry points. Visitor forms and each security view have a Back button. The security dashboard has dedicated found-item, claim-request, and lost-item-report screens; the request screens include refresh buttons and visible result counts.

The Swing interface uses a consistent navy-and-teal theme, spacious card-style layouts, styled forms and tables, and distinct primary and secondary actions.

The visitor start page offers task cards for finding, reporting, and claiming items. The security dashboard shows live totals for active items, pending claims, and visitor reports, with quick-action cards. Security tables support column sorting, text filtering, and status filtering for claims.

## Requirements

- Java 17 or later
- A running MySQL 8 or later server and an account with permission to create databases and tables
- MySQL Connector/J JAR in the existing `lib` folder

The project uses the standard `src` and `lib` layout configured in `.vscode/settings.json`. On startup, it creates the database named in the JDBC URL and application tables if they do not already exist. The MySQL account must have `CREATE DATABASE` and table-creation permissions.

## Configure MySQL

MySQL Server 8.4.9 is installed on Windows by the project setup. To configure the local server, open an **Administrator PowerShell** in the project folder and run:

```powershell
.\setup-local-mysql.ps1
```

The script initializes a local MySQL service bound to `127.0.0.1`, prompts for a root password without storing it in the project, and sets the service to start automatically. It refuses to overwrite an existing MySQL service or non-empty data directory.

Run `.\run.ps1` from PowerShell. It compiles the app, asks for a MySQL JDBC URL (defaulting to the local server), asks for your MySQL login with a secure credential prompt, and asks for an initial security username/password. For a remote server, enter its host and port in the JDBC URL, for example `jdbc:mysql://db.example.com:3306/mall_lost_found?serverTimezone=UTC`. The initial security credentials are used only when the database has no staff accounts. Choose your own passwords; they are not written to source files.

If you launch `App.java` directly from VS Code instead, the app opens dialogs to collect MySQL credentials when `MALL_DB_USER` is not set. It asks for the first security account after it connects and creates the tables. These dialogs avoid requiring environment variables in VS Code's Java launch configuration.

Optional connection override:

```powershell
$env:MALL_DB_URL = "jdbc:mysql://localhost:3306/mall_lost_found?serverTimezone=UTC"
```

On startup, the application creates the required tables and prompts for the initial staff account if no staff account exists. The password is stored as a salted PBKDF2-HMAC-SHA256 hash. Once an account exists, startup will not replace its password.

## Compile and run

To start the app using the interactive credential prompts:

```powershell
.\run.ps1
```

Alternatively, configure the environment variables `MALL_DB_USER`, `MALL_DB_PASSWORD`, `MALL_ADMIN_USERNAME`, and `MALL_ADMIN_PASSWORD`, then compile and run using the MySQL Connector/J JAR in `lib`.

## Features

### Visitor portal

- Search active found items by keyword, category, and location.
- Submit a lost-item report with contact details.
- Submit an ownership claim for a found-item ID with proof details.

### Security / admin

- Staff-only sign-in.
- Register found items with auto-generated IDs and ownership-verification details.
- Review found items, visitor lost-item reports, and claim requests.
- Update item status to `Unclaimed`, `Under Verification`, or `Claimed`.
- Approve or reject pending claims. Approval marks the item as claimed.
- Archive claimed items; archived records remain in the database but disappear from active lists.

All database writes use JDBC `PreparedStatement`s. The forms validate required fields and report invalid item IDs with custom `EmptyFieldException` and `InvalidItemException` exceptions.
