# 本地 Docker 部署 Nginx 与双节点负载均衡实战手册

---

## 一、 为什么在本地部署 Nginx？

在工业级架构中，Nginx 是高并发系统的“门神”。在本地（Fedora Linux + Docker）搭建 Nginx 具有极高的工程价值：
1. **真实模拟大厂集群（面试核心亮点）**：本地启动两个 Spring Boot 实例（`8080` 与 `8081`），通过 Nginx 实现真正的**四层/七层负载均衡与流量分发**。
2. **动静分离**：静态页面（HTML/CSS/JS/图片）由 Nginx 纳秒级返回，API 请求动态反向代理给后端。
3. **零成本与高性能**：本地多核机器的算力远超低配云服务器，可承接数万 QPS 的本地发压。

---

## 二、 核心部署步骤

### 1. 准备本地配置目录与文件

在本地创建挂载目录：
```bash
mkdir -p ~/nginx/conf.d
```

创建 Nginx 核心配置文件 `~/nginx/conf.d/mall.conf`：

```nginx
# 定义后端 Spring Boot 集群（负载均衡池）
upstream mall_backend {
    # host.docker.internal 代表 Docker 容器访问宿主机网络
    server host.docker.internal:8080 weight=1 max_fails=2 fail_timeout=10s;
    # 模拟集群节点2（若启动了 8081 实例，直接取消下方注释即可生效）
    # server host.docker.internal:8081 weight=1 max_fails=2 fail_timeout=10s;
}

server {
    listen 80;
    server_name localhost;

    # 客户端最大请求体限制（如上传附件、报文大小）
    client_max_body_size 10M;

    # 动态接口反向代理
    location /api/ {
        proxy_pass http://mall_backend;
        
        # 传递客户端真实 IP 与网络头信息
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 连接超时设置
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    # 健康检查与根路径测试
    location / {
        return 200 "Nginx Gateway is running! Proxying to Spring Boot Mall Cluster.\n";
        add_header Content-Type text/plain;
    }
}
```

---

### 2. 启动 Nginx Docker 容器

在终端执行以下命令（单条命令一键拉起）：

```bash
docker run -d \
  --name mall-nginx \
  --restart always \
  -p 80:80 \
  --add-host=host.docker.internal:host-gateway \
  -v ~/nginx/conf.d:/etc/nginx/conf.d:ro \
  nginx:alpine
```

> **参数关键点解析**：
> - `--add-host=host.docker.internal:host-gateway`：**Linux 系统必备参数**，允许 Docker 容器内部通过 `host.docker.internal` 解析到宿主机真实 IP，无缝访问宿主机启动的 `8080` 端口。
> - `-v ~/nginx/conf.d:/etc/nginx/conf.d:ro`：将本地配置目录挂载为只读卷，后续修改配置文件无需重建容器，执行 reload 即可热生效。

---

### 3. 配置热重载与常用运维指令

配置修改后，无需重启容器，执行**零停机热重载**：

```bash
# 1. 检查 Nginx 配置文件语法是否正确
docker exec mall-nginx nginx -t

# 2. 零停机平滑重新加载配置
docker exec mall-nginx nginx -s reload

# 3. 查看实时访问日志与排错日志
docker logs -f mall-nginx
```

---

## 三、 本地多实例双节点（8080 + 8081）集群验证

1. **IDEA 启动第一个实例**：默认端口 `8080`。
2. **启动第二个实例**：在 IDEA Run Configuration 中添加 VM Options：`-Dserver.port=8081` 并启动。
3. **开启负载均衡**：在 `~/nginx/conf.d/mall.conf` 中解开 `server host.docker.internal:8081;` 的注释并执行 `nginx -s reload`。
4. **验证流量分发**：
   ```bash
   curl http://localhost/api/orders/...
   ```
   多次请求，观察 `8080` 与 `8081` 的后台控制台，请求将被 Nginx 精确按 1:1 轮流分发！
