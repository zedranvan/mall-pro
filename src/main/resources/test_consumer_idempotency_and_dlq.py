import subprocess, json, time

print("=" * 65)
print("  🛡️ 消费端幂等防重与 Kafka 死信队列（DLQ）实测")
print("=" * 65)

KAFKA_PRODUCER_CMD = [
    "docker", "exec", "-i", "url_shortener_kafka",
    "/opt/kafka/bin/kafka-console-producer.sh",
    "--bootstrap-server", "localhost:9092",
    "--topic", "order-paid-topic"
]

def send_kafka_message(payload_str):
    p = subprocess.Popen(KAFKA_PRODUCER_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p.communicate(input=payload_str + "\n")

# -------------------------------------------------------------
# 场景一：模拟网络闪断导致的「消息重复投递」
# -------------------------------------------------------------
test_order_id = int(time.time() * 1000)
test_payload = json.dumps({
    "orderId": test_order_id,
    "paySn": f"PAY_MOCK_{test_order_id}",
    "tradeNo": f"TRADE_MOCK_{test_order_id}",
    "userId": 9999,
    "amount": 580.00,
    "payTime": "2026-10-04T17:20:00"
})

print(f"\n[测试 1] 模拟极端网络重平衡：连续向 Kafka 推送 2 条完全相同的消息 (orderId={test_order_id})")
print("    --> 投递第 1 条消息...")
send_kafka_message(test_payload)
time.sleep(1.5)

print("    --> 投递第 2 条完全相同的重复消息...")
send_kafka_message(test_payload)
time.sleep(1.5)

# 查询 Redis 验证是否只有 1 个核销码
redis_cmd = ["docker", "exec", "-i", "url_shortener_redis", "redis-cli", "-a", "redis_password", "get", f"ticket:issued:{test_order_id}"]
res = subprocess.run(redis_cmd, capture_output=True, text=True)
verify_code = res.stdout.strip().split("\n")[-1]

print(f"\n    ✅ 幂等防重验证结果:")
print(f"       Redis 锁定核销码: {verify_code}")
print(f"       请检查 Spring Boot 控制台: 是否第 1 次成功出票，第 2 次触发 [出票幂等拦截] 拒绝重复出票！")

# -------------------------------------------------------------
# 场景二：模拟数据损坏导致的「业务毒丸消息」
# -------------------------------------------------------------
print("\n" + "-" * 65)
poison_message = "💥_THIS_IS_A_POISON_PILL_INVALID_JSON_DATA_💥"
print(f"[测试 2] 故意向 order-paid-topic 注入一条无法解析的【业务毒丸消息】...")
print(f"    内容: {poison_message}")
send_kafka_message(poison_message)

print("    --> 正在观察 Spring Kafka @RetryableTopic 自动重试机制 (1s -> 2s 指数退避)...")
time.sleep(4)

print("\n    ✅ 死信队列验证结果:")
print("       请检查 Spring Boot 控制台，亲眼观察两组关键日志:")
print("       1. 连续 3 次报错重试: [Kafka出票消费异常] 消息解析或业务处理失败")
print("       2. 达到 3 次上限后，自动将毒丸隔离至死信队列:")
print("          💥 [死信队列告警] 消息多次消费失败，已自动移入死信队列: topic=order-paid-topic-dlt")

print("\n" + "=" * 65)
print("  🎉 测试脚本执行完毕！请对照控制台查看日志输出")
print("=" * 65)
