CREATE TABLE sys_audit_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    resource_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id BIGINT NOT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at BIGINT NOT NULL,
    CONSTRAINT fk_sys_audit_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    INDEX ix_sys_audit_resource (resource_type, resource_id, id),
    INDEX ix_sys_audit_request (request_id)
);

CREATE TABLE sys_menu_revision (
    id INT PRIMARY KEY,
    revision CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
);

INSERT INTO sys_menu_revision(id, revision) VALUES (1, UUID());
