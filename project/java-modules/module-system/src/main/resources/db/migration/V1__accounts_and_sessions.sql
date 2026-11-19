CREATE TABLE sys_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    department_id BIGINT NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii NOT NULL,
    status ENUM('ACTIVE', 'DISABLED') NOT NULL DEFAULT 'ACTIVE',
    credential_version BIGINT NOT NULL DEFAULT 1,
    platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sys_user_department FOREIGN KEY (department_id) REFERENCES sys_department(id)
);

CREATE TABLE sys_admin_guard (
    id INT PRIMARY KEY
);

CREATE TABLE sys_session_family (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_version BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    CONSTRAINT fk_sys_session_family_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE sys_refresh_session (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consumed_at BIGINT NULL,
    CONSTRAINT fk_sys_refresh_session_family FOREIGN KEY (family_id) REFERENCES sys_session_family(id)
);

INSERT INTO sys_admin_guard(id) VALUES (1);
INSERT INTO sys_department(code, name) VALUES ('operations', 'Operations');
