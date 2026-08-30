DO $$
DECLARE
  d int;
  n int;
  sid text;
  oid bigint;
  pid int;
  price numeric;
  ts timestamp;
  pids int[] := ARRAY[1, 9, 10, 15, 19, 27, 31, 3];
BEGIN
  FOR d IN 1..2 LOOP
    FOR n IN 1..4 LOOP
      sid := gen_random_uuid()::text;
      ts := date_trunc('day', NOW()) - (d || ' days')::interval
            + make_interval(hours => 12 + n, mins => 15);
      INSERT INTO sessions (id, table_id, started_at, closed_at, status)
      VALUES (sid, 1, ts - interval '35 minutes', ts + interval '20 minutes', 'CLOSED');
      pid := pids[1 + ((d + n) % array_length(pids, 1))];
      SELECT p.price INTO price FROM products p WHERE p.id = pid;
      INSERT INTO orders (session_id, table_id, status, subtotal, total, created_at, updated_at)
      VALUES (sid, 1, 'PAID', price * 2, price * 2, ts, ts + interval '18 minutes')
      RETURNING id INTO oid;
      INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total, status, created_at)
      VALUES (oid, pid, 2, price, price * 2, 'ACTIVE', ts);
    END LOOP;
  END LOOP;
END $$;
