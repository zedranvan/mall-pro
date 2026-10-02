#!/bin/bash
set -e

echo "=== [1/4] 正在自动发起下单 (购买 580元 门票)... ==="
ORDER_RESP=$(curl -s -X POST "http://localhost:8080/api/orders/create?userId=8888&ticketCategoryId=101&count=1")
ORDER_ID=$(echo "$ORDER_RESP" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])")
echo "-> 创单成功! 订单号: $ORDER_ID"

echo "=== [2/4] 正在根据订单号发起预支付 (生成流水单)... ==="
PREPAY_RESP=$(curl -s -X POST "http://localhost:8080/api/pay/prepay?orderId=$ORDER_ID")
PAY_SN=$(echo "$PREPAY_RESP" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['paySn'])")
echo "-> 预支付成功! 支付流水号: $PAY_SN"

echo "=== [3/4] 正在模拟收银台点击支付 (触发HMAC验签与状态跃迁)... ==="
PAY_RESULT=$(curl -s -X POST "http://localhost:8080/api/pay/mock/cashier?paySn=$PAY_SN")
echo "-> 支付结果: $PAY_RESULT"

echo "=== [4/4] 验证幂等性防重重试 (再次调用同一笔流水支付)... ==="
RETRY_RESULT=$(curl -s -X POST "http://localhost:8080/api/pay/mock/cashier?paySn=$PAY_SN")
echo "-> 幂等防重结果: $RETRY_RESULT"