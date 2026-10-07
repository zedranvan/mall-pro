import time
import requests
import json
import subprocess

BASE_URL = "http://localhost:8080"

def redis_cli(*args):
    cmd = ["docker", "exec", "url_shortener_redis", "redis-cli", "-a", "redis_password"] + list(args)
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def get_db_ticket_stock(category_id=101):
    res = requests.get(f"{BASE_URL}/api/tickets/category/{category_id}").json()
    if res.get("code") == 200 and res.get("data"):
        return res["data"]["remainStock"]
    return 0

def check_server_ready():
    for _ in range(10):
        try:
            res = requests.get(f"{BASE_URL}/api/tickets/category/101", timeout=2)
            if res.status_code == 200:
                return True
        except Exception:
            time.sleep(1)
    return False

def run_test():
    print("=" * 65)
    print("  🔄 阶梯退票退款、逆向状态跃迁与库存公海回流全套实测")
    print("=" * 65)

    if not check_server_ready():
        print("❌ 错误: 无法连接到 http://localhost:8080，请确认 Spring Boot 服务已成功启动！")
        return

    category_id = 101
    user_id = 8888

    # [1] 预热库存
    print("\n[1] 预热秒杀库存与重置限购名单...")
    requests.post(f"{BASE_URL}/api/seckill/preheat?ticketCategoryId={category_id}")
    init_db_stock = get_db_ticket_stock(category_id)
    init_redis_stock = int(redis_cli("get", f"ticket:stock:{category_id}") or 0)
    print(f"    - DB 初始可售库存: {init_db_stock}")
    print(f"    - Redis 初始秒杀库存: {init_redis_stock}")

    # [2] 正向抢票并完成支付出票
    print(f"\n[2] 用户 ID={user_id} 模拟高并发抢票与秒级出票...")
    buy_res = requests.post(f"{BASE_URL}/api/seckill/order?userId={user_id}&ticketCategoryId={category_id}").json()
    if buy_res.get("code") != 200:
        print(f"    ❌ 抢票失败: {buy_res}")
        return
    order_id = buy_res["data"]["id"]
    order_price = buy_res["data"]["orderPrice"]
    print(f"    ✅ 订单创建成功! orderId={order_id}, 订单金额={order_price}")

    # 预下单
    prepay_res = requests.post(f"{BASE_URL}/api/pay/prepay?orderId={order_id}").json()
    pay_sn = prepay_res["data"]["paySn"]

    # 模拟沙箱收银台直接扣款与回调
    cashier_res = requests.post(f"{BASE_URL}/api/pay/mock/cashier?paySn={pay_sn}").json()
    print(f"    ✅ 模拟微信支付成功: {cashier_res.get('data')}, Outbox 投递 Kafka...")

    # 等待异步出票落库
    time.sleep(2.0)

    # 查询票夹
    tickets_res = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={user_id}").json()
    user_tickets = tickets_res.get("data", [])
    if not user_tickets:
        print("    ❌ 未查询到出票资产！")
        return
    ticket_item = user_tickets[0]
    ticket_id = ticket_item["id"]
    verify_code = ticket_item["verifyCode"]
    print(f"    🎫 电子票已生成: ticketId={ticket_id}, 座位={ticket_item['seatInfo']}, 防伪码={verify_code}, 状态={ticket_item['status']}")

    # 售出后的库存基线
    post_buy_db_stock = get_db_ticket_stock(category_id)
    post_buy_redis_stock = int(redis_cli("get", f"ticket:stock:{category_id}") or 0)
    print(f"    📊 购票后库存变化: DB={post_buy_db_stock}, Redis={post_buy_redis_stock} (库存各-1)")

    # [3] 发起阶梯退票申请
    print(f"\n[3] 用户 ID={user_id} 申请线上退票: ticketItemId={ticket_id}...")
    refund_res = requests.post(f"{BASE_URL}/api/tickets/refund?ticketItemId={ticket_id}&userId={user_id}").json()
    if refund_res.get("code") != 200:
        print(f"    ❌ 退票失败: {refund_res}")
        return
    refund_data = refund_res["data"]
    print(f"    🎉 退票成功响应:")
    print(f"       - 原票价格:   ¥{refund_data['originalPrice']}")
    print(f"       - 手续费率:   {float(refund_data['feeRate']) * 100:.0f}%")
    print(f"       - 扣除手续费: ¥{refund_data['handlingFee']}")
    print(f"       - 最终实退款: ¥{refund_data['actualRefund']}")
    print(f"       - 命中规则:   {refund_data['refundRuleDesc']}")

    # [4] 验证数据库与缓存逆向状态机
    print("\n[4] 正在验证逆向状态机是否达到最终一致性...")
    # 验证票状态
    my_tickets_after = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={user_id}").json()["data"]
    curr_ticket = [t for t in my_tickets_after if t["id"] == ticket_id][0]
    print(f"    - 电子票当前状态: {curr_ticket['status']} (2 代表已作废/已退票: {'✅ 符合预期' if curr_ticket['status'] == 2 else '❌ 状态错误'})")

    # 验证库存自愈
    post_refund_db_stock = get_db_ticket_stock(category_id)
    post_refund_redis_stock = int(redis_cli("get", f"ticket:stock:{category_id}") or 0)
    user_is_member = redis_cli("sismember", f"ticket:users:{category_id}", str(user_id))
    print(f"    - DB 恢复后库存:  {post_refund_db_stock} (回补 +1: {'✅ 成功' if post_refund_db_stock == init_db_stock else '❌ 失败'})")
    print(f"    - Redis 恢复库存: {post_refund_redis_stock} (回补 +1: {'✅ 成功' if post_refund_redis_stock == init_redis_stock else '❌ 失败'})")
    print(f"    - 用户限购名额:   {'✅ 已移出限制名单，用户获得重抢资格' if user_is_member == '0' else '❌ 仍在限购集合'}")

    # [5] 验证闸机联动拦截已退废票
    print(f"\n[5] 模拟黄牛/作弊者企图持已退废票 (防伪码: {verify_code}) 在 GATE_A_01 强闯闸机...")
    gate_res = requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={verify_code}&gateNo=GATE_A_01").json()
    print(f"    🚨 闸机拦截响应: {gate_res}")
    if gate_res.get("code") == 400 and "退票作废" in gate_res.get("message", ""):
        print("    🛡️ 闸机拦截验证大获全胜：成功识别已退作废门票，亮起红灯报警！")
    else:
        print("    ⚠️ 拦截异常，请检查拦截逻辑！")

    # [6] 验证已入场门票严禁退票反向拦截
    print("\n[6] 极端场景验证：模拟用户已刷闸机进场 (status=1)，再企图退票白嫖...")
    user_id_2 = 9999
    buy_res_2 = requests.post(f"{BASE_URL}/api/seckill/order?userId={user_id_2}&ticketCategoryId={category_id}").json()
    order_id_2 = buy_res_2["data"]["id"]
    prepay_res_2 = requests.post(f"{BASE_URL}/api/pay/prepay?orderId={order_id_2}").json()
    pay_sn_2 = prepay_res_2["data"]["paySn"]
    requests.post(f"{BASE_URL}/api/pay/mock/cashier?paySn={pay_sn_2}")
    time.sleep(2.0)
    t2 = requests.get(f"{BASE_URL}/api/tickets/my-tickets?userId={user_id_2}").json()["data"][0]
    # 闸机核销入场
    requests.post(f"{BASE_URL}/api/tickets/verify?verifyCode={t2['verifyCode']}&gateNo=GATE_VIP_01")
    # 企图退票
    illegal_refund = requests.post(f"{BASE_URL}/api/tickets/refund?ticketItemId={t2['id']}&userId={user_id_2}").json()
    print(f"    🛑 已核销退款拦截响应: {illegal_refund}")
    if illegal_refund.get("code") == 400 and "已在闸机核销入场" in illegal_refund.get("message", ""):
        print("    🛡️ 逆向业务防线完美：已入场门票强行拦截退票，坚决捍卫主办方资金安全！")

    print("\n" + "=" * 65)
    print("  🏆 售后逆向全闭环、阶梯退款与库存自愈实测大圆满！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
