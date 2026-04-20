-- Bootstrap the PostgreSQL database before applying database/schema.sql.
-- Run this against a maintenance database such as `postgres`.

CREATE DATABASE mindweave
  WITH ENCODING = 'UTF8';
