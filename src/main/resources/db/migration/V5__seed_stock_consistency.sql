-- The demo orders were seeded without touching stock. Make the numbers add up the way the app would have left them:
-- open orders (PENDING, PAID) hold reservations; shipped and delivered orders have already left the warehouse.

-- reservations for open orders
UPDATE products p SET reserved_quantity = p.reserved_quantity + s.qty
FROM (SELECT oi.product_id, SUM(oi.quantity) AS qty
      FROM order_items oi JOIN orders o ON o.id = oi.order_id
      WHERE o.status IN ('PENDING', 'CONFIRMED', 'PAID', 'PROCESSING')
      GROUP BY oi.product_id) s
WHERE p.id = s.product_id;

-- shipped / delivered orders: stock gone, sales counted
UPDATE products p SET stock_quantity = p.stock_quantity - s.qty, sales_count = p.sales_count + s.qty
FROM (SELECT oi.product_id, SUM(oi.quantity) AS qty
      FROM order_items oi JOIN orders o ON o.id = oi.order_id
      WHERE o.status IN ('SHIPPED', 'IN_TRANSIT', 'DELIVERED')
      GROUP BY oi.product_id) s
WHERE p.id = s.product_id;
