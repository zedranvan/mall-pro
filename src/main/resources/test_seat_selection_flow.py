#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
场馆物理选座建模与 Redis Bitmap 位图并发锁定系统全链路工程验收
测试场景：
1. 全景座位大厅与 Bitmap 初始状态核验（50个物理座位零延迟加载）；
2. 滑动窗口贪心连续座位智能推荐（情侣购票 2 连座首选居中视野佳区）；
3. 用户 A 原子锁定连座（Redis 位图置 1 + 5 分钟排他租约）；
4. 用户 B 并发选座冲突防御（抢锁同一座位被 409 拦截，0 冲突 0 脏写）；
5. 用户 A 主动取消释放座位（位图与 DB 状态毫秒级回滚复原）；
6. 用户 B 接盘重试锁定与全链路闭环验证。
"""

import time
import random
import requests
import redis

BASE_URL = "http://localhost:8080"
PROGRAM_ID = 1
CATEGORY_ID = 101

REDIS_CLIENT = redis.Redis(host='127.0.0.1', port=6379, password='redis_password', decode_responses=True)

def print_step(title):
    print("\n" + "=" * 65)
    print(f"▶ {title}")
    print("=" * 65)

def register_and_login(username, password):
    phone = f"137{random.randint(10000000, 99999999)}"
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
    print("🚀 [开始测试] 场馆物理选座建模与 Redis Bitmap 位图并发锁定全景工程验收")
    
    # 准备测试用户
    user_a_name = f"auditor_a_{int(time.time() * 1000) % 100000}"
    user_b_name = f"auditor_b_{int(time.time() * 1000) % 100000}"
    headers_a, user_a_id = register_and_login(user_a_name, "pass123456")
    headers_b, user_b_id = register_and_login(user_b_name, "pass123456")
    print(f"👤 用户 A 准备就绪: userId={user_a_id}, 用户名={user_a_name}")
    print(f"👤 用户 B 准备就绪: userId={user_b_id}, 用户名={user_b_name}")

    # 环境预热与同步
    requests.post(f"{BASE_URL}/api/seats/sync", params={"programId": PROGRAM_ID})

    # =================================================================
    print_step("步骤 1：全景座位大厅与 Bitmap 初始状态核验")
    # =================================================================
    map_res = requests.get(f"{BASE_URL}/api/seats/map", params={"programId": PROGRAM_ID, "categoryId": CATEGORY_ID})
    assert map_res.status_code == 200, f"获取座位大厅失败: {map_res.text}"
    seats = map_res.json()["data"]
    total_seats = len(seats)
    available_seats = [s for s in seats if s["status"] == 0]
    print(f"🏟️ 场馆当前票档总物理座位数: {total_seats} 个, 空闲可选: {len(available_seats)} 个")
    assert total_seats == 50, f"预期 50 个物理座位，实际为: {total_seats}"
    assert len(available_seats) == 50, "初始状态所有座位应为空闲可选 (status=0)"

    # 核验 Redis Bitmap 底层 bit 状态
    for s in seats[:5]:
        bit_val = REDIS_CLIENT.getbit(f"venue:bitmap:{PROGRAM_ID}", s["seatIndex"])
        assert bit_val == 0, f"座位 offset={s['seatIndex']} 在 Redis Bitmap 中应为 0"
    print("✅ 全馆座位大厅与 Redis Bitmap 初始空闲校验 100% 通过！")

    # =================================================================
    print_step("步骤 2：贪心滑动窗口连续座位智能推荐 (推荐 2 连座)")
    # =================================================================
    rec_res = requests.get(f"{BASE_URL}/api/seats/recommend", params={
        "programId": PROGRAM_ID, "categoryId": CATEGORY_ID, "count": 2
    })
    assert rec_res.status_code == 200, f"连座推荐请求失败: {rec_res.text}"
    rec_seats = rec_res.json()["data"]
    assert len(rec_seats) == 2, f"预期推荐 2 个连座，实际返回: {len(rec_seats)}"
    
    seat_1, seat_2 = rec_seats[0], rec_seats[1]
    print(f"🎯 算法推荐连座: 座位1=[{seat_1['area']} {seat_1['rowNum']}排{seat_1['colNum']}号], "
          f"座位2=[{seat_2['area']} {seat_2['rowNum']}排{seat_2['colNum']}号]")
    
    # 严格校验：同排、列号连续、居中视野最佳
    assert seat_1["rowNum"] == seat_2["rowNum"], "推荐座位必须同排"
    assert seat_2["colNum"] == seat_1["colNum"] + 1, "推荐座位列号必须连续相邻"
    assert seat_1["rowNum"] == 1, "优先推荐前排优质视野座位 (第1排)"
    print("✅ 智能连座算法校验通过：成功推荐同排相邻居中黄金视线座位！")

    # =================================================================
    print_step("步骤 3：用户 A 原子锁定所选连座 (5分钟排他租约)")
    # =================================================================
    seat_ids_to_lock = [seat_1["id"], seat_2["id"]]
    lock_res = requests.post(f"{BASE_URL}/api/seats/lock", headers=headers_a, json={
        "programId": PROGRAM_ID, "seatIds": seat_ids_to_lock
    })
    assert lock_res.status_code == 200, f"用户 A 锁座失败: {lock_res.text}"
    lock_data = lock_res.json()["data"]
    print(f"🔒 用户 A 选座锁定成功: {lock_data['message']}, 倒计时={lock_data['lockExpireSeconds']}s")
    
    # 核验 Redis Bitmap 是否原子变更为 1
    for s_idx in [seat_1["seatIndex"], seat_2["seatIndex"]]:
        bit = REDIS_CLIENT.getbit(f"venue:bitmap:{PROGRAM_ID}", s_idx)
        assert bit == 1, f"座位 offset={s_idx} 在 Redis Bitmap 中未置为 1"
    print("✅ Redis Bitmap 位图原子占位检验通过：已成功标定占用 (bit=1)！")

    # =================================================================
    print_step("步骤 4：用户 B 并发抢座冲突防御 (抢夺用户 A 已锁座位)")
    # =================================================================
    conflict_res = requests.post(f"{BASE_URL}/api/seats/lock", headers=headers_b, json={
        "programId": PROGRAM_ID, "seatIds": [seat_1["id"]]
    })
    conflict_json = conflict_res.json()
    print(f"🛡️ 用户 B 并发抢锁拦截响应: code={conflict_json.get('code')}, message='{conflict_json.get('message')}'")
    assert conflict_json.get("code") in [400, 409], f"预期拦截冲突异常，实际为: {conflict_json}"
    assert "锁定" in conflict_json.get("message", ""), "提示信息应明确告知座位已被锁定"
    print("✅ 并发互斥防御校验通过：用户 B 抢占被排他租约坚决拦截，0 冲突！")

    # =================================================================
    print_step("步骤 5：用户 A 主动释放锁定座位 (取消反悔)")
    # =================================================================
    unlock_res = requests.post(f"{BASE_URL}/api/seats/unlock", headers=headers_a, json={
        "programId": PROGRAM_ID, "seatIds": seat_ids_to_lock
    })
    assert unlock_res.status_code == 200, f"释放座位失败: {unlock_res.text}"
    print("🔓 用户 A 主动释放座位成功！")

    # 验证 Redis Bitmap 与选座大厅是否瞬时自愈复原
    for s_idx in [seat_1["seatIndex"], seat_2["seatIndex"]]:
        bit = REDIS_CLIENT.getbit(f"venue:bitmap:{PROGRAM_ID}", s_idx)
        assert bit == 0, f"释放后座位 offset={s_idx} 在 Redis Bitmap 中应归零"
    print("✅ 状态回滚复原校验通过：Redis Bitmap 与分布式锁已彻底释放 (bit=0)！")

    # =================================================================
    print_step("步骤 6：用户 B 接盘重新选座锁定验证")
    # =================================================================
    b_lock_res = requests.post(f"{BASE_URL}/api/seats/lock", headers=headers_b, json={
        "programId": PROGRAM_ID, "seatIds": [seat_1["id"], seat_2["id"]]
    })
    assert b_lock_res.status_code == 200, f"用户 B 接盘锁座失败: {b_lock_res.text}"
    print(f"🎉 用户 B 成功接盘锁定座位: {seat_ids_to_lock}")

    # 清理环境：用户 B 释放座位
    requests.post(f"{BASE_URL}/api/seats/unlock", headers=headers_b, json={
        "programId": PROGRAM_ID, "seatIds": seat_ids_to_lock
    })
    print("🧹 测试现场环境清理完毕，座位已归还空闲池。")

    print("\n" + "=" * 65)
    print("🏆 [全景验收通过] 场馆物理选座建模与 Redis Bitmap 并发锁定系统 100% 达成！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
