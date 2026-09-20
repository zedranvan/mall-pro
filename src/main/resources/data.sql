-- ============================================================
-- 测试数据 —— 幂等设计，可重复执行
--
-- 原则：不用固定 ID，不写死序列值
--   1. 显式指定 ID 插入 IDENTITY 列会导致序列不前进，后续新增冲突
--   2. 所以统一用 EXISTS / NOT EXISTS 判重，靠名称关联
--
-- 活动时间：NOW() 前后各留一段，保证执行后活动立即可用
-- ============================================================


-- ------------------------------------------------------------
-- 测试用户：test / 123456
-- BCrypt 哈希（$2a$10$ 开头，Spring Security BCryptPasswordEncoder 可直接校验）
-- ------------------------------------------------------------
INSERT INTO t_user (username, password)
SELECT 'test', '$2a$10$0GArqHZoN4Ied9RkdbwhGuFM9UKeOVrpxnBsMGGx1n2F2RElCPYiC'
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'test');


-- ------------------------------------------------------------
-- 商品
-- ------------------------------------------------------------
INSERT INTO goods (name, price, stock, detail)
SELECT 'iPhone 16 Pro', 8999.00, 1000, '测试商品：手机'
WHERE NOT EXISTS (SELECT 1 FROM goods WHERE name = 'iPhone 16 Pro');

INSERT INTO goods (name, price, stock, detail)
SELECT 'MacBook Pro 14', 14999.00, 500, '测试商品：笔记本电脑'
WHERE NOT EXISTS (SELECT 1 FROM goods WHERE name = 'MacBook Pro 14');


-- ------------------------------------------------------------
-- 秒杀活动
-- ------------------------------------------------------------
INSERT INTO seckill_goods (goods_id, seckill_price, stock_count, start_time, end_time)
SELECT g.id, 4999.00, 100, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 day'
FROM goods g
WHERE g.name = 'iPhone 16 Pro'
  AND NOT EXISTS (SELECT 1 FROM seckill_goods sg WHERE sg.goods_id = g.id);

INSERT INTO seckill_goods (goods_id, seckill_price, stock_count, start_time, end_time)
SELECT g.id, 9999.00, 50, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 day'
FROM goods g
WHERE g.name = 'MacBook Pro 14'
  AND NOT EXISTS (SELECT 1 FROM seckill_goods sg WHERE sg.goods_id = g.id);
