-- Prices were stored as DOUBLE PRECISION (binary floating point), which cannot
-- represent most decimal amounts exactly. Convert them to exact decimals.
--
-- Existing rows are converted in place by the column type change: every stored
-- value came from a two-decimal price, so rounding to scale 2 gives back the
-- original amount (6399.99 stays 6399.99) - nothing is lost.
-- NUMERIC(12,2) holds up to 9,999,999,999.99.
-- SET DATA TYPE is standard SQL, so the same file runs on PostgreSQL and H2.

ALTER TABLE product     ALTER COLUMN current_price SET DATA TYPE NUMERIC(12, 2);
ALTER TABLE price_point ALTER COLUMN price         SET DATA TYPE NUMERIC(12, 2);
