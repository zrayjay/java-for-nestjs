CREATE TABLE probe_users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(80) NOT NULL
);

INSERT INTO probe_users (id, name) VALUES (1, 'baseline');
