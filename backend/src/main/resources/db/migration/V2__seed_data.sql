-- =====================================================
-- V2: Datos semilla — Menú de restaurante mexicano
-- =====================================================

-- CATEGORÍAS
INSERT INTO categories (id, name, description, display_order) VALUES
(1, 'Entradas', 'Para comenzar tu experiencia', 1),
(2, 'Sopas y Caldos', 'Calientes y reconfortantes', 2),
(3, 'Tacos', 'Nuestros tacos tradicionales', 3),
(4, 'Platos Fuertes', 'Los favoritos de la casa', 4),
(5, 'Ensaladas', 'Opciones frescas y ligeras', 5),
(6, 'Postres', 'El toque dulce final', 6),
(7, 'Bebidas', 'Refrescantes y deliciosas', 7),
(8, 'Bebidas Alcohólicas', 'Para brindar', 8);

-- INGREDIENTES
INSERT INTO ingredients (id, name, unit, allergens) VALUES
-- Proteínas
(1, 'Pollo', 'gramos', '{}'),
(2, 'Res', 'gramos', '{}'),
(3, 'Cerdo', 'gramos', '{}'),
(4, 'Camarón', 'gramos', '{mariscos}'),
(5, 'Pescado blanco', 'gramos', '{pescado}'),
-- Lácteos
(6, 'Queso Oaxaca', 'gramos', '{lácteos}'),
(7, 'Queso fresco', 'gramos', '{lácteos}'),
(8, 'Crema ácida', 'ml', '{lácteos}'),
-- Vegetales
(9, 'Cebolla', 'gramos', '{}'),
(10, 'Tomate', 'gramos', '{}'),
(11, 'Lechuga', 'gramos', '{}'),
(12, 'Aguacate', 'unidad', '{}'),
(13, 'Chile jalapeño', 'gramos', '{}'),
(14, 'Cilantro', 'gramos', '{}'),
(15, 'Limón', 'unidad', '{}'),
(16, 'Chile poblano', 'unidad', '{}'),
(17, 'Frijoles refritos', 'gramos', '{}'),
(18, 'Arroz', 'gramos', '{}'),
(19, 'Elote', 'gramos', '{}'),
(20, 'Nopal', 'gramos', '{}'),
(21, 'Champiñones', 'gramos', '{}'),
(22, 'Espinaca', 'gramos', '{}'),
-- Tortillas / Bases
(23, 'Tortilla maíz', 'unidad', '{gluten}'),
(24, 'Tortilla harina', 'unidad', '{gluten,lácteos}'),
(25, 'Totopos', 'gramos', '{gluten}'),
(26, 'Pan bolillo', 'unidad', '{gluten}'),
-- Salsas
(27, 'Salsa roja', 'ml', '{}'),
(28, 'Salsa verde', 'ml', '{}'),
(29, 'Guacamole', 'gramos', '{}'),
(30, 'Pico de gallo', 'gramos', '{}'),
(31, 'Mole poblano', 'ml', '{frutos_secos,ajonjolí}'),
(32, 'Salsa chipotle', 'ml', '{}'),
-- Condimentos
(33, 'Sal', 'gramos', '{}'),
(34, 'Pimienta', 'gramos', '{}'),
(35, 'Aceite vegetal', 'ml', '{}'),
(36, 'Ajo', 'gramos', '{}'),
-- Bebidas
(37, 'Agua mineral', 'ml', '{}'),
(38, 'Jugo de naranja', 'ml', '{}'),
(39, 'Horchata', 'ml', '{}'),
(40, 'Jamaica', 'ml', '{}'),
(41, 'Refresco cola', 'ml', '{}'),
(42, 'Cerveza artesanal', 'ml', '{}'),
(43, 'Tequila', 'ml', '{}'),
(44, 'Mezcal', 'ml', '{}'),
-- Postres
(45, 'Flan napolitano', 'unidad', '{lácteos,huevo}'),
(46, 'Churro', 'unidad', '{gluten,lácteos}'),
(47, 'Chocolate caliente', 'ml', '{lácteos}'),
(48, 'Cajeta', 'ml', '{lácteos}'),
(49, 'Canela', 'gramos', '{}'),
(50, 'Azúcar', 'gramos', '{}'),
-- Extras
(51, 'Huevo', 'unidad', '{huevo}'),
(52, 'Chorizo', 'gramos', '{}'),
(53, 'Tocino', 'gramos', '{}'),
(54, 'Piña', 'gramos', '{}'),
(55, 'Papas fritas', 'gramos', '{}');

