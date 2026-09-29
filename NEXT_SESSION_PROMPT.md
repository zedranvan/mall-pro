# 抢票系统项目上下文交接与下一窗口启动提示词

> **文档创建时间**：2026-09-29  
> **开发者**：范嘉润（广东白云学院 软件工程 2026届）  
> **项目名称**：`mall-pro`（高并发大麦演唱会抢票系统）  
> **源码路径**：`/home/zedran/mall-pro`

---

## 一、项目技术栈与基础设施

- **语言与框架**：Java 17, Spring Boot 3.3.4
- **ORM 与持久层**：MyBatis-Plus 3.5.5, HikariCP 连接池
- **数据存储**：
  - PostgreSQL 15（Docker 容器 `mall-postgres`，端口 5432，库名 `mall_db`）
  - Redis 7（Docker 容器 `url_shortener_redis`，端口 6379）
- **配置文件**：`src/main/resources/application.properties`
  - 采用平铺 Properties 格式，彻底消除 YAML 缩进隐患。
  - `spring.output.ansi.enabled=ALWAYS`（彩色终端输出）。
  - `logging.pattern.console` 已完成对齐优化。

---

## 二、当前已完成的核心阶段与技术沉淀

### 1. 领域模型与防超卖引擎（已通过 100 并发压测）
- **数据表**：`d_program`（演出项目）、`d_ticket_category`（票档与库存）、`d_ticket_order`（订单表）。
- **防超卖核心机制**：
  - 数据库行级排他锁（X-Lock）原子扣减：`UPDATE d_ticket_category SET remain_stock = remain_stock - count WHERE id = ? AND remain_stock >= count;`
  - Redis Cache-Aside 缓存加速与失效策略。
  - 压测结论：100 并发抢购 20 张票，精准售出 20 张，80 笔拦截阻断，0 超卖。

### 2. 订单生成与事务保障
- **雪花算法（Snowflake ID）**：`@TableId(type = IdType.ASSIGN_ID)` 生成全局唯一 64 位无序自增 ID。
- **本地事务控制**：`@Transactional(rollbackFor = Exception.class)` 绑定“扣减库存”与“插入订单”，确保强一致性。

### 3. 订单有限状态机（FSM）与自愈闭环
- **状态枚举（`OrderStatus.java`）**：
  - `CREATED(0, "待支付")` $\rightarrow$ `PAID(1, "已支付")` 或 `CANCELLED(4, "已取消")`
  - `PAID(1, "已支付")` $\rightarrow$ `SHIPPED(2, "已发货")` 或 `REFUNDED(5, "已退款")`
  - 终态（`COMPLETED(3)`, `CANCELLED(4)`, `REFUNDED(5)`）不可逆。
- **并发状态防篡改**：基于 `version` 乐观锁（CAS）防止支付与取消竞态。
- **自动关单与库存自愈巡逻任务（`OrderTimeoutTask.java`）**：
  - `@Scheduled(fixedDelay = 10000)` 定时扫描 `status = 0 AND create_time < NOW() - 15min`。
  - **实测验证**：已成功将超时的 20 笔待支付订单变更为 `status = 4`，并将票档 102 的剩余库存从 `0` 自动回滚补偿至 `20`！

---

## 三、下一阶段待办事项（Next Steps）

1. **补齐手动取消订单接口**：
   - 在 `OrderController.java` 中暴露 `@PostMapping("/{id}/cancel")`，调用 `orderService.cancelOrder(id, "用户主动取消")`。
2. **前后端联调与前端开发（Vue 3 + Vite）**：
   - 搭建前端项目（Element Plus + Pinia + Axios + Vue Router）。
   - 页面矩阵：演唱会详情页（选座/选票档）、实时抢票页、待支付倒计时收银台、订单列表与状态展示。

---

## 四、下一窗口直接粘贴的启动提示词（Prompt）

```text
你好！我是范嘉润，广东白云学院软件工程专业学生。
我们正在开发一个高并发大麦演唱会抢票系统「mall-pro」（基于 Java 17 + Spring Boot 3.3.4 + PostgreSQL + Redis + MyBatis-Plus）。

【教学方式要求】
1. 采用「苏格拉底式引导教学与第一性原理」，先抛出痛点/事故场景，再分析业界主流方案与底层原理。
2. 杜绝毫无意义的夸奖与吹捧（不要说“太对了”、“很棒”等套话），保持严谨的工程与代码掌控力。
3. 拒绝直接丢大段未解释的代码，解释清楚每个变量、类名、注解为什么这么写。

【当前项目进展】
项目位于 /home/zedran/mall-pro。
已完成：
1. 数据库原子扣减防超卖与 Redis Cache-Aside 缓存（100 并发压测 0 超卖）。
2. 订单创建与雪花算法 ID、@Transactional 事务保证。
3. 订单有限状态机（OrderStatus）与乐观锁防并发状态错乱。
4. 定时关单与库存自愈（OrderTimeoutTask 已实测成功回滚 20 笔订单库存）。
5. 配置文件已规范为 application.properties。

【本次对话的目标】
1. 检查并补齐 OrderController 中的手动关单接口 POST /api/orders/{id}/cancel 并做 curl 闭环验证。
2. 开启前端工程设计与开发（Vue 3 + Vite + Element Plus + Pinia），带领我完成选票与抢票 UI 交互。

请先确认你已掌握以上背景，并告诉我第一步我们要怎么做。
```
