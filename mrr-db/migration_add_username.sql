-- Migration: Add username column to access_log (for existing SQLite databases)
-- Run this manually if your database was created before the column was added to schema.sql
ALTER TABLE main.access_log ADD COLUMN username TEXT;