-- PRODUCTOS (~30 items)
INSERT INTO products (id, category_id, name, description, price, allergens) VALUES
-- Entradas (1-5)
(1, 1, 'Guacamole con Totopos', 'Aguacate fresco con tomate, cebolla, cilantro y limón. Servido con totopos crujientes.', 89.00, '{}'  ),
(2, 1, 'Quesadilla de Queso Oaxaca', 'Tortilla de harina rellena de queso Oaxaca fundido. Se puede agregar champiñones o flor de calabaza.', 65.00, '{gluten,lácteos}'),
(3, 1, 'Nachos Supremos', 'Totopos con frijoles, queso fundido, jalapeños, crema, guacamole y pico de gallo.', 120.00, '{gluten,lácteos}'),
(4, 1, 'Elote en Vaso', 'Granos de elote con mayonesa, queso fresco, chile y limón.', 55.00, '{lácteos}'),
(5, 1, 'Sopes de Frijol', 'Tres sopes de maíz con frijoles refritos, crema, queso fresco y salsa.', 75.00, '{gluten,lácteos}'),

-- Sopas (6-8)
(6, 2, 'Sopa Azteca', 'Caldo de tomate con tortilla frita, aguacate, queso, crema y chile pasilla.', 85.00, '{gluten,lácteos}'),
(7, 2, 'Pozole Rojo', 'Caldo rojo con maíz cacahuazintle, cerdo, lechuga, rábano, cebolla y orégano.', 110.00, '{}'),
(8, 2, 'Crema de Elote', 'Crema suave de elote con un toque de chile poblano.', 75.00, '{lácteos}'),

-- Tacos (9-14)
(9, 3, 'Tacos al Pastor (3 pzas)', 'Cerdo adobado con piña, cebolla y cilantro en tortilla de maíz.', 85.00, '{gluten}'),
(10, 3, 'Tacos de Bistec (3 pzas)', 'Bistec de res asado con cebolla y cilantro en tortilla de maíz.', 90.00, '{gluten}'),
(11, 3, 'Tacos de Pollo (3 pzas)', 'Pollo a la plancha con guacamole en tortilla de maíz.', 80.00, '{gluten}'),
(12, 3, 'Tacos de Camarón (3 pzas)', 'Camarones empanizados con pico de gallo y salsa chipotle.', 120.00, '{gluten,mariscos}'),
(13, 3, 'Tacos de Nopal (3 pzas)', 'Nopal asado con queso fresco, cebolla y salsa verde. Opción vegetariana.', 70.00, '{gluten,lácteos}'),
(14, 3, 'Tacos Gobernador (3 pzas)', 'Camarón con queso Oaxaca, chile poblano en tortilla de harina.', 130.00, '{gluten,lácteos,mariscos}'),

-- Platos Fuertes (15-21)
(15, 4, 'Enchiladas Verdes', 'Tres tortillas rellenas de pollo bañadas en salsa verde, con crema y queso fresco.', 125.00, '{gluten,lácteos}'),
(16, 4, 'Enchiladas de Mole', 'Tres tortillas rellenas de pollo bañadas en mole poblano con ajonjolí.', 135.00, '{gluten,frutos_secos,ajonjolí}'),
(17, 4, 'Chile Relleno de Queso', 'Chile poblano relleno de queso Oaxaca, capeado, con salsa roja y arroz.', 140.00, '{gluten,lácteos,huevo}'),
(18, 4, 'Mole Poblano con Pollo', 'Pieza de pollo bañada en mole poblano tradicional con arroz y tortillas.', 155.00, '{gluten,frutos_secos,ajonjolí}'),
(19, 4, 'Arrachera', 'Corte de res a la parrilla con guacamole, frijoles charros, nopales y tortillas.', 195.00, '{gluten}'),
(20, 4, 'Pescado a la Veracruzana', 'Filete de pescado blanco en salsa de tomate con aceitunas, alcaparras y chiles.', 170.00, '{pescado}'),
(21, 4, 'Burrito de Carne Asada', 'Tortilla de harina grande rellena de carne asada, frijoles, arroz, queso y pico de gallo.', 135.00, '{gluten,lácteos}'),

