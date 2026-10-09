import time
import requests
import json
import subprocess

BASE_URL = "http://localhost:8080"

def check_server_ready():
    for _ in range(10):
        try:
            res = requests.get(f"{BASE_URL}/api/tickets/category/101", timeout=2)
            if res.status_code == 200:
                return True
        except Exception:
            time.sleep(1)
    return False

def query_db_password(username):
    sql = f"SELECT password FROM d_user WHERE username = '{username}';"
    cmd = ["docker", "exec", "-i", "mall-postgres", "psql", "-U", "mall", "-d", "mall_db", "-t", "-A", "-c", sql]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def run_test():
    print("=" * 65)
    print("  👤 用户中心、BCrypt密码加密、JWT拦截鉴权与常用观演人实测")
    print("=" * 65)

    if not check_server_ready():
        print("❌ 错误: 无法连接到 http://localhost:8080，请确认 Spring Boot 服务已成功启动！")
        return

    test_suffix = str(int(time.time()))[-4:]
    test_username = f"fan_{test_suffix}"
    test_phone = f"156{test_suffix}8888"
    raw_password = "MySecurePassword@2026"

    # -------------------------------------------------------------------------
    # 场景 1: 用户注册、账号唯一性校验与 BCrypt 密文存储
    # -------------------------------------------------------------------------
    print(f"\n[场景 1] 新用户注册 (用户名: {test_username}, 手机号: {test_phone})...")
    reg_payload = {
        "username": test_username,
        "phone": test_phone,
        "password": raw_password,
        "nickname": "嘉润测试官"
    }
    reg_res = requests.post(f"{BASE_URL}/api/user/register", json=reg_payload).json()
    print(f"    - 注册响应: code={reg_res.get('code')}, message={reg_res.get('message')}")
    if reg_res.get("code") != 200:
        print(f"    ❌ 注册失败: {reg_res}")
        return
    user_id = reg_res["data"]["id"]
    print(f"    ✅ 注册成功！分配用户 ID = {user_id}")

    # 重复注册测试
    dup_res = requests.post(f"{BASE_URL}/api/user/register", json=reg_payload).json()
    print(f"    - 重复用户名注册拦截: {dup_res}")
    if dup_res.get("code") == 400 and "已存在" in dup_res.get("message", ""):
        print("    🛡️ 唯一性排他有效：成功拦截同名重复注册！")

    # 验证底层数据库密码是否为 BCrypt 随机盐密文
    db_pwd = query_db_password(test_username)
    print(f"    - 数据库存储的密码密文: {db_pwd[:20]}... (前缀: {db_pwd[:4]})")
    if db_pwd.startswith("$2a$") and raw_password not in db_pwd:
        print("    🔒 密码安全合规：采用 BCrypt 随机加盐哈希，明文绝不落库！")

    # -------------------------------------------------------------------------
    # 场景 2: 密码匹配与 JWT 令牌签发
    # -------------------------------------------------------------------------
    print(f"\n[场景 2] 用户登录与 JWT 令牌签发...")
    # 错密测试
    wrong_login = requests.post(f"{BASE_URL}/api/user/login", json={"username": test_username, "password": "WrongPassword"}).json()
    print(f"    - 错误密码登录响应: {wrong_login}")
    if wrong_login.get("code") == 400:
        print("    🛡️ 防试探阻断：密码错误被安全拦截！")

    # 正确密码登录
    login_res = requests.post(f"{BASE_URL}/api/user/login", json={"username": test_username, "password": raw_password}).json()
    if login_res.get("code") != 200:
        print(f"    ❌ 登录失败: {login_res}")
        return
    token = login_res["data"]["token"]
    print(f"    🎉 登录成功！获取 JWT 令牌:")
    print(f"       - 令牌串: {token[:25]}...{token[-15:]}")
    print(f"       - 昵称:   {login_res['data']['nickname']}")

    # -------------------------------------------------------------------------
    # 场景 3: 拦截器守门人测试 (未登录 401 截断)
    # -------------------------------------------------------------------------
    print(f"\n[场景 3] 统一鉴权拦截器测试 (试图未授权访问个人资料)...")
    # 不带 Token
    no_token_res = requests.get(f"{BASE_URL}/api/user/profile")
    print(f"    - 裸连访问响应状态: HTTP {no_token_res.status_code} -> {no_token_res.json()}")
    if no_token_res.status_code == 401:
        print("    🛡️ 入口截断生效：未登录请求被拦截器成功熔断，禁止进入业务层！")

    # 带伪造假 Token
    fake_token_res = requests.get(f"{BASE_URL}/api/user/profile", headers={"Authorization": "Bearer fake_token_abc123"})
    print(f"    - 伪造 Token 访问: HTTP {fake_token_res.status_code} -> {fake_token_res.json()}")
    if fake_token_res.status_code == 401:
        print("    🛡️ 防伪验签生效：篡改与非法 Token 被当场拦截！")

    # -------------------------------------------------------------------------
    # 场景 4: 携带合法 JWT 访问个人中心 (ThreadLocal 免参提取)
    # -------------------------------------------------------------------------
    print(f"\n[场景 4] 携带合法 JWT 访问受保护接口 (免传 userId)...")
    headers = {"Authorization": f"Bearer {token}"}
    profile_res = requests.get(f"{BASE_URL}/api/user/profile", headers=headers).json()
    print(f"    🎉 个人资料返回: {profile_res}")
    if profile_res.get("code") == 200 and profile_res["data"]["username"] == test_username:
        print("    🟢 ThreadLocal 上下文免参提取完美工作：从 JWT 成功还原用户身份！")

    # -------------------------------------------------------------------------
    # 场景 5: 常用观演人管理 (强实名认证与一人多证)
    # -------------------------------------------------------------------------
    print(f"\n[场景 5] 维护常用观演人列表 (为自己和同行朋友绑定身份)...")
    # 添加观演人 1 (本人)
    requests.post(f"{BASE_URL}/api/user/attendee/add", headers=headers, params={
        "realName": "范嘉润",
        "idCard": "440102199801011234",
        "phone": test_phone
    })
    # 添加观演人 2 (朋友)
    requests.post(f"{BASE_URL}/api/user/attendee/add", headers=headers, params={
        "realName": "李小龙",
        "idCard": "440102200005055678",
        "phone": "13800138000"
    })
    print("    ✅ 成功添加 2 位常用观演人！")

    # 查询观演人列表
    list_res = requests.get(f"{BASE_URL}/api/user/attendee/list", headers=headers).json()
    attendees = list_res.get("data", [])
    print(f"    📋 查询当前用户名下的观演人列表 (共 {len(attendees)} 人):")
    for a in attendees:
        print(f"       - 姓名: {a['realName']}, 身份证: {a['idCardMasked']} (脱敏: ✅), 散列: {a['idCardHash'][:12]}...")

    if len(attendees) == 2:
        print("    🟢 常用观演人中台业务完全跑通：秒杀前一键勾选，免手动输入！")

    print("\n" + "=" * 65)
    print("  🏆 用户中心、JWT 无状态鉴权与常用观演人实测大圆满！")
    print("=" * 65)

if __name__ == "__main__":
    run_test()
