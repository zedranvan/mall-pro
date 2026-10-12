#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
大麦/12306 级候补购票与退票撮合捡漏全链路工程验收
测试场景：
1. 用户 A 成功秒杀并支付，持有有效门票；
2. 票档售罄（Redis 库存清零）；
3. 用户 B 秒杀被 400 阻断，提交候补排队（状态 0，顺位 1）；
4. 用户 A 申请退票，触发系统候补撮合拦截；
5. 验证用户 B 候补状态自动跃迁为 MATCHED(1)；
6. 验证用户 B 电子票夹成功收到印有其观演人姓名的门票；
7. 验证公共 Redis 库存依旧为 0（绝对零泄露给公海黄牛）！
"""

import time
import random
import requests
import redis
import json

BASE_URL = "http://localhost:8080"
PROGRAM_ID = 1
CATEGORY_ID = 101

REDIS_CLIENT = redis.Redis(host='127.0.0.1', port=6379, password='redis_password', decode_responses=True)

def print_step(title):
    print("\n" + "=" * 65)
    print(f"▶ {title}")
    print("=" * 65)

def register_and_login(username, password, real_name, id_card):
    # 随机手机号防重
    phone = f"139{random.randint(10000000, 99999999)}"
    
    # 1. 注册
    reg_res = requests.post(f"{BASE_URL}/api/user/register", json={
        "username": username, "password": password, "nickname": username, "phone": phone
    })
    
    # 2. 登录
    login_res = requests.post(f"{BASE_URL}/api/user/login", json={"username": username, "password": password})
    login_data = login_res.json()
    assert login_data.get("code") == 200, f"登录失败: {login_data}"
    
    token = login_data["data"]["token"]
    user_id = login_data["data"]["userId"]
    headers = {"Authorization": f"Bearer {token}"}

    # 3. 录入常用实名观演人 (/api/user/attendee/add)
    add_res = requests.post(f"{BASE_URL}/api/user/attendee/add", headers=headers, params={
        "realName": real_name, "idCard": id_card, "phone": phone
    })
    assert add_res.json().get("code") == 200, f"添加观演人失败: {add_res.text}"

    # 4. 获取观演人列表拿到 attendeeId
    att_res = requests.get(f"{BASE_URL}/api/user/attendee/list", headers=headers).json()
    attendee_id = att_res["data"][0]["id"]
    return headers, user_id, attendee_id

def run_test():
    print("🚀 [开始测试] 大麦/12306 级候补购票与退票撮合捡漏全链路工程验收")

    ts = int(time.time() * 1000) % 1000000
    headers_a, user_a, att_a = register_and_login(f"buyer_a_{ts}", "Password123!", "张三(原购票人)", "440101199001011234")
    headers_b, user_b, att_b = register_and_login(f"waiter_b_{ts}", "Password123!", "李四(候补拾遗人)", "440101199505055678")
    print(f"👤 用户 A (ID={user_a}, 观演人ID={att_a}) 准备就绪")
    print(f"👤 用户 B (ID={user_b}, 观演人ID={att_b}) 准备就绪")

    # 步骤 1：给用户 A 出一张票
    print_step("步骤 1：用户 A 下单并支付，获得 1 张有效电子票")
    REDIS_CLIENT.set(f"ticket:stock:{CATEGORY_ID}", 1)
    REDIS_CLIENT.delete(f"ticket:users:{CATEGORY_ID}")

    buy_res = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_a, params={
        "ticketCategoryId": CATEGORY_ID, "attendeeId": att_a
    }).json()
    assert buy_res.get("code") == 200, f"用户 A 秒杀失败: {buy_res}"
    order_id = buy_res["data"]["id"]

    # 支付
    prepay = requests.post(f"{BASE_URL}/api/pay/prepay", headers=headers_a, params={"orderId": order_id}).json()
    pay_sn = prepay["data"]["paySn"]
    pay_res = requests.post(f"{BASE_URL}/api/pay/mock/cashier", params={"paySn": pay_sn}).json()
    assert pay_res.get("code") == 200, f"支付失败: {pay_res}"
    print("  ⏳ 等待 Kafka 异步出票消费者写入电子票资产...")
    time.sleep(2)

    wallet_a = requests.get(f"{BASE_URL}/api/tickets/my-tickets", headers=headers_a, params={"userId": user_a}).json()["data"]
    ticket_a = next(t for t in wallet_a if t["orderId"] == order_id)
    print(f"✅ 用户 A 成功持有门票: ticketItemId={ticket_a['id']}, 观演人={ticket_a['realName']}")

    # 步骤 2：模拟开票售罄
    print_step("步骤 2：模拟票档售罄状态 (Redis 库存清零)")
    REDIS_CLIENT.set(f"ticket:stock:{CATEGORY_ID}", 0)
    print(f"📦 票档当前公共 Redis 库存: {REDIS_CLIENT.get(f'ticket:stock:{CATEGORY_ID}')}")

    # 用户 B 尝试秒杀，预期被阻断
    fail_res = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_b, params={
        "ticketCategoryId": CATEGORY_ID, "attendeeId": att_b
    }).json()
    assert fail_res.get("code") != 200, "售罄状态秒杀应当被拦截"
    print(f"🛡️ 用户 B 秒杀如期被 400 阻断: '{fail_res.get('message')}'")

    # 步骤 3：用户 B 提交候补排队
    print_step("步骤 3：用户 B 提交候补排队申请")
    waitlist_res = requests.post(f"{BASE_URL}/api/waitlist/submit", headers=headers_b, json={
        "programId": PROGRAM_ID,
        "ticketCategoryId": CATEGORY_ID,
        "attendeeId": att_b
    }).json()
    assert waitlist_res.get("code") == 200, f"候补提交失败: {waitlist_res}"
    waitlist_data = waitlist_res["data"]
    waitlist_id = waitlist_data["waitlistId"]
    print(f"✅ 用户 B 候补排队成功: waitlistId={waitlist_id}, 顺位号={waitlist_data['queuePosition']}, 状态={waitlist_data['status']}(排队中)")

    # 步骤 4：用户 A 申请退票，触发撮合拦截
    print_step("步骤 4：用户 A 办理退票，验证库存是否被撮合引擎截胡")
    refund_res = requests.post(f"{BASE_URL}/api/tickets/refund", headers=headers_a, params={
        "ticketItemId": ticket_a["id"],
        "userId": user_a
    }).json()
    assert refund_res.get("code") == 200, f"退票申请失败: {refund_res}"
    print(f"✅ 用户 A 退票成功: 实退金额=¥{refund_res['data']['actualRefund']}, 规则={refund_res['data']['refundRuleDesc']}")

    print("  ⏳ 等待候补撮合引擎生成正式订单与 Kafka 异步履约出票...")
    time.sleep(2)

    # 步骤 5：验证用户 B 候补状态是否自动跃迁为 MATCHED
    print_step("步骤 5：验证用户 B 候补单状态跃迁与履约")
    my_waitlist = requests.get(f"{BASE_URL}/api/waitlist/my", headers=headers_b).json()["data"]
    target_wl = next((w for w in my_waitlist if w["waitlistId"] == waitlist_id), None)
    assert target_wl is not None, "未找到用户 B 的候补记录"
    assert target_wl["status"] == 1, f"候补单状态预期为 1(已撮合出票), 实际为: {target_wl['status']}"
    print(f"🎉 状态校验通过：候补单 {waitlist_id} 状态已自动跃迁为 1 (撮合成功已出票)！")

    # 步骤 6：验证用户 B 票夹中是否已入账正式门票
    wallet_b = requests.get(f"{BASE_URL}/api/tickets/my-tickets", headers=headers_b, params={"userId": user_b}).json()["data"]
    assert len(wallet_b) > 0, "用户 B 票夹为空，未完成自动履约！"
    matched_ticket = wallet_b[0]
    assert matched_ticket["realName"] == "李四(候补拾遗人)", f"门票观演人绑定错误: {matched_ticket}"
    print(f"🎟️ 履约校验通过：用户 B 电子票夹成功收到真实门票: ticketItemId={matched_ticket['id']}, 观演人={matched_ticket['realName']}")

    # 步骤 7：验证公共 Redis 库存绝对零泄露
    stock_after = int(REDIS_CLIENT.get(f"ticket:stock:{CATEGORY_ID}") or 0)
    assert stock_after == 0, f"致命缺陷：退票库存泄漏到公共公海！当前库存={stock_after}"
    print(f"🔒 防黄牛公海泄露校验通过：公共 Redis 库存依旧保持为 {stock_after}，退票已被候补精准截胡！")

    print("\n" + "=" * 65)
    print("🏆 [全景验收通过] 大麦/12306 级候补与自动撮合捡漏引擎 100% 达成工业级闭环！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
