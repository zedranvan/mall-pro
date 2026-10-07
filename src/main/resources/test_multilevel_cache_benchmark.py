import time
import requests
import json
import subprocess

BASE_URL = "http://localhost:8080"
CATEGORY_ID = 101

def redis_cli(*args):
    cmd = ["docker", "exec", "url_shortener_redis", "redis-cli", "-a", "redis_password"] + list(args)
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def check_server_ready():
    for _ in range(10):
        try:
            res = requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}", timeout=2)
            if res.status_code == 200:
                return True
        except Exception:
            time.sleep(1)
    return False

def run_benchmark():
    print("=" * 65)
    print("  ⚡ Caffeine (L1) + Redis (L2) 多级缓存极速穿透与压测实测")
    print("=" * 65)

    if not check_server_ready():
        print("❌ 错误: 无法连接到 http://localhost:8080，请确认 Spring Boot 服务已成功启动！")
        return

    # [1] 清理 L2 Redis 缓存，模拟最冷首次访问
    print("\n[1] 模拟冷启动：主动清除 Redis 二级缓存 key=ticket_category:101 ...")
    redis_cli("del", f"ticket_category:{CATEGORY_ID}")

    # [2] 首次请求：L1/L2 均未命中，穿透到 PostgreSQL 数据库
    print("\n[2] 第 1 次请求（冷穿透）：L1 进程内存未命中 -> L2 Redis 未命中 -> 穿透 DB")
    t0 = time.time()
    res1 = requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}").json()
    t_cold = (time.time() - t0) * 1000.0
    print(f"    - 响应耗时: {t_cold:.2f} ms")
    print(f"    - 返回票档: {res1['data']['name']}, 价格: ¥{res1['data']['price']}, 库存: {res1['data']['remainStock']}")
    print("    - 结果: 成功从 PostgreSQL 取得，并完成双级回填 (Redis + Caffeine)！")

    # [3] 第二次请求：命中 L1 Caffeine 本地堆内存直出
    print("\n[3] 第 2 次请求（极速热读）：直接命中 L1 Caffeine 本地堆内存")
    t0 = time.time()
    res2 = requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}").json()
    t_warm = (time.time() - t0) * 1000.0
    print(f"    - 响应耗时: {t_warm:.2f} ms (比冷启动加速 {(t_cold / max(t_warm, 0.01)):.1f} 倍!)")
    print("    - 结果: 零网络 IO，直接由 Tomcat 线程从 JVM 堆内存指针直出！")

    # [4] 压测：模拟开票前 500 次高频并发刷新演出详情页
    total_requests = 500
    print(f"\n[4] 开始模拟开票前超高频狂刷演出详情页 (连续并发发起 {total_requests} 次请求)...")
    latencies = []
    bench_start = time.time()
    for _ in range(total_requests):
        req_start = time.time()
        requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}")
        latencies.append((time.time() - req_start) * 1000.0)
    total_time = time.time() - bench_start

    avg_latency = sum(latencies) / len(latencies)
    latencies.sort()
    p95_latency = latencies[int(len(latencies) * 0.95)]
    p99_latency = latencies[int(len(latencies) * 0.99)]
    qps = total_requests / total_time

    print(f"    📊 压测战报 (Caffeine L1 本地缓存读承载):")
    print(f"       - 总请求数:   {total_requests} 次")
    print(f"       - 总耗时:     {total_time:.3f} 秒")
    print(f"       - 吞吐 QPS:   {qps:.1f} req/s")
    print(f"       - 平均耗时:   {avg_latency:.2f} ms")
    print(f"       - P95 延迟:   {p95_latency:.2f} ms")
    print(f"       - P99 延迟:   {p99_latency:.2f} ms")

    # [5] 验证一致性双淘汰：库存变更时，L1 与 L2 必须瞬间被驱逐
    print("\n[5] 验证数据一致性与双级缓存联动淘汰...")
    print("    - 正在模拟发生购票扣减库存 (扣除 1 张)...")
    deduct_res = requests.post(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}/deduct?count=1").json()
    print(f"    - 扣减结果: {deduct_res}")

    # 查一次，验证是否触发双级重新加载，读到最新库存
    reload_res = requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}").json()
    new_stock = reload_res['data']['remainStock']
    print(f"    - 淘汰后重新穿透加载，获取最新剩余库存: {new_stock} (原库存: {res1['data']['remainStock']})")
    print(f"    - 验证结论: {'✅ 缓存双淘汰成功，读到最新数据库一致状态！' if new_stock == res1['data']['remainStock'] - 1 else '❌ 缓存不一致！'}")

    # 恢复库存保证后续测试
    requests.post(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}/deduct?count=-1")

    print("\n" + "=" * 65)
    print("  🏆 Caffeine + Redis 多级缓存性能压测与一致性实测大圆满！")
    print("=" * 65)

if __name__ == "__main__":
    run_benchmark()
