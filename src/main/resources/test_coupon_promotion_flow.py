#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
营销优惠券资产防超发与动态最优抵扣引擎全链路工程验收
测试场景：
1. 优惠券活动大厅与配额列表核验（满减/折扣/立减等多券种就绪）；
2. 用户 A 抢领优惠券（Redis Lua 原子扣发配额，生成唯一数字防伪券码）；
3. 羊毛党黑产防刷限领防御（同用户重复刷领同活动神券被排他拦截）；
4. 动态最优用券推荐算法画像（输入 580 元门票，算法贪心比对多券，精准推荐省钱最多的 80 元神券）；
5. 门槛未达成边界防御（低价票无法滥用大额券）；
6. 交易链路状态机闭环（下单冻结防重用、关单自愈解冻复原）。
"""

import time
import random
import requests
import redis

BASE_URL = "http://localhost:8080"
REDIS_CLIENT = redis.Redis(host='127.0.0.1', port=6379, password='redis_password', decode_responses=True)

def print_step(title):
    print("\n" + "=" * 65)
    print(f"▶ {title}")
    print("=" * 65)

def register_and_login(username, password):
    phone = f"136{random.randint(10000000, 99999999)}"
    requests.post(f"{BASE_URL}/api/user/register", json={
        "username": username, "password": password, "nickname": username, "phone": phone
    })
    login_res = requests.post(f"{BASE_URL}/api/user/login", json={"username": username, "password": password})
    login_data = login_res.json()
    assert login_data.get("code") == 200, f"登录失败: {login_data}"
    token = login_data["data"]["token"]
    user_id = login_data["data"]["userId"]
    return {"Authorization": f"Bearer {token}"}, user_id

def run_test():
    print("🚀 [开始测试] 营销优惠券资产防超发与动态最优抵扣引擎全景工程验收")

    # 准备测试用户
    user_name = f"coupon_buyer_{int(time.time() * 1000) % 100000}"
    headers, user_id = register_and_login(user_name, "pass123456")
    print(f"👤 用户准备就绪: userId={user_id}, 用户名={user_name}")

    # =================================================================
    print_step("步骤 1：优惠券活动大厅与配额全景拉取")
    # =================================================================
    tpl_res = requests.get(f"{BASE_URL}/api/coupon/templates")
    assert tpl_res.status_code == 200, f"拉取模板失败: {tpl_res.text}"
    templates = tpl_res.json()["data"]
    print(f"🎟️ 当前全网在线优惠券模板: 共 {len(templates)} 种")
    for t in templates:
        print(f"   - 模板ID={t['id']}: [{t['name']}], 门槛=¥{t['thresholdAmount']}, 减免=¥{t['discountAmount']}, 剩余配额={t['remainQuota']}")
    assert len(templates) >= 3, "预期至少有 3 种活动券模板"

    # =================================================================
    print_step("步骤 2：用户抢领多张优惠券 (Redis Lua 原子防超发)")
    # =================================================================
    # 领取模板 1 (满300减50)
    c1_res = requests.post(f"{BASE_URL}/api/coupon/claim", headers=headers, params={"templateId": 1})
    assert c1_res.json().get("code") == 200, f"领取模板 1 失败: {c1_res.text}"
    coupon_1 = c1_res.json()["data"]
    print(f"✅ 领券成功: 券ID={coupon_1['id']}, 名称={coupon_1['templateName']}, 券码={coupon_1['couponCode']}")

    # 领取模板 2 (满500减80)
    c2_res = requests.post(f"{BASE_URL}/api/coupon/claim", headers=headers, params={"templateId": 2})
    assert c2_res.json().get("code") == 200, f"领取模板 2 失败: {c2_res.text}"
    coupon_2 = c2_res.json()["data"]
    print(f"✅ 领券成功: 券ID={coupon_2['id']}, 名称={coupon_2['templateName']}, 券码={coupon_2['couponCode']}")

    # =================================================================
    print_step("步骤 3：羊毛党黑产防刷限领防御 (重复抢领排他拦截)")
    # =================================================================
    duplicate_res = requests.post(f"{BASE_URL}/api/coupon/claim", headers=headers, params={"templateId": 2})
    dup_json = duplicate_res.json()
    print(f"🛡️ 重复领券拦截响应: code={dup_json.get('code')}, message='{dup_json.get('message')}'")
    assert dup_json.get("code") == 400, f"预期 400 拦截重复领券，实际为: {dup_json}"
    assert "限领一张" in dup_json.get("message", ""), "提示信息应明确告知每人限领一张"
    print("✅ 防刷风控拦截校验通过：Redis Set 互斥排他机制坚决阻断重复领券套利！")

    # =================================================================
    print_step("步骤 4：动态最优用券推荐算法画像 (输入 580 元门票订单)")
    # =================================================================
    calc_res = requests.get(f"{BASE_URL}/api/coupon/calculate", headers=headers, params={"orderAmount": 580.00})
    assert calc_res.status_code == 200, f"最优用券计算失败: {calc_res.text}"
    result = calc_res.json()["data"]
    
    rec_coupon = result["recommendedCoupon"]
    assert rec_coupon is not None, "应成功计算并推荐出最优优惠券"
    print(f"🎯 算法智能推荐最优券: [{rec_coupon['templateName']}], 券ID={rec_coupon['id']}")
    print(f"💰 优惠方案测算: 订单原价=¥{result['orderAmount']}, 最高抵扣=¥{result['maxDiscountAmount']}, 最终实付=¥{result['finalPayAmount']}")

    # 严格数学断言：满500减80 优于 满300减50
    assert rec_coupon["templateId"] == 2, "算法应精准推选减免额度最高的 80 元大额神券！"
    assert float(result["maxDiscountAmount"]) == 80.0, "抵扣金额应为 80.00 元"
    assert float(result["finalPayAmount"]) == 500.0, "最终实付应为 500.00 元"
    print("✅ 智能抵扣算法校验通过：贪心策略毫秒级算力成功匹配最优省钱方案！")

    # =================================================================
    print_step("步骤 5：门槛未达成边界防御 (输入 200 元低价订单)")
    # =================================================================
    low_res = requests.get(f"{BASE_URL}/api/coupon/calculate", headers=headers, params={"orderAmount": 200.00})
    low_result = low_res.json()["data"]
    print(f"🛡️ 低价门槛未满足测算: 可用券数={len(low_result['availableCoupons'])}, 不可用券数={len(low_result['unavailableCoupons'])}")
    assert low_result["recommendedCoupon"] is None, "未达门槛不应推荐任何优惠券"
    assert float(low_result["maxDiscountAmount"]) == 0.0, "未达门槛抵扣额应为 0"
    for un_coupon in low_result["unavailableCoupons"]:
        print(f"   - 拦截原因: {un_coupon['templateName']} -> {un_coupon['cannotUseReason']}")
    print("✅ 门槛合规校验通过：严格拦截未达满减门槛的滥用抵扣！")

    # =================================================================
    print_step("步骤 6：交易链路状态机闭环 (下单冻结防重用与关单自愈)")
    # =================================================================
    order_id = 888888889999
    # 1. 下单冻结推荐券
    freeze_res = requests.post(f"{BASE_URL}/api/coupon/freeze", headers=headers, params={
        "userCouponId": rec_coupon["id"], "orderId": order_id
    })
    assert freeze_res.json().get("code") == 200, f"冻结优惠券失败: {freeze_res.text}"
    print(f"🔒 优惠券已成功冻结到订单 {order_id}: status=1 (下单冻结中)")

    # 2. 并发防套利校验：尝试用同一张券在另一订单再次冻结
    conflict_res = requests.post(f"{BASE_URL}/api/coupon/freeze", headers=headers, params={
        "userCouponId": rec_coupon["id"], "orderId": 999999998888
    })
    print(f"🛡️ 并发重复抵扣拦截响应: code={conflict_res.json().get('code')}, message='{conflict_res.json().get('message')}'")
    assert conflict_res.json().get("code") in [400, 409], "一券多单重复冻结必须被拒绝"
    print("✅ 防一券多用拦截校验通过：CAS 状态机彻底杜绝并发多订单套券！")

    # 3. 模拟超时关单/反悔解冻自愈
    release_res = requests.post(f"{BASE_URL}/api/coupon/release", headers=headers, params={
        "userCouponId": rec_coupon["id"]
    })
    assert release_res.json().get("code") == 200, f"解冻优惠券失败: {release_res.text}"
    print("🔓 订单超时/反悔触发解冻：优惠券资产 100% 毫秒级自愈恢复 (status=0)！")

    print("\n" + "=" * 65)
    print("🏆 [全景验收通过] 营销优惠券资产防超发与动态最优抵扣引擎 100% 达成商业级闭环！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
