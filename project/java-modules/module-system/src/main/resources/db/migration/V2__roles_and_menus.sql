CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
);

CREATE TABLE sys_role_grant (
    role_id BIGINT NOT NULL,
    permission VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope ENUM('OWN', 'DEPARTMENT', 'ALL') NOT NULL,
    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_sys_role_grant_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
);

CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NULL,
    label VARCHAR(100) NOT NULL,
    route_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
    permission VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id),
    INDEX ix_sys_menu_parent_order (parent_id, sort_order, id)
);

INSERT INTO sys_role(code, name) VALUES ('ORDER_CLERK', '录单员'), ('ORDER_REVIEWER', '部门复核员');

INSERT INTO sys_role_grant(role_id, permission, scope)
SELECT r.id, g.permission, g.scope
FROM sys_role r
JOIN (
    SELECT 'ORDER_CLERK' AS code, 'orders:read' AS permission, 'OWN' AS scope
    UNION ALL SELECT 'ORDER_CLERK', 'orders:create', 'OWN'
    UNION ALL SELECT 'ORDER_CLERK', 'orders:edit', 'OWN'
    UNION ALL SELECT 'ORDER_CLERK', 'orders:submit', 'OWN'
    UNION ALL SELECT 'ORDER_CLERK', 'orders:cancel', 'OWN'
    UNION ALL SELECT 'ORDER_REVIEWER', 'orders:read', 'DEPARTMENT'
    UNION ALL SELECT 'ORDER_REVIEWER', 'orders:approve', 'DEPARTMENT'
    UNION ALL SELECT 'ORDER_REVIEWER', 'orders:cancel', 'DEPARTMENT'
) g ON g.code = r.code;

INSERT INTO sys_menu(label, route_key, permission, sort_order)
VALUES ('订单', 'orders', 'orders:read', 10),
       ('用户', 'users', 'system:admin', 20),
       ('角色', 'roles', 'system:admin', 30),
       ('菜单', 'menus', 'system:admin', 40),
       ('审计', 'audit', 'system:admin', 50);
