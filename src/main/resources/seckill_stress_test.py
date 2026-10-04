import urllib.request, json, threading, time

BASE_URL = 'http://localhost:8080/api/seckill/order?ticketCategoryId=102&userId='
TOTAL_USERS = 100
barrier = threading.Barrier(TOTAL_USERS)
success, fail, error = 0, 0, 0
lock = threading.Lock()

# 1. 先自动预热库存为 20
urllib.request.urlopen(urllib.request.Request('http://localhost:8080/api/seckill/preheat?ticketCategoryId=102',method='POST'))

def rush(uid):
    global success, fail, error
    barrier.wait() # 发令枪齐发
    try:
        req = urllib.request.Request(f'{BASE_URL}{uid}', method='POST')
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            with lock:
                if data.get('code') == 200: success += 1
                elif data.get('code') == 400: fail += 1
                else: error += 1
    except urllib.error.HTTPError as e:
        with lock:
            if e.code == 400: fail += 1
            else: error += 1
    except Exception:
        with lock: error += 1

threads = [threading.Thread(target=rush, args=(1000 + i,)) for i in range(TOTAL_USERS)]
start = time.time()
for t in threads: t.start()
for t in threads: t.join()
print(f'\n=== 压测战报 (耗时 {time.time()-start:.2f}s) ===')
print(f'成功抢到票 (200 OK): {success} 笔')
print(f'被 Lua 拦截阻断 (400 售罄): {fail} 笔')
print(f'系统崩溃异常 (500): {error} 笔')
if success == 20 and fail == 80:
    print('🎉 完美达成 0 超卖！20 张票被 20 个不同用户抢完，剩余 80 人全部在 Redis 内存阻断！')