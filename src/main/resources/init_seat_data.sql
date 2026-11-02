-- 1. 创建物理座位表
CREATE TABLE IF NOT EXISTS d_seat (
    id BIGINT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    area VARCHAR(64) NOT NULL,
    row_num INT NOT NULL,
    col_num INT NOT NULL,
    seat_index INT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    lock_user_id BIGINT,
    lock_time TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_program_seat_index ON d_seat(program_id, seat_index);
CREATE INDEX IF NOT EXISTS idx_program_category ON d_seat(program_id, category_id);

-- 2. 为当前场次 (program_id=1, category_id=101) 初始化 5 排 x 10 列 = 50 个标准物理座位
-- seat_index 从 0 到 49，全部初始为 0 (可选/空闲)
DO $$
DECLARE
    r INT;
    c INT;
    s_idx INT := 0;
    seat_id BIGINT := 3000000000000000000;
BEGIN
    FOR r IN 1..5 LOOP
        FOR c IN 1..10 LOOP
            INSERT INTO d_seat (id, program_id, category_id, area, row_num, col_num, seat_index, status)
            VALUES (seat_id + s_idx, 1, 101, '南看台A区', r, c, s_idx, 0)
            ON CONFLICT (program_id, seat_index) DO UPDATE 
            SET status = 0, lock_user_id = NULL, lock_time = NULL;
            s_idx := s_idx + 1;
        END LOOP;
    END LOOP;
END $$;
