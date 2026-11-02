-- 插入丰富的大麦演示演出场次数据 (覆盖广州、深圳、佛山等多城市及演唱会、话剧、音乐节、体育等多品类)
INSERT INTO d_program (id, title, actor, place, show_time) VALUES
(2, '陈奕迅【FEAR and DREAMS】世界巡回演唱会-深圳站', '陈奕迅', '深圳大运中心体育场', '2026-11-05 19:30:00'),
(3, '开心麻花爆笑舞台剧《乌龙山伯爵》-佛山站', '开心麻花团队', '佛山大剧院大剧场', '2026-10-25 20:00:00'),
(4, '2026 广州大湾区星巢草莓音乐节', '新裤子 / 痛仰 / 房东的猫', '广州长隆音乐广场', '2026-11-15 14:00:00'),
(5, '2026 粤港澳大湾区足球超级联赛焦点战', '广州队 vs 深圳队', '广州越秀山体育场', '2026-10-30 19:30:00')
ON CONFLICT (id) DO UPDATE SET 
    title = EXCLUDED.title,
    actor = EXCLUDED.actor,
    place = EXCLUDED.place,
    show_time = EXCLUDED.show_time;

-- 关联票档最低价格
INSERT INTO d_ticket_category (id, program_id, name, price, total_stock, remain_stock) VALUES
(201, 2, '看台 480元', 480.00, 500, 500),
(202, 2, '内场 1680元', 1680.00, 200, 200),
(301, 3, '二楼普通座 180元', 180.00, 300, 300),
(302, 3, 'VIP池座 480元', 480.00, 100, 100),
(401, 4, '单日早鸟票 380元', 380.00, 1000, 1000),
(402, 4, '双日通票 680元', 680.00, 500, 500),
(501, 5, '看台普及票 100元', 100.00, 2000, 2000)
ON CONFLICT (id) DO UPDATE SET 
    price = EXCLUDED.price,
    remain_stock = EXCLUDED.remain_stock;
