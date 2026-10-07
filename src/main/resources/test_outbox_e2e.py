import urllib.request, json, time, subprocess

BASE = "http://localhost:8080/api"

print("=" * 55)
print("  🚀 Outbox 本地消息表与可靠事件投递端到端测试")
print("=" * 55)

# 1. 创建订单
print("[1] 正在创建测试订单 (用户ID: 6666, 票档: 101)...")
req = urllib.request.Request(f"{BASE}/orders/create?userId=6666&ticketCategoryId=101&count=1", method='POST')
with urllib.request.urlopen(req) as resp:
    order_data = json.loads(resp.read().decode())['data']
    order_id = order_data['id']
print(f"    ✅ 订单创建成功! orderId = {order_id}")

# 2. 预下单生成流水
print("\n[2] 正在调用预下单接口生成支付流水...")
req = urllib.request.Request(f"{BASE}/pay/prepay?orderId={order_id}", method='POST')
with urllib.request.urlopen(req) as resp:
    pay_data = json.loads(resp.read().decode())['data']
    pay_sn = pay_data['paySn']
print(f"    ✅ 预下单成功! paySn = {pay_sn}")

# 3. 沙箱支付并测量接口耗时
print("\n[3] 正在模拟沙箱支付扣款...")
t0 = time.time()
req = urllib.request.Request(f"{BASE}/pay/mock/cashier?paySn={pay_sn}", method='POST')
with urllib.request.urlopen(req) as resp:
    cashier_resp = json.loads(resp.read().decode())
pay_latency_ms = (time.time() - t0) * 1000.0
print(f"    ✅ 支付接口响应成功 (耗时: {pay_latency_ms:.2f}ms)!")

# 4. 查数据库验证 t_outbox
print("\n[4] 正在查询 PostgreSQL 数据库验证 t_outbox 本地消息表...")
sql = f"SELECT id, aggregate_id, topic, status, retry_count FROM t_outbox WHERE aggregate_id = {order_id};"
cmd = ["docker", "exec", "-i", "mall-postgres", "psql", "-U", "mall", "-d", "mall_db", "-t", "-A", "-F", ",", "-c", sql]

outbox_row = None
for _ in range(5):
    res = subprocess.run(cmd, capture_output=True, text=True)
    lines = res.stdout.strip().split("\n")
    if lines and lines[0]:
        outbox_row = lines[0].split(",")
        break
    time.sleep(1)

if outbox_row:
    msg_id, agg_id, topic, status, retries = outbox_row
    print(f"    ✅ t_outbox 成功捕获待发事件!")
    print(f"       - 消息 ID: {msg_id}")
    print(f"       - 聚合根 ID (orderId): {agg_id}")
    print(f"       - 投递 Topic: {topic}")
    print(f"       - 当前状态: {status} (0:待发送, 1:投递成功, 2:失败)")
    
    # 等待 OutboxRelayer 异步推进
    if status == '0':
        print("\n[*] 等待 2~3 秒观察 OutboxRelayer 后台投递员状态跃迁...")
        time.sleep(3)
        res = subprocess.run(cmd, capture_output=True, text=True)
        lines = res.stdout.strip().split("\n")
        if lines and lines[0]:
            _, _, _, new_status, _ = lines[0].split(",")
            print(f"    ✅ 状态跃迁验证: 初始 0 (INIT) -> 当前 {new_status} (1: SENT 已投递)")
else:
    print("    ⚠️ 未在 t_outbox 中查到对应记录，请检查事务是否提交！")

print("\n" + "=" * 55)
print("  🎉 测试完成！请在控制台查看 TicketIssueConsumer 出票日志")
print("=" * 55)
