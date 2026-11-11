CREATE TABLE data_owners (
    id INT PRIMARY KEY,
    name VARCHAR(40) NOT NULL
);
INSERT INTO data_owners(id, name) VALUES (1,'Alice'),(2,'Bob'),(3,'Cara');
CREATE TABLE data_tasks (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    priority TINYINT NOT NULL,
    owner_id INT NOT NULL DEFAULT 1,
    CONSTRAINT ck_data_priority CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT fk_data_owner FOREIGN KEY(owner_id) REFERENCES data_owners(id)
);
