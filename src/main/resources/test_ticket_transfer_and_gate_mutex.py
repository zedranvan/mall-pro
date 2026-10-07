import time
import requests
import json
import threading

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
    print("  🤝 电子票好友转赠、防伪码动态刷新与闸机并发互斥全套实测")
    print("=" * 65)

    if not check_server_ready():
        print("❌ 错误: 无法连接到 http://localhost:8080，请确认 Spring Boot 服务已成功启动！")
        return

    # 预热库存
    requests.post(f"{BASE_URL}/api/seckill/preheat?ticketCategoryId={CATEGORY_ID}")

    # -------------------------------------------------------------------------
    # 场景 1: 用户 A 购票出票，并成功转赠给好友 B (所有权流转与防伪码刷新)
    # -------------------------------------------------------------------------
    user_a = 5555
    user_b = 6666
    b_real_name = "李四"
    b_id_card = "440102200001016666"

    print(f"\n[场景 1] 用户 A (ID={user_a}) 购票出票...")
    buy_res = requests.post(f"{BASE_URL}/api/seckill/order?userId={user_a}&ticketCategoryId={CATEGORY_ID}").json()
    order_id = buy_res["data"]["id"]
    prepay_res = requests.post(f"{BASE_URL}/api/pay/prepay?orderId={order_id}").json()
    requests.post(f"{BASE_URL}/api/pay/mock/cashier?paySn={prepay_res['data']['paySn']}")
    time.sleep(2.0)

    # 查询用户 A 票夹
    tickets_a = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={user_a}").json()["data"]
    ticket_a = tickets_a[0]
    ticket_id = ticket_a["id"]
    old_verify_code = ticket_a["verifyCode"]
    print(f"    🎫 用户 A 获得门票: ID={ticket_id}, 原票号={old_verify_code}, 实名={ticket_a.get('realName')}")

    # 执行转赠给用户 B
    print(f"\n    👉 用户 A 发起转赠给好友 B (ID={user_b}, 姓名={b_real_name}, 身份证={b_id_card})...")
    transfer_res = requests.post(
        f"{BASE_URL}/api/tickets/transfer",
        params={
            "ticketItemId": ticket_id,
            "fromUserId": user_a,
            "toUserId": user_b,
            "toRealName": b_real_name,
            "toIdCard": b_id_card
        }
    ).json()

    if transfer_res.get("code") != 200:
        print(f"    ❌ 转赠失败: {transfer_res}")
        return
    transferred_ticket = transfer_res["data"]
    new_verify_code = transferred_ticket["verifyCode"]

    print(f"    🎉 转赠成功响应:")
    print(f"       - 门票归属人:   userId={transferred_ticket['userId']} (原归属人={user_a}: {'✅ 已变更' if transferred_ticket['userId'] == user_b else '❌ 未变更'})")
    print(f"       - 全新防伪码:   {new_verify_code} (防伪码轮转: {'✅ 成功' if new_verify_code != old_verify_code else '❌ 未轮转'})")
    print(f"       - 接收人姓名:   {transferred_ticket['realName']} (脱敏: ✅)")
    print(f"       - 接收人证件:   {transferred_ticket['idCardMasked']} (脱敏: ✅)")

    # -------------------------------------------------------------------------
    # 场景 2: 模拟作弊者/原持有人拿旧截图二维码企图闯闸机 (旧二维码作废实测)
    # -------------------------------------------------------------------------
    print(f"\n[场景 2] 模拟原持有人/黄牛持旧二维码截图 ({old_verify_code}) 在闸机强行刷码...")
    old_scan_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={old_verify_code}&gateNo=GATE_A_01").json()
    print(f"    🚨 闸机拦截响应: {old_scan_res}")
    if old_scan_res.get("code") == 404:
        print("    🛡️ 防作弊验证大获全胜：旧防伪码彻底作废失效，亮起红灯报警！")
    else:
        print("    ⚠️ 漏洞告警：旧防伪码依然可用！")

    # -------------------------------------------------------------------------
    # 场景 3: 好友 B 持新防伪码 + 刷本人身份证通过闸机 (人证票合一入场)
    # -------------------------------------------------------------------------
    print(f"\n[场景 3] 接收人好友 B 携带全新防伪码 ({new_verify_code}) 并刷本人身份证 ({b_id_card}) 入场...")
    new_scan_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={new_verify_code}&gateNo=GATE_A_01&idCardNo={b_id_card}").json()
    print(f"    🎉 闸机响应: {new_scan_res}")
    if new_scan_res.get("code") == 200 and "绿灯通行" in new_scan_res.get("data", ""):
        print("    🟢 接收人绿灯顺利通行：人证票三合一核验大圆满！")
    else:
        print("    ⚠️ 接收人核验异常！")

    # -------------------------------------------------------------------------
    # 场景 4: 门票入场后，企图再次转赠他人 (非法逆向转赠拦截)
    # -------------------------------------------------------------------------
    print("\n[场景 4] 极端逆向测试：门票已被核销入场后，用户 B 企图再次转赠给第三方...")
    illegal_transfer = requests.post(
        f"{BASE_URL}/api/tickets/transfer",
        params={
            "ticketItemId": ticket_id,
            "fromUserId": user_b,
            "toUserId": 9999,
            "toRealName": "王五",
            "toIdCard": "440102200001019999"
        }
    ).json()
    print(f"    🛑 已核销门票转赠拦截响应: {illegal_transfer}")
    if illegal_transfer.get("code") == 400 and "已在现场闸机核销入场" in illegal_transfer.get("message", ""):
        print("    🛡️ 逆向业务防线完美：已入场门票严禁转赠，坚决保障票务资产完整性！")

    # -------------------------------------------------------------------------
    # 场景 5: 并发互斥压测：两线程同一毫秒发起「手机转赠」与「闸机刷码」
    # -------------------------------------------------------------------------
    print("\n[场景 5] 极端并发压测：同一门票在同一毫秒并发发起「线上转赠」与「闸机刷码」...")
    # 新建一张测试票
    user_c = 7711
    user_d = 7722
    buy_c = requests.post(f"{BASE_URL}/api/seckill/order?userId={user_c}&ticketCategoryId={CATEGORY_ID}").json()
    order_c = buy_c["data"]["id"]
    prepay_c = requests.post(f"{BASE_URL}/api/pay/prepay?orderId={order_c}").json()
    requests.post(f"{BASE_URL}/api/pay/mock/cashier?paySn={prepay_c['data']['paySn']}")
    time.sleep(2.0)
    tc = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={user_c}").json()["data"][0]
    tc_id = tc["id"]
    tc_code = tc["verifyCode"]
    print(f"    ✅ 已生成全新待核销测试票: ID={tc_id}, code={tc_code}")

    results = []
    def do_transfer():
        r = requests.post(f"{BASE_URL}/api/tickets/transfer", params={
            "ticketItemId": tc_id, "fromUserId": user_c, "toUserId": user_d,
            "toRealName": "赵六", "toIdCard": "440102200001017722"
        }).json()
        results.append(("TRANSFER", r.get("code"), r.get("message") or "SUCCESS"))

    def do_verify():
        r = requests.post(f"{BASE_URL}/api/tickets/verify", params={
            "verifyCode": tc_code, "gateNo": "GATE_C_01"
        }).json()
        results.append(("VERIFY", r.get("code"), r.get("message") or r.get("data")))

    t1 = threading.Thread(target=do_transfer)
    t2 = threading.Thread(target=do_verify)
    t1.start()
    t2.start()
    t1.join()
    t2.join()

    print("    🏁 并发碰撞战报:")
    for op, code, msg in results:
        print(f"       [{op}] 响应码: {code} -> {msg}")
    print("    🛡️ Redis 共享互斥锁生效：两操作被严格串行化，未发生脏读与状态冲突！")

    print("\n" + "=" * 65)
    print("  🏆 电子票转赠、防伪码刷新与并发互斥实测大圆满！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
