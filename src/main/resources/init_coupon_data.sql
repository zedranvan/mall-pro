-- 1. 营销优惠券模板表 (Coupon Template)
CREATE TABLE IF NOT EXISTS d_coupon_template (
    id                  BIGINT          PRIMARY KEY,                  -- 模板ID
    name                VARCHAR(128)    NOT NULL,                     -- 优惠券名称
    type                SMALLINT        NOT NULL,                     -- 1:满减券, 2:折扣券, 3:无门槛立减
    threshold_amount    NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,        -- 使用门槛金额 (0表示无门槛)
    discount_amount     NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,        -- 满减/立减减免金额
    discount_rate       NUMERIC(5, 2),                                -- 折扣率 (如 0.85 表示 85折)
    max_discount_amount NUMERIC(10, 2),                               -- 折扣券最高减免封顶金额
    total_quota         INT             NOT NULL,                     -- 总发放配额
    remain_quota        INT             NOT NULL,                     -- 剩余未领取配额
    valid_start_time    TIMESTAMP       NOT NULL,                     -- 有效期起始时间
    valid_end_time      TIMESTAMP       NOT NULL,                     -- 有效期截止时间
    status              SMALLINT        NOT NULL DEFAULT 1,           -- 1:启用, 0:停用
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 用户优惠券资产表 (User Coupon Asset)
CREATE TABLE IF NOT EXISTS d_coupon_user (
    id                  BIGINT          PRIMARY KEY,                  -- 用户券ID
    coupon_code         VARCHAR(64)     UNIQUE NOT NULL,              -- 唯一防伪券码
    user_id             BIGINT          NOT NULL,                     -- 持有用户ID
    template_id         BIGINT          NOT NULL,                     -- 归属模板ID
    status              SMALLINT        NOT NULL DEFAULT 0,           -- 0:未使用, 1:冻结锁定中, 2:已核销使用, 3:已失效
    order_id            BIGINT,                                       -- 关联消费的订单ID
    used_time           TIMESTAMP,                                    -- 核销时间
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 复合防刷与快速查询索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_template ON d_coupon_user(user_id, template_id);
CREATE INDEX IF NOT EXISTS idx_coupon_user_status ON d_coupon_user(user_id, status);

-- 3. 预置 3 张典型商业优惠券活动模板
INSERT INTO d_coupon_template (
    id, name, type, threshold_amount, discount_amount, discount_rate, max_discount_amount,
    total_quota, remain_quota, valid_start_time, valid_end_time, status
) VALUES 
(
    1, '看台专享·满300减50元券', 1, 300.00, 50.00, NULL, NULL,
    100, 100, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '30 days', 1
),
(
    2, '大额神券·满500减80元券', 1, 500.00, 80.00, NULL, NULL,
    50, 50, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '30 days', 1
),
(
    3, '狂欢折扣·全场85折券', 2, 0.00, 0.00, 0.85, 70.00,
    200, 200, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '30 days', 1
)
ON CONFLICT (id) DO UPDATE SET 
    remain_quota = EXCLUDED.total_quota,
    status = 1;
