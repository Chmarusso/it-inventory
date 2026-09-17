CREATE TABLE equipment (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(30) NOT NULL CHECK (type IN ('MAIN_COMPUTER', 'MONITOR', 'KEYBOARD', 'MOUSE')),
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('AVAILABLE', 'RESERVED', 'ASSIGNED')),
    condition_score NUMERIC(3, 2) NOT NULL CHECK (condition_score BETWEEN 0 AND 1),
    purchase_date DATE NOT NULL CHECK (purchase_date <= CURRENT_DATE)
);

CREATE INDEX idx_equipment_state_type ON equipment (state, type);

CREATE TABLE allocation_request (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('RESERVED', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_allocation_request_created_at ON allocation_request (created_at DESC);

CREATE TABLE allocation_policy_slot (
    id BIGSERIAL PRIMARY KEY,
    allocation_request_id BIGINT NOT NULL REFERENCES allocation_request(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    type VARCHAR(30) NOT NULL CHECK (type IN ('MAIN_COMPUTER', 'MONITOR', 'KEYBOARD', 'MOUSE')),
    minimum_condition NUMERIC(3, 2) CHECK (minimum_condition BETWEEN 0 AND 1),
    preferred_brand VARCHAR(100),
    prefer_recent BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (allocation_request_id, position)
);

CREATE INDEX idx_policy_slot_allocation ON allocation_policy_slot (allocation_request_id);

CREATE TABLE allocation_item (
    id BIGSERIAL PRIMARY KEY,
    allocation_request_id BIGINT NOT NULL REFERENCES allocation_request(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id),
    UNIQUE (allocation_request_id, equipment_id)
);

CREATE INDEX idx_allocation_item_allocation ON allocation_item (allocation_request_id);
CREATE INDEX idx_allocation_item_equipment ON allocation_item (equipment_id);

