# Parallax Database Layer (SQLite)

This module provides the **SQLite-based persistence layer** for the Parallax Parking System.  
It replaces the original in-memory repositories with a durable, file-based SQLite implementation.

The database layer implements the two core interfaces defined by the backend:

- `UserRepository`
- `VehicleRepository`

Once integrated into `HttpServerApp`, all backend features (Authentication / Vehicles / Blacklist / Query / Admin) automatically use SQLite without requiring any changes to HTTP handlers or frontend code.

---

## Features

### 1. Persistent storage using SQLite

- All user and vehicle data is saved into a local SQLite database file (e.g. `parallax.db`).
- Data persists across server restarts.
- No external database server is required.

### 2. Full implementation of backend repository interfaces

#### `SQLiteUserRepository implements UserRepository`

Supports:

- `Optional<User> findByUsername(String username)`
- `Optional<User> findByEmailOrPhone(String identifier)`
- `List<User> findAll()`
- `void save(User user)`
- `void deleteByUsername(String username)`

#### `SQLiteVehicleRepository implements VehicleRepository`

Supports:

- `List<Vehicle> findByOwner(String owner)`
- `Optional<Vehicle> findByOwnerAndPlate(String owner, String plate)`
- `Optional<Vehicle> findByPlateNormalized(String normalizedPlate)`
- `List<Vehicle> findAll()`
- `void save(Vehicle vehicle)`
- `void deleteByOwnerAndPlate(String owner, String plate)`
- `void updateBlacklist(String owner, String plate, boolean blacklisted)`

### 3. Automatic schema initialization

On startup, the module automatically creates the required tables if they do not exist:

- `users`
- `vehicles`

This ensures the project can run on any machine without manual database setup.

---

## Database Schema

### Table: `users`

The `users` table stores user account information.

| Column            | Type    | Notes                               |
|-------------------|---------|-------------------------------------|
| `username`        | TEXT    | PRIMARY KEY, unique user identifier |
| `country`         | TEXT    |                                     |
| `first_name`      | TEXT    |                                     |
| `last_name`       | TEXT    |                                     |
| `birth_month`     | INTEGER |                                     |
| `birth_day`       | INTEGER |                                     |
| `birth_year`      | INTEGER |                                     |
| `email`           | TEXT    |                                     |
| `password`        | TEXT    | encrypted by backend                |
| `phone_country`   | TEXT    |                                     |
| `phone`           | TEXT    |                                     |
| `contact_method`  | TEXT    | preferred contact channel           |

> Note: password hashing / validation is handled by the backend; this module simply stores the value it receives.

---

### Table: `vehicles`

The `vehicles` table stores vehicle information registered by users.

| Column                      | Type    | Notes                                                  |
|-----------------------------|---------|--------------------------------------------------------|
| `owner`                     | TEXT    | NOT NULL, FOREIGN KEY → `users.username`              |
| `license_number`            | TEXT    | NOT NULL, original plate string                       |
| `license_number_normalized` | TEXT    | NOT NULL, uppercase + whitespace removed              |
| `make`                      | TEXT    | vehicle manufacturer                                   |
| `model`                     | TEXT    | vehicle model                                         |
| `year`                      | INTEGER | model year                                            |
| `blacklisted`               | INTEGER | 0 or 1, indicates whether the vehicle is blacklisted  |

Typical constraint (depending on implementation):

```sql
PRIMARY KEY (owner, license_number)
