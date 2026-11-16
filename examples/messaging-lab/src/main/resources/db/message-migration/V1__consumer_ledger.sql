CREATE TABLE processed_events (
  consumer_name VARCHAR(40) NOT NULL,
  event_id CHAR(36) NOT NULL,
  task_key VARCHAR(40) NOT NULL,
  PRIMARY KEY (consumer_name, event_id)
);
CREATE TABLE projection_effects (
  task_key VARCHAR(40) PRIMARY KEY,
  applied_count INT NOT NULL
);
