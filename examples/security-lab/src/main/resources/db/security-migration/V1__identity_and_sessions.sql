CREATE TABLE sec_departments (
    id INT PRIMARY KEY,
    name VARCHAR(40) NOT NULL
);
CREATE TABLE sec_users (
    id INT PRIMARY KEY,
    username VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    department_id INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sec_user_owner FOREIGN KEY(id) REFERENCES data_owners(id),
    CONSTRAINT fk_sec_user_department FOREIGN KEY(department_id) REFERENCES sec_departments(id)
);
CREATE TABLE sec_roles (
    id INT PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE
);
CREATE TABLE sec_permissions (
    id INT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE
);
CREATE TABLE sec_user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY(user_id,role_id),
    FOREIGN KEY(user_id) REFERENCES sec_users(id),
    FOREIGN KEY(role_id) REFERENCES sec_roles(id)
);
CREATE TABLE sec_role_permissions (
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY(role_id,permission_id),
    FOREIGN KEY(role_id) REFERENCES sec_roles(id),
    FOREIGN KEY(permission_id) REFERENCES sec_permissions(id)
);
CREATE TABLE sec_sessions (
    id CHAR(36) PRIMARY KEY,
    user_id INT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT NULL,
    FOREIGN KEY(user_id) REFERENCES sec_users(id)
);
CREATE TABLE sec_refresh_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    expires_at BIGINT NOT NULL,
    consumed_at BIGINT NULL,
    revoked_at BIGINT NULL,
    FOREIGN KEY(session_id) REFERENCES sec_sessions(id),
    INDEX ix_sec_refresh_session(session_id)
);
CREATE TABLE sec_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id INT NULL,
    event_code VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NULL,
    request_id VARCHAR(64) NOT NULL,
    occurred_at BIGINT NOT NULL
);
