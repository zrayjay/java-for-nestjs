CREATE TABLE outbox_tasks (
  task_key VARCHAR(40) PRIMARY KEY,
  event_id CHAR(36) NOT NULL UNIQUE
);
CREATE TABLE message_outbox (
  event_id CHAR(36) PRIMARY KEY,
  task_key VARCHAR(40) NOT NULL,
  lease_token CHAR(36) NULL,
  lease_until TIMESTAMP(6) NULL,
  sent_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_outbox_task FOREIGN KEY (event_id) REFERENCES outbox_tasks(event_id)
);
