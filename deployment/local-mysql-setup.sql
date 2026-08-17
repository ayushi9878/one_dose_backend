-- ============================================================================
-- CareFlow - Local MySQL 8 Setup
-- ============================================================================
-- Purpose:
--   Creates the local CareFlow database and a dedicated application user.
--
-- Environment:
--   Windows MySQL 8
--   Host: 127.0.0.1
--   Port: 3307
--
-- IMPORTANT:
--   1. Run this script as a MySQL administrative/root user.
--   2. Replace CHANGE_ME_STRONG_PASSWORD with a strong LOCAL password.
--   3. NEVER commit this file after replacing the placeholder with a real
--      password. Restore the placeholder before committing.
--   4. The CareFlow application must NOT connect as root.
--
-- Docker/GCE note:
--   This script is for LOCAL MySQL only. In Docker/GCE, the application will
--   connect to the MySQL service as:
--       DB_HOST=mysql
--       DB_PORT=3306
--
-- ============================================================================

-- 1. Create the application database.
CREATE DATABASE IF NOT EXISTS careflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Create a dedicated non-root application user.
--    localhost is appropriate for this Windows-local MySQL setup.
CREATE USER IF NOT EXISTS 'careflow'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

-- 3. If the user already exists, this updates its password to the value
--    supplied above. Remove this statement if you intentionally want to
--    preserve an existing local password.
ALTER USER 'careflow'@'localhost'
    IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';

-- 4. Give the application user access ONLY to the CareFlow database.
GRANT ALL PRIVILEGES ON careflow.* TO 'careflow'@'localhost';

FLUSH PRIVILEGES;

-- ============================================================================
-- Verification
-- ============================================================================
-- Run these commands after the setup to verify the database and user.

SHOW DATABASES LIKE 'careflow';

SELECT User, Host
FROM mysql.user
WHERE User = 'careflow';

SHOW GRANTS FOR 'careflow'@'localhost';

-- ============================================================================
-- Optional: verify the application user can access the database.
-- ============================================================================
-- Exit the root/admin session and connect using:
--
--   mysql.exe -h 127.0.0.1 -P 3307 -u careflow -p careflow
--
-- Then run:
--
--   SELECT DATABASE();
--   SELECT VERSION();
--
-- Expected database:
--   careflow
--
-- ============================================================================

-- Local Spring Boot environment should be configured separately, for example:
--
-- DB_HOST=localhost
-- DB_PORT=3307
-- DB_NAME=careflow
-- DB_USERNAME=careflow
-- DB_PASSWORD=<your-local-password>
--
-- Do NOT put the real DB_PASSWORD in this file or commit it to GitHub.
-- ============================================================================