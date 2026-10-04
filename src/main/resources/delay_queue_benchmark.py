import urllib.request, json, time

BASE = "http://localhost:8080/api"

# 1. 记录初始库存
with urllib.request.urlopen(f"{BASE}/tickets/category/101") as resp:
    init_stock = json.loads(resp.read().decode())['data']['remainStock']
print(f"[*] 初始库存: {init_stock}")

# 2. 批量创建 20 笔待支付订单
BATCH = 20
orders = []
print(f"[*] 正在并发创建 {BATCH} 笔订单（超时时间 30s）...")
start_time = time.time()
for i in range(BATCH):
    req = urllib.request.Request(f"{BASE}/orders/create?userId={2000+i}&ticketCategoryId=101&count=1", method='POST')
    with urllib.request.urlopen(req) as resp:
        order_id = json.loads(resp.read().decode())['data']['id']
        orders.append(order_id)

with urllib.request.urlopen(f"{BASE}/tickets/category/101") as resp:
    deducted_stock = json.loads(resp.read().decode())['data']['remainStock']
print(f"[*] 20 笔订单创建完毕，当前锁定后库存: {deducted_stock} (减少了 {init_stock - deducted_stock})")

# 3. 倒计时等待 30s 到期
print("[*] 开始静置等待 35 秒（等待 Redis 时间轮到期并自动关单）...")
for sec in range(35, 0, -5):
    print(f"    剩余倒计时: {sec}s ...")
    time.sleep(5)

# 4. 统计关单结果与库存自愈
cancelled_count = 0
for oid in orders:
    with urllib.request.urlopen(f"{BASE}/orders/{oid}") as resp:
        st = json.loads(resp.read().decode())['data']['status']
        if st == 4:  # CANCELLED
            cancelled_count += 1

with urllib.request.urlopen(f"{BASE}/tickets/category/101") as resp:
    final_stock = json.loads(resp.read().decode())['data']['remainStock']

print("\n=== 延迟队列与库存自愈战报 ===")
print(f"到期自动取消成功率: {cancelled_count}/{BATCH} ({(cancelled_count/BATCH)*100:.1f}%)")
print(f"库存自愈恢复结果: 初始 {init_stock} -> 扣减后 {deducted_stock} -> 最终自愈回滚至 {final_stock}")
if final_stock == init_stock:
    print("✅ 达成库存 0 漂移！所有超时订单全量自愈！")