-- Ensaladas (22-23)
(22, 5, 'Ensalada César con Pollo', 'Lechuga romana, pollo a la plancha, crutones, queso parmesano y aderezo César.', 110.00, '{gluten,lácteos,huevo}'),
(23, 5, 'Ensalada de Nopal', 'Nopal picado con tomate, cebolla, cilantro, queso fresco y aguacate.', 80.00, '{lácteos}'),

-- Postres (24-26)
(24, 6, 'Flan Napolitano', 'Flan casero de vainilla con caramelo.', 65.00, '{lácteos,huevo}'),
(25, 6, 'Churros con Chocolate (4 pzas)', 'Churros crujientes espolvoreados con canela y azúcar, con chocolate caliente.', 70.00, '{gluten,lácteos}'),
(26, 6, 'Pastel de Tres Leches', 'Bizcocho empapado en tres leches con crema batida y canela.', 75.00, '{gluten,lácteos,huevo}'),

-- Bebidas (27-30)
(27, 7, 'Agua de Horchata', 'Refrescante agua de arroz con canela y vainilla.', 40.00, '{}'),
(28, 7, 'Agua de Jamaica', 'Infusión de flor de jamaica servida fría.', 40.00, '{}'),
(29, 7, 'Jugo de Naranja Natural', 'Jugo de naranja recién exprimido.', 45.00, '{}'),
(30, 7, 'Refresco', 'Coca-Cola, Sprite o Fanta.', 35.00, '{}'),

-- Bebidas Alcohólicas (31-33)
(31, 8, 'Cerveza Artesanal', 'Cerveza local de barril (clara u oscura).', 65.00, '{gluten}'),
(32, 8, 'Margarita', 'Tequila, jugo de limón, triple sec, sal al borde.', 95.00, '{}'),
(33, 8, 'Mezcal Joven', 'Caballito de mezcal joven con naranja y sal de gusano.', 85.00, '{}');

-- PRODUCT_INGREDIENTS (recetas base)
-- Guacamole con Totopos (id=1)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(1, 12, 2, 'BASE', 0),      -- Aguacate x2
(1, 10, 50, 'BASE', 0),     -- Tomate
(1, 9, 30, 'REMOVABLE', 0), -- Cebolla
(1, 14, 10, 'REMOVABLE', 0),-- Cilantro
(1, 15, 1, 'BASE', 0),      -- Limón
(1, 13, 15, 'REMOVABLE', 0),-- Jalapeño
(1, 25, 80, 'BASE', 0);     -- Totopos

-- Quesadilla (id=2)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(2, 24, 1, 'BASE', 0),      -- Tortilla harina
(2, 6, 80, 'BASE', 0),      -- Queso Oaxaca
(2, 21, 50, 'OPTIONAL', 15),-- Champiñones (extra)
(2, 52, 40, 'OPTIONAL', 20);-- Chorizo (extra)

-- Nachos (id=3)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(3, 25, 120, 'BASE', 0),    -- Totopos
(3, 17, 80, 'BASE', 0),     -- Frijoles
(3, 6, 60, 'BASE', 0),      -- Queso Oaxaca
(3, 13, 20, 'REMOVABLE', 0),-- Jalapeños
(3, 8, 30, 'REMOVABLE', 0), -- Crema
(3, 29, 50, 'BASE', 0),     -- Guacamole
(3, 30, 40, 'BASE', 0),     -- Pico de gallo
(3, 1, 60, 'OPTIONAL', 25); -- Pollo (extra)

-- Tacos al Pastor (id=9)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(9, 23, 3, 'BASE', 0),      -- Tortilla maíz x3
(9, 3, 120, 'BASE', 0),     -- Cerdo
(9, 54, 30, 'REMOVABLE', 0),-- Piña
(9, 9, 20, 'REMOVABLE', 0), -- Cebolla
(9, 14, 10, 'REMOVABLE', 0),-- Cilantro
(9, 27, 30, 'BASE', 0);     -- Salsa roja

-- Tacos de Bistec (id=10)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(10, 23, 3, 'BASE', 0),     -- Tortilla maíz x3
(10, 2, 130, 'BASE', 0),    -- Res
(10, 9, 20, 'REMOVABLE', 0),-- Cebolla
(10, 14, 10, 'REMOVABLE', 0),-- Cilantro
(10, 15, 1, 'BASE', 0);     -- Limón

