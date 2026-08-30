-- =====================================================
-- V1: Schema inicial del sistema de restaurante
-- =====================================================

-- CATÁLOGO
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(500),
    active BOOLEAN DEFAULT true,
    allergens TEXT[] DEFAULT '{}',
    version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE ingredients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    unit VARCHAR(30) NOT NULL DEFAULT 'unidad',
    allergens TEXT[] DEFAULT '{}',
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE product_ingredients (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id),
    quantity_required DECIMAL(10,3) NOT NULL DEFAULT 1,
    ingredient_type VARCHAR(20) NOT NULL DEFAULT 'BASE',
    extra_price DECIMAL(10,2) DEFAULT 0,
    CONSTRAINT chk_ingredient_type CHECK (ingredient_type IN ('BASE','REMOVABLE','OPTIONAL')),
    UNIQUE(product_id, ingredient_id)
);

CREATE TABLE substitution_rules (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    original_ingredient_id BIGINT NOT NULL REFERENCES ingredients(id),
    substitute_ingredient_id BIGINT NOT NULL REFERENCES ingredients(id),
    price_diff DECIMAL(10,2) DEFAULT 0,
    active BOOLEAN DEFAULT true
);

-- INVENTARIO
CREATE TABLE inventory (
    ingredient_id BIGINT PRIMARY KEY REFERENCES ingredients(id),
    quantity_available DECIMAL(12,3) NOT NULL DEFAULT 0,
    min_threshold DECIMAL(12,3) NOT NULL DEFAULT 10,
    updated_at TIMESTAMP DEFAULT NOW(),
    version INT DEFAULT 0
);

CREATE TABLE inventory_log (
    id BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id),
    delta DECIMAL(12,3) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

-- MESAS Y SESIONES
CREATE TABLE restaurant_tables (
    id BIGSERIAL PRIMARY KEY,
    table_number INT NOT NULL UNIQUE,
    name VARCHAR(50),
    capacity INT DEFAULT 4,
    active BOOLEAN DEFAULT true
);

CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    table_id BIGINT NOT NULL REFERENCES restaurant_tables(id),
    started_at TIMESTAMP DEFAULT NOW(),
    closed_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    CONSTRAINT chk_session_status CHECK (status IN ('ACTIVE','CLOSED'))
);

-- PEDIDOS
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL REFERENCES sessions(id),
    table_id BIGINT NOT NULL REFERENCES restaurant_tables(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    subtotal DECIMAL(10,2) DEFAULT 0,
    total DECIMAL(10,2) DEFAULT 0,
    notes TEXT,
    idempotency_key VARCHAR(100) UNIQUE,
    version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT chk_order_status CHECK (status IN ('DRAFT','PENDING','IN_PREPARATION','READY','DELIVERED','PAYMENT_REQUESTED','PAID','CLOSED','CANCELLED'))
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    modifiers JSONB DEFAULT '[]',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT chk_item_status CHECK (status IN ('ACTIVE','CANCELLED'))
);

-- CONVERSACIÓN
CREATE TABLE conversation_turns (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    session_id VARCHAR(36) NOT NULL REFERENCES sessions(id),
    role VARCHAR(15) NOT NULL,
    content TEXT NOT NULL,
    audio_url VARCHAR(500),
    action_plan JSONB,
    apply_result JSONB,
    idempotency_key VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT chk_turn_role CHECK (role IN ('USER','ASSISTANT','SYSTEM'))
);

-- AUDITORÍA
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    payload JSONB,
    actor VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP
);

-- STAFF / AUTH
CREATE TABLE staff (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    pin_hash VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT chk_staff_role CHECK (role IN ('ADMIN','KITCHEN','WAITER'))
);

-- EMBEDDINGS PARA RAG
CREATE TABLE catalog_embeddings (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ÍNDICES
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_product_ingredients_product ON product_ingredients(product_id);
CREATE INDEX idx_orders_session ON orders(session_id);
CREATE INDEX idx_orders_table ON orders(table_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_conversation_turns_session ON conversation_turns(session_id);
CREATE INDEX idx_audit_events_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_outbox_unprocessed ON outbox_events(processed_at) WHERE processed_at IS NULL;
CREATE INDEX idx_sessions_table ON sessions(table_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_catalog_embeddings_entity ON catalog_embeddings(entity_type, entity_id);

-- Índice vectorial para RAG (HNSW)
CREATE INDEX idx_catalog_embeddings_vector ON catalog_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
