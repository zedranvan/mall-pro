import urllib.request
import urllib.error
import json
import time
from concurrent.futures import ThreadPoolExecutor

BASE_URL = "http://localhost:8080/api/seckill/order"
PREHEAT_URL = "http://localhost:8080/api/seckill/preheat?ticketCategoryId=102"

TOTAL_USERS = 2000       # 真实的 2000 位互不相同的独立歌迷
CONCURRENCY = 50         # 50 个线程持续倾泻流量
CATEGORY_ID = 102

stats = {
    "200_SUCCESS": 0,           # 成功抢到票
    "400_LIMIT_ONE": 0,         # 一人一单拦截
    "400_SOLD_OUT": 0,          # 手慢了售罄拦截
    "500_SERVER_ERROR": 0,      # 服务器崩溃
    "TIMEOUT_OR_NET_ERR": 0     # 网络异常
}

# 1. 自动预热
print(f">>> 正在预热 Redis 票档库存...")
try:
    req = urllib.request.Request(PREHEAT_URL, method="POST")
    with urllib.request.urlopen(req) as resp:
        print(">>> 预热完成，2000 位独立歌迷即将涌入！\n")
except Exception as e:
    print(f"预热失败，请确保服务已启动: {e}")
    exit(1)

# 2. 生成 2000 个绝对唯一的独立用户 ID (10001 到 12000)
user_pool = [10000 + i for i in range(TOTAL_USERS)]

def send_request(uid):
    url = f"{BASE_URL}?ticketCategoryId={CATEGORY_ID}&userId={uid}"
    req = urllib.request.Request(url, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = json.loads(resp.read().decode())
            biz_code = body.get("code")
            msg = body.get("message", "")
            
            if biz_code == 200:
                stats["200_SUCCESS"] += 1
            elif biz_code == 400:
                if "一人限购" in msg or "已抢购过" in msg:
                    stats["400_LIMIT_ONE"] += 1
                elif "售罄" in msg:
                    stats["400_SOLD_OUT"] += 1
            else:
                stats["500_SERVER_ERROR"] += 1
    except urllib.error.HTTPError as e:
        stats["500_SERVER_ERROR"] += 1
    except Exception:
        stats["TIMEOUT_OR_NET_ERR"] += 1

# 3. 50 线程大水漫灌
start_time = time.time()
print(f"🌊 正在开启大水漫灌: {CONCURRENCY} 线程，向后端倾泻 {TOTAL_USERS} 位独立用户的抢票请求...")

with ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
    executor.map(send_request, user_pool)

duration = time.time() - start_time
qps = TOTAL_USERS / duration

print("\n==================== 真实千人抢票战报 ====================")
print(f"参与抢票真实人数:         {TOTAL_USERS} 位独立用户")
print(f"总耗时:                   {duration:.3f} 秒")
print(f"实际吞吐率 (QPS):         {qps:.1f} 请求/秒")
print("----------------------------------------------------------")
print(f"【抢票成功 (200)】:       {stats['200_SUCCESS']} 笔")
print(f"【售罄阻断 (400)】:       {stats['400_SOLD_OUT']} 笔")
print(f"【限购阻断 (400)】:       {stats['400_LIMIT_ONE']} 笔")
print(f"【服务器崩溃 (500)】:     {stats['500_SERVER_ERROR']} 笔 (0 代表健壮)")
print(f"【网络超时异常】:         {stats['TIMEOUT_OR_NET_ERR']} 笔")
print("==========================================================")