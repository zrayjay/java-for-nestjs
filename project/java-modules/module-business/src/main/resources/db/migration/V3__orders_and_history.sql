CREATE TABLE sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_name VARCHAR(100) NOT NULL,
    total_amount_cents INT NOT NULL CHECK (total_amount_cents BETWEEN 1 AND 999999999),
    owner_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    status ENUM('DRAFT', 'SUBMITTED', 'APPROVED', 'CANCELLED') NOT NULL,
    version INT NOT NULL CHECK (version > 0),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT fk_order_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_order_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    INDEX ix_order_department (department_id, id),
    INDEX ix_order_owner (owner_id, id)
);

CREATE TABLE sales_order_history (
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action ENUM('CREATE', 'EDIT', 'SUBMIT', 'APPROVE', 'CANCEL') NOT NULL,
    from_status ENUM('DRAFT', 'SUBMITTED', 'APPROVED', 'CANCELLED') NULL,
    to_status ENUM('DRAFT', 'SUBMITTED', 'APPROVED', 'CANCELLED') NOT NULL,
    version INT NOT NULL,
    occurred_at BIGINT NOT NULL,
    PRIMARY KEY (order_id, version),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_history_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id)
);
