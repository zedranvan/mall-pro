import urllib.request
import urllib.error
import json
import threading
import time

BASE_URL = "http://localhost:8080/api/orders/create?ticketCategoryId=102&count=1&userId="
TOTAL_USERS = 100  # 100 位歌迷同时抢购 20 张 VIP 门票
barrier = threading.Barrier(TOTAL_USERS) # 线程栅栏：发令枪

success_count = 0
fail_count = 0
error_count = 0
lock = threading.Lock()

def rush_ticket(user_id):
    global success_count, fail_count, error_count
    # 所有人等待发令枪齐发
    barrier.wait()

    url = f"{BASE_URL}{10000 + user_id}"
    req = urllib.request.Request(url, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            res = json.loads(response.read().decode('utf-8'))
            code = res.get("code")
            with lock:
                if code == 200:
                    success_count += 1
                elif code == 400:
                    fail_count += 1
                else:
                    error_count += 1
    except Exception as e:
        with lock:
            error_count += 1

print(f" 准备就绪：已集结 {TOTAL_USERS} 位歌迷，目标冲击真实下单接口，抢购 20 张内场 VIP票...")
threads = [threading.Thread(target=rush_ticket, args=(i,)) for i in range(TOTAL_USERS)]

start_time = time.time()
for t in threads:
    t.start()
for t in threads:
    t.join()
duration = time.time() - start_time

print("\n================== 真实下单并发压测战报 ==================")
print(f"总计冲击请求数: {TOTAL_USERS}")
print(f"抢票成功下单数 (200 成功):   {success_count} 笔")
print(f"抢票失败阻断数 (400 已售罄): {fail_count} 笔")
print(f"系统异常崩溃数 (500 异常):   {error_count} 笔")
print(f"总耗时: {duration:.3f} 秒")
print("==========================================================")

if success_count == 20 and fail_count == 80 and error_count == 0:
    print("恭喜！完美达成 0 超卖！数据库原子更新正常，订单落库！")
else:
    print(f"警告：数据异常！成功 {success_count}，失败 {fail_count}，异常{error_count}")