-- Tacos de Pollo (id=11)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(11, 23, 3, 'BASE', 0),     -- Tortilla maíz x3
(11, 1, 120, 'BASE', 0),    -- Pollo
(11, 29, 40, 'BASE', 0),    -- Guacamole
(11, 9, 20, 'REMOVABLE', 0);-- Cebolla

-- Tacos de Camarón (id=12)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(12, 23, 3, 'BASE', 0),     -- Tortilla maíz
(12, 4, 120, 'BASE', 0),    -- Camarón
(12, 30, 40, 'BASE', 0),    -- Pico de gallo
(12, 32, 20, 'BASE', 0);    -- Salsa chipotle

-- Enchiladas Verdes (id=15)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(15, 23, 3, 'BASE', 0),     -- Tortilla maíz
(15, 1, 100, 'BASE', 0),    -- Pollo
(15, 28, 80, 'BASE', 0),    -- Salsa verde
(15, 8, 30, 'REMOVABLE', 0),-- Crema
(15, 7, 40, 'REMOVABLE', 0),-- Queso fresco
(15, 18, 80, 'OPTIONAL', 0);-- Arroz (acompañamiento)

-- Mole Poblano (id=18)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(18, 1, 200, 'BASE', 0),    -- Pollo
(18, 31, 120, 'BASE', 0),   -- Mole poblano
(18, 18, 100, 'BASE', 0),   -- Arroz
(18, 23, 3, 'BASE', 0);     -- Tortillas

-- Arrachera (id=19)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(19, 2, 250, 'BASE', 0),    -- Res
(19, 29, 50, 'BASE', 0),    -- Guacamole
(19, 17, 80, 'BASE', 0),    -- Frijoles
(19, 20, 60, 'REMOVABLE', 0),-- Nopales
(19, 23, 3, 'BASE', 0);     -- Tortillas

-- Burrito (id=21)
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(21, 24, 1, 'BASE', 0),     -- Tortilla harina
(21, 2, 150, 'BASE', 0),    -- Res
(21, 17, 60, 'BASE', 0),    -- Frijoles
(21, 18, 60, 'BASE', 0),    -- Arroz
(21, 6, 50, 'REMOVABLE', 0),-- Queso
(21, 30, 40, 'REMOVABLE', 0),-- Pico de gallo
(21, 8, 20, 'REMOVABLE', 0);-- Crema

-- Bebidas simples
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(27, 39, 500, 'BASE', 0),   -- Horchata
(28, 40, 500, 'BASE', 0),   -- Jamaica
(29, 38, 350, 'BASE', 0),   -- Jugo naranja
(30, 41, 355, 'BASE', 0),   -- Refresco
(31, 42, 500, 'BASE', 0),   -- Cerveza
(32, 43, 60, 'BASE', 0),    -- Tequila
(32, 15, 2, 'BASE', 0),     -- Limón
(33, 44, 60, 'BASE', 0);    -- Mezcal

-- Postres
INSERT INTO product_ingredients (product_id, ingredient_id, quantity_required, ingredient_type, extra_price) VALUES
(24, 45, 1, 'BASE', 0),     -- Flan
(25, 46, 4, 'BASE', 0),     -- Churros
(25, 47, 200, 'BASE', 0),   -- Chocolate caliente
(25, 49, 5, 'BASE', 0),     -- Canela
(25, 50, 10, 'BASE', 0);    -- Azúcar

-- SUSTITUCIONES
INSERT INTO substitution_rules (product_id, original_ingredient_id, substitute_ingredient_id, price_diff) VALUES
(9, 23, 24, 5),   -- Taco pastor: maíz → harina (+$5)
(10, 23, 24, 5),  -- Taco bistec: maíz → harina
(11, 23, 24, 5),  -- Taco pollo: maíz → harina
(2, 6, 7, 0),     -- Quesadilla: Oaxaca → fresco (sin costo)
(15, 1, 6, 10),   -- Enchiladas: pollo → queso (+$10)
(21, 2, 1, -10);  -- Burrito: res → pollo (-$10)

