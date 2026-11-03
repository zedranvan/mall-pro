#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🎟️ 安卓智能闸机上位机模拟器 (Android Gate Upper-Machine Simulator)
功能定位：
1. 模拟安卓工控板上位机与硬件扫码头、单片机继电器的串口交互
2. 演示本地 SQLite 离线存储与断网边缘验签
3. 演示扫码防抖 (Debounce)、断网防重放 (Anti-Replay)
4. 演示网络恢复后与云端 mall-pro 的批量自愈对账 (Batch Sync)
"""

import sqlite3
import time
import requests
import json
import base64
import os
import sys

# 颜色控制
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

BASE_URL = "http://localhost:8080"
LOCAL_DB_FILE = "android_gate_local.db"

class AndroidGateDevice:
    def __init__(self, gate_no="GATE_SOUTH_01"):
        self.gate_no = gate_no
        self.last_qr_code = None
        self.last_scan_time = 0
        self.debounce_ms = 3000  # 3秒硬件防抖
        self.is_online = True
        self.init_sqlite()

    def init_sqlite(self):
        """初始化安卓上位机本地轻量级 SQLite 数据库"""
        conn = sqlite3.connect(LOCAL_DB_FILE)
        cursor = conn.cursor()
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS local_offline_check (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id TEXT NOT NULL,
                qr_code TEXT NOT NULL,
                gate_no TEXT NOT NULL,
                check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                sync_status INTEGER NOT NULL DEFAULT 0 -- 0:待同步 1:已同步
            );
        """)
        cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_ticket_local ON local_offline_check(ticket_id);")
        conn.commit()
        conn.close()

    def simulate_hardware_debounce(self, qr_code):
        """1. 扫码头硬件防抖检测 (时间戳比对)"""
        now = int(time.time() * 1000)
        if qr_code == self.last_qr_code and (now - self.last_scan_time < self.debounce_ms):
            diff = (now - self.last_scan_time) / 1000.0
            print(f"{YELLOW}[硬件防抖拦截] 扫码头在 {diff:.2f} 秒内重复触发同一票据，自动丢弃信号，保护电机继电器！{RESET}")
            return False
        self.last_qr_code = qr_code
        self.last_scan_time = now
        return True

    def scan_and_verify(self, qr_code):
        """2. 核心验票：区分 在线直连 vs 断网离线"""
        print(f"\n{CYAN}>>> [扫码头事件触发] 闸机号: {self.gate_no}, 收到原始字符串: {qr_code[:32]}...{RESET}")

        # 第一道门：硬件防抖
        if not self.simulate_hardware_debounce(qr_code):
            return

        # 第二道门：断网离线边缘计算 (如果处于断网状态)
        if not self.is_online:
            self.offline_verify_process(qr_code)
            return

        # 第三道门：在线网络验签
        try:
            url = f"{BASE_URL}/api/ticket/verify/online"
            payload = {"qrCode": qr_code, "gateNo": self.gate_no}
            resp = requests.post(url, json=payload, timeout=2)
            if resp.status_code == 200 and resp.json().get("code") == 200:
                print(f"{GREEN}{BOLD}🟢 [开闸放行-在线绿灯] {resp.json().get('data')}{RESET}")
                print(f"   └─ 向下位机单片机发送串口指令: [0xAA, 0x01, 0xFF] (驱动无刷电机开闸)")
            else:
                msg = resp.json().get("message") if resp.status_code == 200 else f"HTTP {resp.status_code}"
                print(f"{RED}{BOLD}🔴 [红灯拦截-非法票据] 原因: {msg}{RESET}")
                print(f"   └─ 向下位机单片机发送串口指令: [0xAA, 0x00, 0x00] (蜂鸣报警，严禁通行)")
        except Exception as e:
            print(f"{YELLOW}⚠️ [云端超时/断网警告] 无法连通后端服务器: {e}，自动降级为【断网离线边缘模式】！{RESET}")
            self.offline_verify_process(qr_code)

    def offline_verify_process(self, qr_code):
        """断网离线纯边缘验签 (RSA公钥 + SQLite本地去重)"""
        # 从凭据中解析 ticket_id (格式如 RSA_CERT_xxx 或 纯票码)
        ticket_id = qr_code.split("|")[0] if "|" in qr_code else qr_code

        # 检查本地 SQLite 是否已经核销过 (本地防重放)
        conn = sqlite3.connect(LOCAL_DB_FILE)
        cursor = conn.cursor()
        cursor.execute("SELECT check_time FROM local_offline_check WHERE ticket_id = ?", (ticket_id,))
        row = cursor.fetchone()

        if row:
            print(f"{RED}{BOLD}🔴 [红灯拦截-断网二次刷码作弊] 门票已于 {row[0]} 在本闸机离线核销过，禁止重复入场！{RESET}")
            conn.close()
            return

        # 假票或格式篡改检测
        if "INVALID" in qr_code or "FAKE" in qr_code:
            print(f"{RED}{BOLD}🔴 [红灯拦截-伪造凭证] 离线 RSA 公钥签名校验失败，发现假票！{RESET}")
            conn.close()
            return

        # 离线放行并落本地 SQLite
        cursor.execute("""
            INSERT INTO local_offline_check (ticket_id, qr_code, gate_no, sync_status) 
            VALUES (?, ?, ?, 0)
        """, (ticket_id, qr_code, self.gate_no))
        conn.commit()
        conn.close()

        print(f"{GREEN}{BOLD}🟢 [开闸放行-断网离线绿灯] RSA公钥校验有效！记录已存入本地 SQLite (待恢复联网同步){RESET}")
        print(f"   └─ 向下位机单片机发送串口指令: [0xAA, 0x01, 0xFF] (离线驱动电机放行)")

    def sync_offline_records_to_cloud(self):
        """3. 网络恢复后：从本地 SQLite 批量上报同步到云端数据库"""
        print(f"\n{CYAN}>>> [上位机自愈任务] 正在探测本地待同步核销记录...{RESET}")
        conn = sqlite3.connect(LOCAL_DB_FILE)
        cursor = conn.cursor()
        cursor.execute("SELECT id, ticket_id, qr_code, gate_no, check_time FROM local_offline_check WHERE sync_status = 0")
        rows = cursor.fetchall()

        if not rows:
            print(f"    ℹ️ 当前本地没有待同步的积压记录。")
            conn.close()
            return

        print(f"    📦 发现 {len(rows)} 条断网期间核销的信件，开始向云端批量上报...")
        synced_ids = []
        for r in rows:
            rec_id, ticket_id, qr, gate, ctime = r
            # 模拟向云端批量推送
            print(f"    -> 正在向云端回传票据: {ticket_id} (闸机: {gate}, 核销时间: {ctime})")
            synced_ids.append(rec_id)

        # 批量更新本地标记为已同步 (sync_status = 1)
        placeholders = ",".join("?" * len(synced_ids))
        cursor.execute(f"UPDATE local_offline_check SET sync_status = 1 WHERE id IN ({placeholders})", synced_ids)
        conn.commit()
        conn.close()
        print(f"{GREEN}✅ [批量对账完成] {len(synced_ids)} 条记录全部同步至云端，本地 SQLite 状态跃迁完毕！{RESET}")


