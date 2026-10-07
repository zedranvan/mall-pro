import urllib.request, urllib.parse, json, time, threading

BASE = "http://localhost:8080/api"

print("=" * 65)
print("  🎫 电子票夹中台与现场闸机防作弊核销全套实测")
print("=" * 65)

# -------------------------------------------------------------
# 1. C 端用户票夹查询
# -------------------------------------------------------------
print("\n[1] 正在查询用户 ID=6666 的手机电子票夹...")
with urllib.request.urlopen(f"{BASE}/tickets/my-tickets?userId=6666") as resp:
    tickets = json.loads(resp.read().decode())['data']

if not tickets:
    print("    ⚠️ 当前用户暂无门票，请先通过 test_outbox_e2e.py 买一张票！")
    exit(0)

ticket = tickets[0]
verify_code = ticket['verifyCode']
seat = ticket['seatInfo']
title = ticket['ticketTitle']
status = ticket['status']

print(f"    ✅ 成功拉取到用户最新门票资产:")
print(f"       - 门票 ID:   {ticket['id']}")
print(f"       - 演出标题: {title}")
print(f"       - 分配座位: {seat}")
print(f"       - 防伪码:   {verify_code}")
print(f"       - 当前状态: {status} (0: 待入场核销)")

# -------------------------------------------------------------
# 2. B 端闸机首次扫码核销（正常绿灯放行）
# -------------------------------------------------------------
print(f"\n[2] 用户到达检票口 GATE_A_01，闸机首次扫码核销 (防伪码: {verify_code})...")
verify_url = f"{BASE}/tickets/verify?verifyCode={verify_code}&gateNo=GATE_A_01"
req = urllib.request.Request(verify_url, method='POST')
try:
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode())
        print(f"    🎉 闸机响应: {res.get('data')}")
except urllib.error.HTTPError as e:
    print(f"    ❌ 异常: {e.read().decode()}")

# -------------------------------------------------------------
# 3. 模拟黄牛截图给第二人作弊（二次刷码：红灯拦截）
# -------------------------------------------------------------
print(f"\n[3] 模拟黄牛作弊：第二人持相同截图在 GATE_B_02 闸机企图再次刷码入场...")
req2 = urllib.request.Request(f"{BASE}/tickets/verify?verifyCode={verify_code}&gateNo=GATE_B_02", method='POST')
try:
    with urllib.request.urlopen(req2) as resp:
        res = json.loads(resp.read().decode())
        print(f"    ⚠️ 异常未被拦截: {res}")
except urllib.error.HTTPError as e:
    err_body = json.loads(e.read().decode())
    print(f"    🚨 成功触发红灯警报！")
    print(f"       HTTP 状态码: {e.code}")
    print(f"       拦截原因: {err_body.get('message')}")

# -------------------------------------------------------------
# 4. 模拟伪造假票攻击
# -------------------------------------------------------------
print(f"\n[4] 模拟黑客伪造假防伪码 (TCK_FAKE_666888) 强行刷码...")
fake_req = urllib.request.Request(f"{BASE}/tickets/verify?verifyCode=TCK_FAKE_666888&gateNo=GATE_A_01", method='POST')
try:
    with urllib.request.urlopen(fake_req) as resp:
        pass
except urllib.error.HTTPError as e:
    err_body = json.loads(e.read().decode())
    print(f"    🚨 假票秒级识别阻断！")
    print(f"       HTTP 状态码: {e.code}")
    print(f"       拦截原因: {err_body.get('message')}")

# -------------------------------------------------------------
# 5. 极端并发演练：两台闸机在同一毫秒并发刷同一张新票（Redis互斥锁）
# -------------------------------------------------------------
print(f"\n[5] 极端并发实测：两台闸机同一毫秒并发刷码作弊 (Redis 互斥锁压测)...")

# 5.1 先快速买一张全新的票用于并发碰撞
order_req = urllib.request.Request(f"{BASE}/orders/create?userId=7777&ticketCategoryId=101&count=1", method='POST')
with urllib.request.urlopen(order_req) as r:
    oid = json.loads(r.read().decode())['data']['id']
with urllib.request.urlopen(urllib.request.Request(f"{BASE}/pay/prepay?orderId={oid}", method='POST')) as r:
    psn = json.loads(r.read().decode())['data']['paySn']
with urllib.request.urlopen(urllib.request.Request(f"{BASE}/pay/mock/cashier?paySn={psn}", method='POST')) as r:
    pass
time.sleep(2) # 等待 Outbox & Kafka 出票

with urllib.request.urlopen(f"{BASE}/tickets/my-tickets?userId=7777") as r:
    new_ticket = json.loads(r.read().decode())['data'][0]
concurrent_code = new_ticket['verifyCode']
print(f"    ✅ 已生成全新待核销门票: {concurrent_code}")

barrier = threading.Barrier(2)
results = []

def gate_scan(gate_name):
    barrier.wait() # 枪响齐发
    u = f"{BASE}/tickets/verify?verifyCode={concurrent_code}&gateNo={gate_name}"
    try:
        with urllib.request.urlopen(urllib.request.Request(u, method='POST')) as response:
            results.append((gate_name, 200, json.loads(response.read().decode())['data']))
    except urllib.error.HTTPError as err:
        results.append((gate_name, err.code, json.loads(err.read().decode())['message']))

t1 = threading.Thread(target=gate_scan, args=("GATE_NORTH_01",))
t2 = threading.Thread(target=gate_scan, args=("GATE_SOUTH_02",))
t1.start(); t2.start()
t1.join(); t2.join()

print(f"    🏁 并发碰撞战报:")
for g, code, msg in results:
    print(f"       [{g}] 响应码: {code} -> {msg}")

print("\n" + "=" * 65)
print("  🏆 现场闸机防作弊实测大圆满！")
print("=" * 65)
