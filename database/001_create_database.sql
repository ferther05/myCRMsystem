-- CRM dedicated database bootstrap.
-- This script creates only crm_system and never drops an existing database.
CREATE DATABASE IF NOT EXISTS crm_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE crm_system;

SELECT DATABASE() AS selected_database,
       @@version AS mysql_version,
       @@character_set_database AS database_charset,
       @@collation_database AS database_collation;