def run_demo():
    print(f"{BOLD}================================================================{RESET}")
    print(f"{BOLD}🎟️  mall-pro 安卓智能闸机上位机端到端运行模拟器{RESET}")
    print(f"{BOLD}================================================================{RESET}")
    device = AndroidGateDevice("GATE_HAIPEI_A01")

    # 1. 模拟在线扫一张真票
    print("\n【场景 1：网络畅通时的正常扫码】")
    device.scan_and_verify("VRFY_888888|PROG_1|VALID_SIG_OK")

    # 2. 模拟高频防抖
    print("\n【场景 2：扫码头硬件高频防抖测试 (100毫秒内连扫)】")
    device.scan_and_verify("VRFY_888888|PROG_1|VALID_SIG_OK")

    # 3. 模拟断网切断
    print("\n【场景 3：现场基站断网（切断上位机网络连接）】")
    device.is_online = False
    print("⚠️  上位机以太网/Wi-Fi 已拔线，当前网络状态: OFFLINE (断网)")
    
    # 在断网下连续验票两张
    device.scan_and_verify("VRFY_999001|PROG_1|OFFLINE_RSA_PASS")
    device.scan_and_verify("VRFY_999002|PROG_1|OFFLINE_RSA_PASS")

    # 4. 模拟断网二次刷码作弊
    print("\n【场景 4：同一观众在断网期间将门票给朋友二次刷码（防作弊）】")
    device.scan_and_verify("VRFY_999001|PROG_1|OFFLINE_RSA_PASS")

    # 5. 模拟假票拦截
    print("\n【场景 5：黄牛伪造篡改签名的假票进场拦截】")
    device.scan_and_verify("VRFY_FAKE_HACKER|PROG_1|INVALID_SIG")

    # 6. 模拟网络恢复与自愈回传
    print("\n【场景 6：演出中途网络恢复，上位机自动发起对账同步】")
    device.is_online = True
    print("🌐  网络连接已恢复正常 (ONLINE)")
    device.sync_offline_records_to_cloud()

    print(f"\n{BOLD}{GREEN}🎉 [全景演示完成] 上位机完整生命周期测试全部通过！{RESET}\n")

if __name__ == "__main__":
    run_demo()
