import requests
import json
import time
import subprocess
import random

BASE_URL = "http://localhost:8080"
CATEGORY_ID = 101 # 看台 580元

def log_step(title):
    print(f"\n{'='*75}\n  🎯 [全商业链路闭环] {title}\n{'='*75}")

def run_psql(sql):
    cmd = ["docker", "exec", "mall-postgres", "psql", "-U", "mall", "-d", "mall_db", "-t", "-A", "-c", sql]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def main():
    print("\n" + "="*75)
    print("  🎪 大麦级完整商业闭环端到端实测：检索 -> 鉴权 -> 实名秒杀 -> 履约 -> 闸机")
    print("="*75)

    # 预热库存
    requests.post(f"{BASE_URL}/api/seckill/preheat", params={"ticketCategoryId": CATEGORY_ID})

    # =========================================================================
    # [场景 1] 游客未登录模式：演出场次检索与票档详情联动
    # =========================================================================
    log_step("场景 1: 游客公开检索演出详情与 580/1880 票档 (多级缓存直出)")
    search_resp = requests.get(f"{BASE_URL}/api/program/search", params={"keyword": "周杰伦"})
    assert search_resp.status_code == 200, f"检索异常: HTTP {search_resp.status_code}"
    programs = search_resp.json()["data"]
    assert len(programs) > 0, "未查到周杰伦演出！"
    program_id = programs[0]["id"]
    print(f"✅ 游客检索成功: 《{programs[0]['title']}》 (ID={program_id})")

    detail_resp = requests.get(f"{BASE_URL}/api/program/{program_id}")
    assert detail_resp.status_code == 200
    detail = detail_resp.json()["data"]
    categories = detail.get("ticketCategory") or detail.get("ticketCategories")
    print(f"🎫 演出详情聚合票档成功 (共 {len(categories)} 档位):")
    for c in categories:
        print(f"   - [{c['name']}] 价格: ¥{c['price']}, 余票: {c['remainStock']}")

    # =========================================================================
    # [场景 2] 用户中心：注册登录、JWT 签发与添加常用实名观演人
    # =========================================================================
    log_step("场景 2: 用户注册登录拿 JWT，并在个人中心录入真实观演人")
    rand_suffix = random.randint(1000, 9999)
    username = f"buyer_{rand_suffix}"
    phone = f"138{rand_suffix}0000"
    password = "Password123!"

    # 注册用户 A
    reg_resp = requests.post(f"{BASE_URL}/api/user/register", json={
        "username": username,
        "phone": phone,
        "password": password,
        "nickname": f"嘉润_{rand_suffix}"
    })
    assert reg_resp.json()["code"] == 200, f"用户注册失败: {reg_resp.text}"

    # 登录用户 A 获取 JWT
    login_resp = requests.post(f"{BASE_URL}/api/user/login", json={
        "username": username,
        "password": password
    })
    token_a = login_resp.json()["data"]["token"]
    user_id_a = login_resp.json()["data"]["userId"]
    headers_a = {"Authorization": f"Bearer {token_a}"}
    print(f"✅ 用户 A 登录成功: userId={user_id_a}, 获取 JWT Token 成功")

    # 用户 A 添加本人观演人
    raw_name_a = "范嘉润"
    raw_id_card_a = "440102199801011234"
    att_resp_a = requests.post(f"{BASE_URL}/api/user/attendee/add", headers=headers_a, params={
        "realName": raw_name_a,
        "idCard": raw_id_card_a,
        "phone": phone
    })
    assert att_resp_a.json()["code"] == 200, f"添加观演人失败: {att_resp_a.text}"

    # 获取观演人列表拿到 attendeeId
    att_list_resp = requests.get(f"{BASE_URL}/api/user/attendee/list", headers=headers_a)
    attendees_a = att_list_resp.json()["data"]
    attendee_id_a = attendees_a[0]["id"]
    print(f"📋 用户 A 常用观演人录入完毕: attendeeId={attendee_id_a}, 姓名={raw_name_a}, 证件={attendees_a[0]['idCardMasked']}")

    # 注册用户 B (用于越权测试对照组)
    username_b = f"hacker_{rand_suffix}"
    phone_b = f"139{rand_suffix}0000"
    requests.post(f"{BASE_URL}/api/user/register", json={"username": username_b, "phone": phone_b, "password": password})
    login_b = requests.post(f"{BASE_URL}/api/user/login", json={"username": username_b, "password": password})
    token_b = login_b.json()["data"]["token"]
    headers_b = {"Authorization": f"Bearer {token_b}"}

    # =========================================================================
    # [场景 3] 强实名秒杀下单：安全拦截与越权防护
    # =========================================================================
    log_step("场景 3: 强实名秒杀防线校验 (未登录拦截 401、越权盗用拦截 403、合法抢票)")
    
    # 1. 未登录拦截 (无 Token)
    no_auth_resp = requests.post(f"{BASE_URL}/api/seckill/order", params={"ticketCategoryId": CATEGORY_ID, "attendeeId": attendee_id_a})
    assert no_auth_resp.status_code == 401, f"未登录拦截失效: {no_auth_resp.status_code}"
    print("  🛡️ [防线 1 生效] 游客未登录抢票 -> HTTP 401 成功阻断熔断！")

    # 2. 越权盗用拦截 (用户 B 试图使用用户 A 的观演人身份)
    hack_resp = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_b, params={"ticketCategoryId": CATEGORY_ID, "attendeeId": attendee_id_a})
    assert hack_resp.json()["code"] == 403, f"越权防御失效: {hack_resp.text}"
    print(f"  🛡️ [防线 2 生效] 黑客越权使用他人观演人 -> 403 成功阻断: '{hack_resp.json()['message']}'")

    # 3. 合法抢票：用户 A 携带 JWT 勾选自己的观演人
    seckill_resp = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_a, params={"ticketCategoryId": CATEGORY_ID, "attendeeId": attendee_id_a})
    assert seckill_resp.json()["code"] == 200, f"合法秒杀失败: {seckill_resp.text}"
    order = seckill_resp.json()["data"]
    order_id = order["id"]
    print(f"  🟢 [秒杀斩获成功] 创建订单: orderId={order_id}, 金额=¥{order['orderPrice']}")

    # 校验数据库订单表中的实名快照
    db_order_check = run_psql(f"SELECT attendee_id, real_name, id_card_masked FROM d_ticket_order WHERE id={order_id};")
    print(f"  🔍 [数据库订单快照落盘] {db_order_check}")
    assert "440102********1234" in db_order_check, "订单快照中未找到正确脱敏身份证！"

    # =========================================================================
    # [场景 4] 沙箱收银台支付与 Kafka 异步履约出票
    # =========================================================================
    log_step("场景 4: 支付收银台结账 -> Outbox 事务保障 -> Kafka 异步绑定真实电子票")
    prepay_resp = requests.post(f"{BASE_URL}/api/pay/prepay", headers=headers_a, params={"orderId": order_id})
    assert prepay_resp.json()["code"] == 200, f"预支付失败: {prepay_resp.text}"
    pay_sn = prepay_resp.json()["data"]["paySn"]

    pay_resp = requests.post(f"{BASE_URL}/api/pay/mock/cashier", params={"paySn": pay_sn})
    assert pay_resp.json()["code"] == 200, f"收银台支付失败: {pay_resp.text}"
    print(f"  💳 [支付成功] 订单完成结账，流水号={pay_sn}")

    # 等待 Kafka 消费与电子票写入
    print("  ⏳ 等待 Kafka 异步出票消费者写入电子票资产...")
    time.sleep(2)

    db_ticket = run_psql(f"SELECT id, verify_code, real_name, id_card_masked, status FROM d_ticket_item WHERE order_id={order_id};")
    print(f"  🎫 [电子票履约成功] 数据库记录: {db_ticket}")
    assert "440102********1234" in db_ticket, "电子票未绑定真实观演人身份证！"
    assert "范嘉润" in db_ticket, "电子票未绑定真实观演人姓名！"

    ticket_parts = db_ticket.split("|")
    ticket_id = ticket_parts[0]
    verify_code = ticket_parts[1]

    # =========================================================================
    # [场景 5] 现场闸机人证票合一硬件核验 (防冒名入场)
    # =========================================================================
    log_step("场景 5: 现场闸机人证票三合一安全核验 (假冒拦截 vs 凭证放行)")

    # 1. 冒名顶替（持他人门票二维码，但身份证不对）
    fake_verify = requests.post(f"{BASE_URL}/api/tickets/verify", params={
        "verifyCode": verify_code,
        "gateNo": "GATE_A_01",
        "idCardNo": "440102199001019999" # 假冒者身份证
    })
    assert fake_verify.json()["code"] == 403, f"冒名拦截失败: {fake_verify.text}"
    print(f"  🛑 [闸机冒名红灯] 身份证不符拦截成功: 403 '{fake_verify.json()['message']}'")

    # 2. 真实观演人持本人身份证入场
    real_verify = requests.post(f"{BASE_URL}/api/tickets/verify", params={
        "verifyCode": verify_code,
        "gateNo": "GATE_A_01",
        "idCardNo": raw_id_card_a # 真实身份证
    })
    assert real_verify.json()["code"] == 200, f"真实核验失败: {real_verify.text}"
    print(f"  🟢 [闸机绿灯放行] 人证票三合一核验通过: 200 '{real_verify.json()['message']}'")

    # 3. 二次重复核销拦截
    dup_verify = requests.post(f"{BASE_URL}/api/tickets/verify", params={
        "verifyCode": verify_code,
        "gateNo": "GATE_A_01",
        "idCardNo": raw_id_card_a
    })
    assert dup_verify.json()["code"] == 400, f"防重拦截失败: {dup_verify.text}"
    print(f"  🛡️ [闸机防重复刷票] 重复核销拦截成功: 400 '{dup_verify.json()['message']}'")

    print("\n" + "="*75)
    print("  🏆 大圆满！端到端真实业务全链路 100% 验收通过！")
    print("  从选座到实名秒杀、支付履约、出票绑定、闸机人证合一彻底贯通！")
    print("="*75 + "\n")

if __name__ == "__main__":
    main()