-- INVENTARIO INICIAL (cantidades generosas para demo)
INSERT INTO inventory (ingredient_id, quantity_available, min_threshold) VALUES
(1, 5000, 500),   -- Pollo 5kg
(2, 5000, 500),   -- Res
(3, 4000, 400),   -- Cerdo
(4, 3000, 300),   -- Camarón
(5, 2000, 300),   -- Pescado
(6, 3000, 300),   -- Queso Oaxaca
(7, 2000, 200),   -- Queso fresco
(8, 2000, 200),   -- Crema
(9, 3000, 300),   -- Cebolla
(10, 3000, 300),  -- Tomate
(11, 2000, 200),  -- Lechuga
(12, 50, 10),     -- Aguacate (unidades)
(13, 1000, 100),  -- Jalapeño
(14, 500, 50),    -- Cilantro
(15, 100, 20),    -- Limón (unidades)
(16, 30, 5),      -- Chile poblano
(17, 3000, 300),  -- Frijoles
(18, 3000, 300),  -- Arroz
(19, 2000, 200),  -- Elote
(20, 1000, 100),  -- Nopal
(21, 1000, 100),  -- Champiñones
(22, 500, 50),    -- Espinaca
(23, 200, 30),    -- Tortilla maíz (unidades)
(24, 100, 20),    -- Tortilla harina
(25, 3000, 300),  -- Totopos
(26, 50, 10),     -- Bolillo
(27, 5000, 500),  -- Salsa roja
(28, 5000, 500),  -- Salsa verde
(29, 3000, 300),  -- Guacamole
(30, 3000, 300),  -- Pico de gallo
(31, 3000, 300),  -- Mole
(32, 2000, 200),  -- Chipotle
(33, 5000, 100),  -- Sal
(34, 1000, 100),  -- Pimienta
(35, 5000, 500),  -- Aceite
(36, 1000, 100),  -- Ajo
(37, 10000, 1000),-- Agua mineral
(38, 5000, 500),  -- Jugo naranja
(39, 5000, 500),  -- Horchata
(40, 5000, 500),  -- Jamaica
(41, 10000, 1000),-- Refresco
(42, 10000, 1000),-- Cerveza
(43, 3000, 300),  -- Tequila
(44, 3000, 300),  -- Mezcal
(45, 20, 5),      -- Flan (unidades)
(46, 50, 10),     -- Churros (unidades)
(47, 5000, 500),  -- Chocolate
(48, 2000, 200),  -- Cajeta
(49, 500, 50),    -- Canela
(50, 3000, 300),  -- Azúcar
(51, 100, 20),    -- Huevo
(52, 2000, 200),  -- Chorizo
(53, 1000, 100),  -- Tocino
(54, 2000, 200),  -- Piña
(55, 3000, 300);  -- Papas fritas

-- MESAS
INSERT INTO restaurant_tables (id, table_number, name, capacity) VALUES
(1, 1, 'Mesa 1', 4),
(2, 2, 'Mesa 2', 4),
(3, 3, 'Mesa 3', 2),
(4, 4, 'Mesa 4', 6),
(5, 5, 'Mesa 5', 4),
(6, 6, 'Mesa 6', 8),
(7, 7, 'Mesa 7', 4),
(8, 8, 'Mesa 8', 2),
(9, 9, 'Mesa 9', 6),
(10, 10, 'Mesa 10', 4);

-- STAFF (PINs: admin=1234, cocina=5678, mesero=9012)
-- Hashes BCrypt pre-generados
INSERT INTO staff (id, name, pin_hash, role) VALUES
(1, 'Administrador', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN'),
(2, 'Chef Carlos', '$2a$10$ixlPY3AAd4ty1l6E2IsQ9OFZi2ba9ZQE0bP7RFcGIWNhS0tXk5.Qi', 'KITCHEN'),
(3, 'Mesero Juan', '$2a$10$xn3LI/AjqicFYZFruSwve.388SeRLhBc8LMFXQv.Hs4Lxufy.2oD2', 'WAITER'),
(4, 'Mesera María', '$2a$10$xn3LI/AjqicFYZFruSwve.388SeRLhBc8LMFXQv.Hs4Lxufy.2oD2', 'WAITER');

-- Actualizar secuencias
SELECT setval('categories_id_seq', 10);
SELECT setval('products_id_seq', 40);
SELECT setval('ingredients_id_seq', 60);
SELECT setval('restaurant_tables_id_seq', 15);
SELECT setval('staff_id_seq', 10);
