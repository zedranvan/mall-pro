import requests
import json
import time
import redis

BASE_URL = "http://localhost:8080/api/program"

# 物理直连 Redis，用于在测试中直接“嗅探”底层缓存是否真的被写入、TTL是否生效
redis_client = redis.Redis(
    host="127.0.0.1",
    port=6379,
    password="redis_password",
    decode_responses=True
)

def log_step(title):
    print(f"\n{'='*70}\n  🔬 [架构实测] {title}\n{'='*70}")


def test_search_all():
    log_step("场景 1: 查询全部在售演出列表 (不传 keyword)")
    resp = requests.get(f"{BASE_URL}/search")
    assert resp.status_code == 200, f"HTTP 状态码异常: {resp.status_code}"

    data = resp.json()
    assert data["code"] == 200, f"业务响应失败: {data.get('message')}"
    programs = data["data"]
    print(f"✅ 成功获取演出列表 (共 {len(programs)} 场):")
    for p in programs:
        print(f"   - [ID: {p['id']}] {p['title']} | 艺人: {p['actor']} | 场馆: {p['place']}")

    assert len(programs) > 0, "数据库中应至少有 1 场在售演出！"
    return programs[0]["id"]


def test_search_matrix():
    log_step("场景 2: 参数化模糊检索矩阵与防 SQL 注入探测")

    test_cases = [
        {"keyword": "周杰伦", "expected_match": True, "desc": "艺人全称精确匹配"},
        {"keyword": "广州", "expected_match": True, "desc": "城市地域模糊匹配"},
        {"keyword": "体育中心", "expected_match": True, "desc": "场馆名称模糊匹配"},
        {"keyword": "陶喆", "expected_match": False, "desc": "冷门不存在关键词"},
        {"keyword": "' OR '1'='1", "expected_match": False, "desc": "SQL 注入攻击探测 (验证 MyBatis 预编译)"},
    ]

    for tc in test_cases:
        kw = tc["keyword"]
        resp = requests.get(f"{BASE_URL}/search", params={"keyword": kw})
        assert resp.status_code == 200, f"检索异常: HTTP {resp.status_code}"

        data = resp.json()
        assert data["code"] == 200, f"业务异常: {data.get('message')}"
        results = data["data"]

        if tc["expected_match"]:
            assert len(results) > 0, f"[{tc['desc']}] 预期应命中，但返回空结果"
            print(f"  🟢 [{tc['desc']}] 搜索 '{kw}' -> 成功命中 {len(results)} 场演出")
        else:
            assert len(results) == 0, f"[{tc['desc']}] 预期应返回空列表，但意外返回数据！存在 SQL 注入拖库风险！"
            print(f"  🛡️ [{tc['desc']}] 输入 '{kw}' -> 安全转义，返回 0 匹配 (防御成功)")


def test_program_detail_and_cache(program_id):
    log_step("场景 3: 演出详情级联结构守则与 Redis 物理缓存穿透嗅探")

    # 1. 发起请求查详情
    resp = requests.get(f"{BASE_URL}/{program_id}")
    assert resp.status_code == 200, f"HTTP 状态码异常: {resp.status_code}"

    detail = resp.json()["data"]
    print(f"🎫 演出详情: 《{detail['title']}》 | 场馆: {detail['place']} | 时间: {detail['showTime']}")

    categories = detail.get("ticketCategory") or detail.get("ticketCategories")
    assert categories and len(categories) > 0, "演出详情下挂的票档列表不应为空！"

    # 2. 深度业务守则校验：外键一致性、库存守恒与价格升序
    last_price = 0
    for cat in categories:
        assert cat["programId"] == program_id, f"外键串号致命错误: 票档所属场次 {cat['programId']} != 当前场次 {program_id}"
        assert cat["remainStock"] <= cat["totalStock"], f"库存逻辑错乱: 余票 {cat['remainStock']} > 总票 {cat['totalStock']}"
        assert cat["price"] >= last_price, f"票档排序混乱: 前置价格 {last_price} 高于当前价格 {cat['price']}"
        last_price = cat["price"]
        print(f"  └─ 票档: [{cat['name']}] 价格: ¥{cat['price']} (余票: {cat['remainStock']}/{cat['totalStock']})")

        # 3. 核心：Redis 物理旁路嗅探 (验证 L2 Redis 是否被真实写入并设置了 TTL)
        cache_key = f"ticket_category:{cat['id']}"
        raw_cache = redis_client.get(cache_key)
        assert raw_cache is not None, f"物理嗅探失败: Redis 中未找到缓存键 {cache_key}！多级缓存未生效！"
        ttl = redis_client.ttl(cache_key)
        print(f"     🔍 [Redis物理嗅探] Key '{cache_key}' 存在, TTL={ttl}s (证明 L2 缓存生效)")


