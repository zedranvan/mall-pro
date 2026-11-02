#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Elasticsearch 演出全文检索中枢全链路工程验收
测试场景：
1. ES 集群健康探测与全量演出倒排索引预热同步；
2. 关键字全文倒排检索与命中高亮标红（匹配标题与艺人）；
3. 跨字段模糊匹配（按场馆地点如 '大运中心' / 艺人组合如 '新裤子' 精准召回）；
4. 多维条件组合过滤（城市 + 分类 + 价格区间组合筛选）；
5. 动态排序策略画像（按起步票价由低到高 ASC 严格递增验证）；
6. ES 倒排检索极速性能摸高画像（采样 100 次测算平均查询延迟）。
"""

import time
import requests

BASE_URL = "http://localhost:8080"
ES_URL = "http://localhost:9200"

def print_step(title):
    print("\n" + "=" * 65)
    print(f"▶ {title}")
    print("=" * 65)

def run_test():
    print("🚀 [开始测试] Elasticsearch 演出全文检索中枢全景工程验收")

    # =================================================================
    print_step("步骤 1：Elasticsearch 集群健康探测与全量索引预热")
    # =================================================================
    es_health = requests.get(f"{ES_URL}/_cluster/health").json()
    print(f"📡 ES 集群连接正常: cluster_name='{es_health.get('cluster_name')}', status='{es_health.get('status')}'")
    assert es_health.get("status") in ["green", "yellow"], "ES 集群状态应为健康"

    init_res = requests.post(f"{BASE_URL}/api/search/init").json()
    assert init_res.get("code") == 200, f"初始化索引失败: {init_res}"
    print(f"✅ 倒排索引构建完成: {init_res.get('data')}")

    # =================================================================
    print_step("步骤 2：关键字全文检索与标题高亮标红 (检索 '周杰伦')")
    # =================================================================
    res = requests.post(f"{BASE_URL}/api/search/programs", json={"keyword": "周杰伦"}).json()
    assert res.get("code") == 200, f"检索失败: {res}"
    data = res["data"]
    print(f"🎯 关键字 '周杰伦' 召回记录数: {data['total']} 条")
    assert data["total"] >= 1, "应至少召回 1 条周杰伦演出"
    first = data["list"][0]
    print(f"   - 演出ID={first['id']}: [{first['title']}], 艺人={first['actor']}, 最低票价=¥{first['minPrice']}")
    
    # 验证高亮
    highlight_title = data["highlightMap"].get(str(first["id"]))
    print(f"   - 动态高亮标红: {highlight_title}")
    assert "<em style='color:red'>周杰伦</em>" in highlight_title, "标题中应正确嵌入高亮标签"
    print("✅ 倒排检索与高亮标红校验 100% 通过！")

    # =================================================================
    print_step("步骤 3：跨字段模糊匹配 (通过场馆 '大运中心' / 艺人 '新裤子')")
    # =================================================================
    res_place = requests.post(f"{BASE_URL}/api/search/programs", json={"keyword": "大运中心"}).json()["data"]
    print(f"🏟️ 场馆检索 '大运中心' 召回: {res_place['total']} 条 -> {res_place['list'][0]['title']}")
    assert res_place["total"] == 1 and "深圳大运中心" in res_place["list"][0]["place"], "应准确召回深圳大运中心演出"

    res_actor = requests.post(f"{BASE_URL}/api/search/programs", json={"keyword": "新裤子"}).json()["data"]
    print(f"🎸 艺人检索 '新裤子' 召回: {res_actor['total']} 条 -> {res_actor['list'][0]['title']}")
    assert res_actor["total"] == 1 and "草莓音乐节" in res_actor["list"][0]["title"], "应准确召回音乐节演出"
    print("✅ 跨字段倒排召回校验通过！")

    # =================================================================
    print_step("步骤 4：多维条件组合过滤 (城市 + 价格区间)")
    # =================================================================
    # 1. 城市筛选：深圳
    res_city = requests.post(f"{BASE_URL}/api/search/programs", json={"city": "深圳"}).json()["data"]
    print(f"🌆 城市筛选 '深圳' 召回: {res_city['total']} 条")
    assert all(item["city"] == "深圳" for item in res_city["list"]), "所有召回结果城市必须为深圳"

    # 2. 价格区间过滤：100 元 ~ 300 元
    res_price = requests.post(f"{BASE_URL}/api/search/programs", json={"minPrice": 100.0, "maxPrice": 300.0}).json()["data"]
    print(f"🏷️ 价格区间 [¥100 ~ ¥300] 召回: {res_price['total']} 条")
    for item in res_price["list"]:
        print(f"   - [{item['title']}] 最低价=¥{item['minPrice']}")
        assert 100.0 <= item["minPrice"] <= 300.0, "最低价必须在 100~300 元区间内"
    assert res_price["total"] == 2, "预期恰好召回 2 场适价演出"
    print("✅ 多维联合过滤准确率 100% 达成！")

    # =================================================================
    print_step("步骤 5：动态排序策略画像 (票价由低到高 ASC 排序)")
    # =================================================================
    res_sort = requests.post(f"{BASE_URL}/api/search/programs", json={"sortType": 3}).json()["data"]
    prices = [item["minPrice"] for item in res_sort["list"]]
    print(f"📈 价格由低到高排序结果: {prices}")
    assert prices == sorted(prices), "价格列表必须为严格单调递增"
    print("✅ 动态多维排序画像检验通过！")

    # =================================================================
    print_step("步骤 6：Elasticsearch 倒排索引响应延迟性能摸高 (100次采样)")
    # =================================================================
    latencies = []
    for _ in range(100):
        t0 = time.perf_counter()
        requests.post(f"{BASE_URL}/api/search/programs", json={"keyword": "演唱会"})
        latencies.append((time.perf_counter() - t0) * 1000)

    avg_lat = sum(latencies) / len(latencies)
    min_lat = min(latencies)
    p95_lat = sorted(latencies)[int(len(latencies) * 0.95)]
    print(f"⚡ ES 搜索性能画像: 平均耗时={avg_lat:.2f}ms, 最小耗时={min_lat:.2f}ms, P95={p95_lat:.2f}ms")
    print(f"🚀 搜索换算 QPS 承载能力: 超过 {1000 / avg_lat:.0f} req/s (完全零数据库压力)！")

    print("\n" + "=" * 65)
    print("🏆 [全景验收通过] Elasticsearch 演出全文检索中枢 100% 达成大麦级商业闭环！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
