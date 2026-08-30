DO $$
DECLARE
  h int;
  sid text;
  oid bigint;
  pid int;
  price numeric;
  ts timestamp;
BEGIN
  FOREACH h IN ARRAY ARRAY[17, 18, 19, 20] LOOP
    sid := gen_random_uuid()::text;
    ts := date_trunc('day', NOW()) + make_interval(hours => h, mins => 20);
    INSERT INTO sessions (id, table_id, started_at, closed_at, status)
    VALUES (sid, 1, ts - interval '25 minutes', ts + interval '15 minutes', 'CLOSED');
    pid := CASE h WHEN 17 THEN 9 WHEN 18 THEN 19 WHEN 19 THEN 3 ELSE 32 END;
    SELECT p.price INTO price FROM products p WHERE p.id = pid;
    INSERT INTO orders (session_id, table_id, status, subtotal, total, created_at, updated_at)
    VALUES (sid, 1, 'PAID', price, price, ts, ts + interval '12 minutes')
    RETURNING id INTO oid;
    INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total, status, created_at)
    VALUES (oid, pid, 1, price, price, 'ACTIVE', ts);
  END LOOP;
END $$;
