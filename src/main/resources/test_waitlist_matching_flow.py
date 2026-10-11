#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
大麦/12306 级候补购票与自动撮合捡漏引擎 - 全链路闭环验收套件
测试场景：
1. 用户A 成功秒杀并持有门票；
2. 票档库存耗尽（已售罄）；
3. 用户B 抢票失败，提交候补排队（进入 WAITING 队列，顺位第 1）；
4. 用户A 发起售后退票申请；
5. 核心拦截验证：释放库存被候补引擎直接截胡，撮合给用户B出票；
6. 物理对账验证：用户B 收到正式门票，候补单变为 MATCHED，且公共 Redis 库存未泄露回公海！
"""

import time
import requests
import redis
import json

BASE_URL = "http://localhost:8080"
PROGRAM_ID = 1
CATEGORY_ID = 1

REDIS_CLIENT = redis.Redis(host='127.0.0.1', port=6379, password='redis_password', decode_responses=True)

def print_step(title):
    print("\n" + "=" * 65)
    print(f"▶ {title}")
    print("=" * 65)

def register_and_login(username, password, real_name, id_card):
    # 注册或直接登录
    login_res = requests.post(f"{BASE_URL}/api/user/login", json={"username": username, "password": password})
    if login_res.status_code != 200 or login_res.json().get("code") != 200:
        requests.post(f"{BASE_URL}/api/user/register", json={
            "username": username, "password": password, "nickname": username, "phone": "13800000000"
        })
        login_res = requests.post(f"{BASE_URL}/api/user/login", json={"username": username, "password": password})

    token = login_res.json()["data"]["token"]
    user_id = login_res.json()["data"]["userId"]
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # 绑定常用观演人
    requests.post(f"{BASE_URL}/api/attendee/add", headers=headers, json={"realName": real_name, "idCard": id_card})
    att_res = requests.get(f"{BASE_URL}/api/attendee/list", headers=headers).json()
    attendee_id = att_res["data"][0]["id"]
    return headers, user_id, attendee_id

def run_test():
    print("🚀 [开始测试] 大麦级候补购票与退票撮合捡漏全链路工程验收")

    # 准备测试用户
    ts = int(time.time())
    headers_a, user_a, att_a = register_and_login(f"user_a_{ts}", "123456", "张三(原购票人)", "440101199001011234")
    headers_b, user_b, att_b = register_and_login(f"user_b_{ts}", "123456", "李四(候补拾遗人)", "440101199505055678")

    # 步骤 1：给用户 A 出一张票
    print_step("步骤 1：用户 A 下单并支付，获得 1 张有效电子票")
    # 确保有库存抢购
    REDIS_CLIENT.set(f"ticket:stock:{CATEGORY_ID}", 1)
    REDIS_CLIENT.delete(f"ticket:users:{CATEGORY_ID}")

    buy_res = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_a, json={
        "ticketCategoryId": CATEGORY_ID, "attendeeId": att_a
    }).json()
    assert buy_res.get("code") == 200, f"用户A秒杀失败: {buy_res}"
    order_id = buy_res["data"]["id"]

    # 模拟支付
    pay_res = requests.post(f"{BASE_URL}/api/pay/simulate", headers=headers_a, json={"orderId": order_id}).json()
    assert pay_res.get("code") == 200, f"用户A支付失败: {pay_res}"
    time.sleep(1) # 等待 Kafka 履约发票

    wallet_a = requests.get(f"{BASE_URL}/api/ticket/wallet", headers=headers_a).json()["data"]
    ticket_a = next(t for t in wallet_a if t["orderId"] == order_id)
    print(f"✅ 用户 A 成功持有门票: ticketItemId={ticket_a['id']}, 观演人={ticket_a['realName']}")

    # 步骤 2：模拟开票售罄
    print_step("步骤 2：模拟票档售罄状态 (Redis 库存归零)")
    REDIS_CLIENT.set(f"ticket:stock:{CATEGORY_ID}", 0)
    print(f"📦 票档当前公共 Redis 库存: {REDIS_CLIENT.get(f'ticket:stock:{CATEGORY_ID}')}")

    # 用户 B 尝试秒杀，预期被阻断
    fail_res = requests.post(f"{BASE_URL}/api/seckill/order", headers=headers_b, json={
        "ticketCategoryId": CATEGORY_ID, "attendeeId": att_b
    }).json()
    assert fail_res.get("code") != 200, "售罄状态秒杀应当被拦截"
    print(f"🛡️ 用户 B 秒杀如期被阻断: {fail_res.get('message')}")

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

    # 步骤 4：用户 A 申请退票，触发系统撮合拦截
    print_step("步骤 4：用户 A 办理退票，验证库存是否被撮合引擎截胡")
    refund_res = requests.post(f"{BASE_URL}/api/refund/{ticket_a['id']}", headers=headers_a).json()
    assert refund_res.get("code") == 200, f"退票申请失败: {refund_res}"
    print(f"✅ 用户 A 退票成功: 实退金额={refund_res['data']['actualRefund']}")

    time.sleep(1.5) # 等待撮合引擎生成正式订单与 Kafka 异步出票

    # 步骤 5：验证用户 B 候补状态是否自动跃迁为 MATCHED
    print_step("步骤 5：验证用户 B 候补单状态跃迁与正式履约")
    my_waitlist = requests.get(f"{BASE_URL}/api/waitlist/my", headers=headers_b).json()["data"]
    target_wl = next((w for w in my_waitlist if w["waitlistId"] == waitlist_id), None)
    assert target_wl is not None, "未找到用户 B 的候补记录"
    assert target_wl["status"] == 1, f"候补单状态预期为 1(已撮合出票), 实际为: {target_wl['status']}"
    print(f"🎉 状态校验通过：候补单 {waitlist_id} 状态已自动跃迁为 1 (撮合成功已出票)！")

    # 步骤 6：验证用户 B 票夹中是否已入账正式门票
    wallet_b = requests.get(f"{BASE_URL}/api/ticket/wallet", headers=headers_b).json()["data"]
    assert len(wallet_b) > 0, "用户 B 票夹为空，未完成自动履约！"
    matched_ticket = wallet_b[0]
    assert matched_ticket["realName"] == "李四(候补拾遗人)", f"门票观演人绑定错误: {matched_ticket}"
    print(f"🎟️ 履约校验通过：用户 B 电子票夹成功收到真实门票: ticketItemId={matched_ticket['id']}, 观演人={matched_ticket['realName']}")

    # 步骤 7：验证公共 Redis 库存绝对零泄露（没有流向公海给黄牛）
    stock_after = int(REDIS_CLIENT.get(f"ticket:stock:{CATEGORY_ID}") or 0)
    assert stock_after == 0, f"致命缺陷：退票库存泄漏到公共公海！当前库存={stock_after}"
    print(f"🔒 防黄牛公海泄露校验通过：公共 Redis 库存依旧保持为 {stock_after}，退票已被候补精准截胡！")

    print("\n" + "=" * 65)
    print("🏆 [全景验收通过] 大麦/12306 级候补与自动撮合捡漏引擎 100% 达成工业级闭环！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
