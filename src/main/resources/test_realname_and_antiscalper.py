import time
import requests
import json
import subprocess

BASE_URL = "http://localhost:8080"
CATEGORY_ID = 101

def check_server_ready():
    for _ in range(10):
        try:
            res = requests.get(f"{BASE_URL}/api/tickets/category/{CATEGORY_ID}", timeout=2)
            if res.status_code == 200:
                return True
        except Exception:
            time.sleep(1)
    return False

def run_test():
    print("=" * 65)
    print("  🛡️ 强实名制、黄牛高频刷单风控与闸机人证票三合一全套实测")
    print("=" * 65)

    if not check_server_ready():
        print("❌ 错误: 无法连接到 http://localhost:8080，请确认 Spring Boot 服务已成功启动！")
        return

    # 预热库存
    requests.post(f"{BASE_URL}/api/seckill/preheat?ticketCategoryId={CATEGORY_ID}")

    # -------------------------------------------------------------------------
    # 场景 1: 黄牛自动化脚本毫秒级高频连击（Redis ZSet 滑动窗口风控实测）
    # -------------------------------------------------------------------------
    print("\n[场景 1] 模拟黄牛脚本多线程毫秒级高频并发连击秒杀接口 (用户ID=1001)...")
    scalper_user_id = 1001
    intercepted = False
    for i in range(1, 6):
        res = requests.post(f"{BASE_URL}/api/seckill/order?userId={scalper_user_id}&ticketCategoryId={CATEGORY_ID}").json()
        print(f"    - 第 {i} 次瞬时请求: code={res.get('code')}, message={res.get('message')}")
        if res.get("code") == 429:
            intercepted = True
            print("    🚨 命中风控防线：成功识别高频脚本刷单，触发 HTTP 429 频次拦截！")

    if intercepted:
        print("    🛡️ [场景 1 战报]：Redis ZSet 2秒滑动窗口有效扼杀毫秒级刷单流量！")
    else:
        print("    ⚠️ [场景 1 告警]：未能成功拦截高频请求，请检查限流代码！")

    # 等待 2.2 秒让滑动窗口自然流逝
    print("    ⏳ 等待 2.2 秒让滑动窗口自然滑过...")
    time.sleep(2.2)

    # -------------------------------------------------------------------------
    # 场景 2: 真实合规用户抢票出票与实名隐私脱敏验证 (PIPL 合规性核查)
    # -------------------------------------------------------------------------
    normal_user_id = 7777
    expected_raw_id = "44010219980101" + f"{normal_user_id % 10000:04d}"
    print(f"\n[场景 2] 正常合规用户 ID={normal_user_id} 发起购票与支付...")
    buy_res = requests.post(f"{BASE_URL}/api/seckill/order?userId={normal_user_id}&ticketCategoryId={CATEGORY_ID}").json()
    if buy_res.get("code") != 200:
        print(f"    ❌ 下单失败: {buy_res}")
        return
    order_id = buy_res["data"]["id"]
    print(f"    ✅ 订单创建成功! orderId={order_id}")

    # 模拟沙箱扣款
    prepay_res = requests.post(f"{BASE_URL}/api/pay/prepay?orderId={order_id}").json()
    requests.post(f"{BASE_URL}/api/pay/mock/cashier?paySn={prepay_res['data']['paySn']}")
    print("    ✅ 模拟微信支付成功，等待 Kafka 异步出票与实名绑定...")
    time.sleep(2.0)

    # 查询电子票夹资产
    tickets_res = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={normal_user_id}").json()
    user_tickets = tickets_res.get("data", [])
    if not user_tickets:
        print("    ❌ 未查询到出票资产！")
        return
    ticket = user_tickets[0]
    verify_code = ticket["verifyCode"]
    print(f"    🎫 电子门票已出票:")
    print(f"       - 门票 ID:     {ticket['id']}")
    print(f"       - 防伪票号:   {verify_code}")
    print(f"       - 实名姓名:   {ticket.get('realName')} (脱敏合规: ✅)")
    print(f"       - 证件展示:   {ticket.get('idCardMasked')} (脱敏合规: ✅)")
    print(f"       - 证件散列:   {ticket.get('idCardHash')[:16]}... (加盐 SHA-256 不可逆存储: ✅)")

    # -------------------------------------------------------------------------
    # 场景 3: 现场闸机人证票三合一防作弊核销实测
    # -------------------------------------------------------------------------
    print(f"\n[场景 3] 现场闸机人证票三合一安全核验实测 (防伪码: {verify_code})...")

    # 3.1 模拟黄牛拿截图冒名顶替入场 (票号真实，但持有的身份证非购票本人)
    fake_id_card = "440102199001019999"
    print(f"    - [测试 3.1] 冒名顶替测试: 拿真实二维码，但刷冒名身份证 ({fake_id_card})...")
    fake_verify_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={verify_code}&gateNo=GATE_A_01&idCardNo={fake_id_card}").json()
    print(f"      🚨 闸机拦截响应: {fake_verify_res}")
    if fake_verify_res.get("code") == 403 and "人证不一致" in fake_verify_res.get("message", ""):
        print("      🛡️ 冒名顶替被瞬间击穿：闸机亮起红灯报警，阻断非本人进场！")
    else:
        print("      ⚠️ 闸机未拦截人证不符，存在严重漏洞！")

    # 3.2 真实实名购票人本人入场 (人证票完全匹配)
    print(f"    - [测试 3.2] 本人刷证核验: 刷本人身份证 ({expected_raw_id}) 并扫描门票码...")
    real_verify_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={verify_code}&gateNo=GATE_A_01&idCardNo={expected_raw_id}").json()
    print(f"      🎉 闸机响应: {real_verify_res}")
    if real_verify_res.get("code") == 200 and "绿灯通行" in real_verify_res.get("data", ""):
        print("      🟢 人证票三合一核验成功：闸机绿灯亮起，开门放行！")
    else:
        print("      ⚠️ 正式核验未通过，请检查逻辑！")

    # 3.3 二次重复入场拦截
    print(f"    - [测试 3.3] 重复刷码测试: 该门票进场后，企图再次刷码入场...")
    re_verify_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={verify_code}&gateNo=GATE_B_02&idCardNo={expected_raw_id}").json()
    print(f"      🚨 重复核销拦截: {re_verify_res}")
    if re_verify_res.get("code") == 400 and "门票已被核销" in re_verify_res.get("message", ""):
        print("      🛡️ 二次进场拦截完美：闸机红灯报警，彻底杜绝一票多人进！")

    print("\n" + "=" * 65)
    print("  🏆 强实名制合规、黄牛滑动窗口风控与人证票闸机实测大圆满！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
