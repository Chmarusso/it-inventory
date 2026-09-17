-- failure_reason holds an exact per-threshold diagnosis. A 50-slot policy (the API maximum)
-- spread over several thresholds can exceed the original VARCHAR(1000), so the column becomes
-- unbounded text and the application no longer has to truncate what it tells the operator.
ALTER TABLE allocation_request ALTER COLUMN failure_reason TYPE TEXT;
