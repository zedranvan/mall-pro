#!/usr/bin/env python3
import asyncio
import aiohttp
import time
import subprocess

# ================= 1. 配置参数 =================
BASE_URL = "http://localhost:8080/api/seckill"
GOODS_ID = 1          # 抢购商品 1
STOCK_COUNT = 50      # 初始库存 50 件
TOTAL_USERS = 1000    # 模拟 1000 个真实独立用户
CONCURRENCY_LIMIT = 200  # 同时在网络上并发的连接上限（防止把本机连接数打满）

# ================= 2. 积木一：测试前自动重置环境 =================
def reset_environment():
    print("🧹 正在初始化测试环境...")
    # 通过 docker 命令行，直接执行 SQL 清理订单并还原库存
    sql = f"""
    DELETE FROM seckill_order WHERE goods_id = {GOODS_ID};
    DELETE FROM order_info WHERE goods_id = {GOODS_ID};
    UPDATE seckill_goods SET stock_count = {STOCK_COUNT} WHERE id = {GOODS_ID};
    """
    subprocess.run(f'docker exec mall-postgres psql -U mall -d mall_db -c "{sql}"', shell=True, capture_output=True)
    
    # 调用我们的 init 接口，预热 Redis 库存
    subprocess.run(f'curl -s -X POST "{BASE_URL}/{GOODS_ID}/init?count={STOCK_COUNT}"', shell=True, capture_output=True)
    print("✅ 数据库与 Redis 已重置为 50 件初始库存！\n")

# ================= 3. 积木二：单个用户的完整秒杀动作 =================
async def single_user_seckill(session, semaphore, user_id, results):
    """
    每个用户做两件事：
    第一步：GET /path 拿到 60 秒有效的动态密码
    第二步：POST /{path}/order 带着密码真正抢购
    """
    async with semaphore:  # 信号量控制：最多允许 200 个协程同时发包
        start = time.perf_counter()
        try:
            # 1. 获取动态 path
            path_url = f"{BASE_URL}/{GOODS_ID}/path?userId={user_id}"
            async with session.get(path_url, timeout=5) as resp:
                data = await resp.json()
                dynamic_path = data.get("data")
                if not dynamic_path:
                    return

            # 2. 带着动态 path 下单
            order_url = f"{BASE_URL}/{GOODS_ID}/{dynamic_path}/order?userId={user_id}"
            async with session.post(order_url, timeout=5) as resp:
                body = await resp.json()
                code = body.get("code")  # 拿到业务状态码：200 还是 400
                cost = time.perf_counter() - start
                results.append({"code": code, "cost": cost})
        except Exception as e:
            results.append({"code": 500, "error": str(e)})

# ================= 4. 积木三：主调度与性能统计 =================
async def main():
    reset_environment()
    print(f"🚀 开始并发测试：{TOTAL_USERS} 个用户并发争抢 {STOCK_COUNT} 件库存...")

    results = []
    semaphore = asyncio.Semaphore(CONCURRENCY_LIMIT)  # 限制最大同时在途连接
    connector = aiohttp.TCPConnector(limit=CONCURRENCY_LIMIT)

    async with aiohttp.ClientSession(connector=connector) as session:
        t_start = time.perf_counter()
        # 创建 1000 个协程任务（userId: 1001 到 2000）
        tasks = [
            single_user_seckill(session, semaphore, 1000 + i, results)
            for i in range(1, TOTAL_USERS + 1)
        ]
        # 这一行：1000 个任务全军出击！
        await asyncio.gather(*tasks)
        total_time = time.perf_counter() - t_start

    # 统计前台数据
    success = sum(1 for r in results if r.get("code") == 200)
    failed = sum(1 for r in results if r.get("code") == 400)
    qps = TOTAL_USERS / total_time if total_time > 0 else 0

    print("\n" + "="*45)
    print(f"⏱️ 总耗时       : {total_time:.3f} 秒")
    print(f"⚡ 吞吐量 QPS   : {qps:.2f} req/s")
    print(f"🟢 抢到名额 (200): {success} 人")
    print(f"🔴 售罄拦截 (400): {failed} 人")
    print("="*45)

    # 等待 3 秒，让 Kafka 消费者在后台写完数据库
    print("\n⏳ 正在等待 Kafka 异步落库...")
    time.sleep(3)

    # ================= 5. 积木四：数据库硬核验资对账 =================
    # 查数据库真正生成了几个订单
    res_orders = subprocess.run(
        'docker exec mall-postgres psql -U mall -d mall_db -t -c "SELECT COUNT(*) FROM seckill_order WHERE goods_id = 1;"',
        shell=True, capture_output=True, text=True
    ).stdout.strip()
    
    # 查数据库剩余库存
    res_stock = subprocess.run(
        'docker exec mall-postgres psql -U mall -d mall_db -t -c "SELECT stock_count FROM seckill_goods WHERE id = 1;"',
        shell=True, capture_output=True, text=True
    ).stdout.strip()

    print("\n" + "="*45)
    print(f"📦 数据库实际订单数 : {res_orders}")
    print(f"📉 数据库剩余库存   : {res_stock}")
    
    if res_orders == str(STOCK_COUNT) and res_stock == "0":
        print("🏆 最终结论：完美！0 超卖！数据强一致性校验通过！")
    else:
        print("❌ 警告：出现数据不一致或超卖！")
    print("="*45)

if __name__ == "__main__":
    asyncio.run(main())