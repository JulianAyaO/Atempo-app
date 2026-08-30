-- V4: Crear tabla de movimientos de inventario
CREATE TABLE IF NOT EXISTS inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL,
    ingredient_name VARCHAR(255),
    movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('SALIDA','ENTRADA','AJUSTE')),
    quantity_delta NUMERIC(10,3) NOT NULL,
    stock_before NUMERIC(10,3) NOT NULL,
    stock_after NUMERIC(10,3) NOT NULL,
    reference_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inv_movements_ingredient ON inventory_movements(ingredient_id);
CREATE INDEX IF NOT EXISTS idx_inv_movements_reference ON inventory_movements(reference_id);
