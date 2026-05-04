-- V6: Update price column in workshops to BIGINT
ALTER TABLE workshops ALTER COLUMN price TYPE BIGINT USING price::bigint;