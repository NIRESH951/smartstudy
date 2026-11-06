-- =====================================================================
-- SmartStudy - create the empty database and the application user
--
-- Run ONCE as the MySQL root user in MySQL Workbench:
--   File > Open SQL Script... > this file > Execute (lightning icon).
--
-- Do NOT create the tables yourself: when the backend starts, Flyway
-- creates them from backend/src/main/resources/db/migration.
--
-- Replace 'ChangeMe_Str0ng!' with your own password and use the same value
-- for the DB_PASSWORD environment variable of the backend.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smartstudy
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'smartstudy'@'localhost' IDENTIFIED BY 'ChangeMe_Str0ng!';

GRANT ALL PRIVILEGES ON smartstudy.* TO 'smartstudy'@'localhost';
FLUSH PRIVILEGES;

-- Check: should list the database (empty until the backend has started once)
SHOW DATABASES LIKE 'smartstudy';
