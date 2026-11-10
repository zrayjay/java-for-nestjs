CREATE TABLE tx_lab_entries (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    label VARCHAR(64) NOT NULL
);
CREATE TABLE tx_lab_balances (
    id INT NOT NULL PRIMARY KEY,
    amount INT NOT NULL
);
INSERT INTO tx_lab_balances (id, amount) VALUES (1, 100);
