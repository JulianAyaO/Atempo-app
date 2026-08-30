-- Demo floor: limpia mesas, deja la 1 libre y llena 2-10 en distintos estados.
-- También genera ventas de los últimos 14 días para Reportes.

BEGIN;

DELETE FROM conversation_turns;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM sessions;

-- Ventas históricas (sesiones cerradas) para gráficas
DO $$
DECLARE
  d int;
  n int;
  sid text;
  oid bigint;
  tid bigint;
  pids int[] := ARRAY[1,3,9,10,11,15,16,19,21,24,25,27,28,31,32];
  pid int;
  qty int;
  price numeric;
  v_total numeric;
  ts timestamp;
  i int;
  st text;
BEGIN
  FOR d IN 0..13 LOOP
    FOR n IN 1..5 LOOP
      tid := 1 + ((d + n) % 10);
      ts := date_trunc('day', NOW()) - (d || ' days')::interval
            + make_interval(hours => 11 + (n % 8), mins => n * 6);
      sid := gen_random_uuid()::text;
      INSERT INTO sessions (id, table_id, started_at, closed_at, status)
      VALUES (sid, tid, ts - interval '45 minutes', ts + interval '25 minutes', 'CLOSED');

      st := CASE WHEN n = 5 AND d IN (1, 4, 8) THEN 'CANCELLED'
                 WHEN d = 0 AND n <= 2 THEN 'CLOSED'
                 ELSE 'PAID' END;

      INSERT INTO orders (session_id, table_id, status, subtotal, total, created_at, updated_at)
      VALUES (sid, tid, st, 0, 0, ts, ts + interval '20 minutes')
      RETURNING id INTO oid;

      v_total := 0;
      FOR i IN 1..(1 + (n % 3)) LOOP
        pid := pids[1 + ((d + n + i) % array_length(pids, 1))];
        qty := 1 + (i % 2);
        SELECT p.price INTO price FROM products p WHERE p.id = pid;
        INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total, status, created_at)
        VALUES (oid, pid, qty, price, price * qty, 'ACTIVE', ts);
        v_total := v_total + price * qty;
      END LOOP;
      UPDATE orders SET subtotal = v_total, total = v_total WHERE id = oid;
    END LOOP;
  END LOOP;
END $$;

-- Helper: crea sesión activa + pedido con productos
CREATE OR REPLACE FUNCTION tmp_demo_order(p_table bigint, p_status text, p_product_ids int[])
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  sid text := gen_random_uuid()::text;
  oid bigint;
  pid int;
  price numeric;
  v_total numeric := 0;
BEGIN
  INSERT INTO sessions (id, table_id, started_at, status)
  VALUES (sid, p_table, NOW() - interval '25 minutes', 'ACTIVE');

  INSERT INTO orders (session_id, table_id, status, subtotal, total, created_at, updated_at)
  VALUES (sid, p_table, p_status, 0, 0, NOW() - interval '20 minutes', NOW() - interval '5 minutes')
  RETURNING id INTO oid;

  FOREACH pid IN ARRAY p_product_ids LOOP
    SELECT p.price INTO price FROM products p WHERE p.id = pid;
    INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total, status, created_at)
    VALUES (oid, pid, 1, price, price, 'ACTIVE', NOW() - interval '18 minutes');
    v_total := v_total + price;
  END LOOP;
  UPDATE orders SET subtotal = v_total, total = v_total WHERE id = oid;
END $$;

-- Mesa 1: libre (sin sesión)
-- Mesa 2: ocupada sin pedido
INSERT INTO sessions (id, table_id, started_at, status)
VALUES (gen_random_uuid()::text, 2, NOW() - interval '8 minutes', 'ACTIVE');

-- Mesa 3: borrador
SELECT tmp_demo_order(3, 'DRAFT', ARRAY[1, 9]);
-- Mesa 4: pendiente (cocina)
SELECT tmp_demo_order(4, 'PENDING', ARRAY[3, 28]);
-- Mesa 5: en preparación
SELECT tmp_demo_order(5, 'IN_PREPARATION', ARRAY[15, 27]);
-- Mesa 6: listo
SELECT tmp_demo_order(6, 'READY', ARRAY[19, 31]);
-- Mesa 7: entregado
SELECT tmp_demo_order(7, 'DELIVERED', ARRAY[18, 24]);
-- Mesa 8: cuenta solicitada
SELECT tmp_demo_order(8, 'PAYMENT_REQUESTED', ARRAY[21, 25, 32]);
-- Mesa 9: pagado (sesión sigue abierta)
SELECT tmp_demo_order(9, 'PAID', ARRAY[12, 32]);
-- Mesa 10: otro pedido en cocina
SELECT tmp_demo_order(10, 'PENDING', ARRAY[9, 10, 11, 30]);

DROP FUNCTION tmp_demo_order(bigint, text, int[]);

INSERT INTO inventory_movements (ingredient_id, ingredient_name, movement_type, quantity_delta, stock_before, stock_after, reference_id, created_at)
SELECT i.id, i.name, 'SALIDA', 40, 200, 160, 'demo-video', NOW() - (n || ' hours')::interval
FROM ingredients i
CROSS JOIN generate_series(1, 8) AS n
WHERE i.id IN (1, 12, 23, 25, 27, 31, 6, 3);

SELECT setval('orders_id_seq', COALESCE((SELECT MAX(id) FROM orders), 1));
SELECT setval('order_items_id_seq', COALESCE((SELECT MAX(id) FROM order_items), 1));

COMMIT;
