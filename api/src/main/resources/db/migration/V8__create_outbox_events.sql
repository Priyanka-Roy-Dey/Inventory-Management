-- Events written in the same transaction as the change that caused them (design note §2.5). A publisher sends the
-- rows with no published_at (INV-17). No foreign key: an event stays valid after its item is deleted.
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(50)  NOT NULL,
    aggregate_id BIGINT       NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT outbox_events_event_id_key UNIQUE (event_id)
);

CREATE INDEX outbox_events_unpublished_idx ON outbox_events (id) WHERE published_at IS NULL;
