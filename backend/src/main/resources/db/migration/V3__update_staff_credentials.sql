-- V3: Actualizar credenciales de staff
-- admin / 1234, cocina / 5678, mesero / 9012

DELETE FROM staff;

INSERT INTO staff (id, name, pin_hash, role) VALUES
(1, 'admin', '$2b$10$9JB6e9qMdqjwztppVjBmzuQbXCsVAUbdZj31JuuOBynkiHclBPKvO', 'ADMIN'),
(2, 'cocina', '$2b$10$W4BI64mFJsHaqeM7Rf8ZWu2wMbSMnvYpbqZVrdtc9J5qNmrUFccCq', 'KITCHEN'),
(3, 'mesero', '$2b$10$WSsNM6Y3Y/SEbJoQBAr48uVM8LnJqkoyG2hOSDcdwf8AliLwWXQFG', 'WAITER');

SELECT setval('staff_id_seq', 10);