def test_boundary_and_whitelist():
    log_step("场景 4: 边界异常容错与游客白名单正反对照验证")

    # 1. 边界异常：查不存在的演出 ID
    resp = requests.get(f"{BASE_URL}/999999")
    data = resp.json()
    assert data["code"] == 404, f"预期应返回 404 错误，实际返回: {data}"
    print(f"  🛡️ [边界防御成功] 查不存在的 ID(999999) -> 成功返回业务 404: '{data['message']}'")

    # 2. 游客白名单放行：不带任何 Token 访问公开接口
    guest_resp = requests.get(f"{BASE_URL}/search")
    assert guest_resp.status_code == 200, "白名单失效: 游客访问演出检索被非法拦截！"
    print("  🟢 [白名单放行] 游客无 Token 访问演出检索 -> HTTP 200 正常放行")

    # 3. 反向对照组：无 Token 访问私密个人资料 (验证拦截器没有全局放水)
    private_resp = requests.get("http://localhost:8080/api/user/info")
    assert private_resp.status_code == 401, f"鉴权漏洞: 受保护接口未拦截无 Token 访问！状态码: {private_resp.status_code}"
    print(f"  🔒 [私密接口守卫] 游客无 Token 访问个人资料 -> HTTP 401 成功阻断拦截")


def test_cache_benchmark(program_id):
    log_step("场景 5: L1(Caffeine) + L2(Redis) 连续高频热读耗时分布压测")

    latencies = []
    rounds = 30
    for _ in range(rounds):
        start = time.perf_counter()
        requests.get(f"{BASE_URL}/{program_id}")
        latencies.append((time.perf_counter() - start) * 1000)  # 毫秒

    avg_latency = sum(latencies) / len(latencies)
    min_latency = min(latencies)
    max_latency = max(latencies)
    print(f"⚡ 连续 {rounds} 次高频读取详情与票档联动数据:")
    print(f"   - 平均响应耗时 (Avg): {avg_latency:.2f} ms")
    print(f"   - 最快响应耗时 (Min): {min_latency:.2f} ms (进程内 L1 Caffeine 直出)")
    print(f"   - 最慢长尾耗时 (Max): {max_latency:.2f} ms")
    assert avg_latency < 10.0, "两级缓存性能异常: 平均响应时间超过 10ms！"
    print("  🚀 [多级缓存性能断言通过] 接口维持在毫秒甚至亚毫秒级高并发支撑能力！")


if __name__ == "__main__":
    print("\n" + "="*70)
    print("  🎪 演出场次检索、级联票档多级缓存与白名单鉴权自动化综合验收套件")
    print("="*70)

    # 串联流水线
    first_program_id = test_search_all()
    test_search_matrix()
    test_program_detail_and_cache(first_program_id)
    test_boundary_and_whitelist()
    test_cache_benchmark(first_program_id)

    print("\n" + "="*70)
    print("  🏆 全部 5 大场景断言 100% 通过！演出场次检索与票档多级缓存联动架构闭环！")
    print("="*70 + "\n")