# API

基地址：`http://127.0.0.1:8080/api/v1`。除会话初始化、登录与静态页面外，需已登录 Session Cookie。写入发送当前 `X-CSRF-TOKEN`，浏览器同源。CSRF 缺失 403，未登录读取 401。

## 会话

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/auth/session` | 返回 data 用户（未登录 null）与 csrf.headerName / token |
| POST | `/auth/login` | 表单格式 username / password；成功后重新读取 session 获取 CSRF |
| POST | `/auth/logout` | Session 失效并清除 Cookie；随后重新初始化 CSRF |

Cookie HttpOnly / SameSite=Strict，空闲 30 分钟过期。演示 HTTP 无 Secure。失败统一 invalid_credentials，不区分账号与密码问题。

## 项目与成员

| 方法 | 路径 | 用途与权限 |
| --- | --- | --- |
| GET | `/projects` | 自己参与的项目，含当前用户 role |
| POST | `/projects` | 登录用户创建，body `{"name":"项目名称"}`，1–80 字，成为 owner，201 |
| GET | `/projects/{project}/members` | 成员读取 id / username / displayName / role |
| POST | `/projects/{project}/members` | owner 添加现有虚拟账号，body `{"username":"demo_editor","role":"editor"}` |
| PUT | `/projects/{project}/members/{username}` | owner 修改 editor / viewer，不允许修改 owner |
| GET | `/projects/{project}/activities` | 当前项目最近 100 条审计，含 actor / 可空 taskId |

仅 editor / viewer。重复添加 409 member_exists，未知账号 400 unknown_account。降级 viewer 时同一事务清除分派、递增任务版本、写审计。

## 项目任务

以下路径以 `/projects/{project}` 开始。逐次校验成员关系，task 必须属于项目；外部项目及错误项目内任务 ID 返回 404。owner / editor 可写，viewer 只读。旧全局 /tasks、/summary 已移除。

| 方法 | 后缀 | 用途 |
| --- | --- | --- |
| GET | `/tasks` | 分页检索 |
| GET | `/tasks/{task}` | 读取任务 |
| POST | `/tasks` | 创建，201 与完整 Location |
| PUT | `/tasks/{task}` | 替换可编辑字段，必须 version |
| PATCH | `/tasks/{task}/status` | 合法状态迁移，必须 version |
| GET | `/tasks/{task}/activities` | 最近 100 条任务活动，id 倒序 |
| GET | `/tasks/{task}/comments` | 最近 100 条评论，id 倒序 |
| POST | `/tasks/{task}/comments` | body `{"body":"验收讨论"}`，1–1000 字；actor 取登录用户 |
| GET | `/summary` | 当前项目统计，不随筛选变化 |

列表：q 最多 120 字，搜索标题 / 描述 / 负责人，英文不区分大小写，% 和 _ 按字面；status / priority 可选；page 0–100000，size 1–100（默认 50）。更新时间与 id 倒序。

```json
{"title":"完成接口联调","description":"确认鉴权与错误状态","assignee":"demo_editor","priority":"high","dueDate":"2026-12-20"}
```

标题 1–120 字、描述最多 2000、负责人最多 60，可空，否则是当前项目 owner / editor 用户名；priority low / medium / high，日期可空。新建 backlog / version 0，不接受版本号或未知字段。编辑传 version，遗漏说明 / 负责人表示清空。

状态 body `{"status":"doing","version":0}`：

```text
backlog -> doing
doing   -> backlog / blocked / done
blocked -> doing / backlog
done    -> doing
```

相同状态且版本有效不增加版本或日志。成功编辑、状态变更、角色降级清分派，version 加 1；评论不改内容版本。

单资源 `{"data": {...}}`；列表 `{"data": [], "meta": {"page":0,"size":50,"total":0,"totalPages":0}}`。任务有 projectId，活动有 actor。时间戳 ISO UTC、逾期用 UTC 日期；统计 total / byStatus / overdue / completionRate（0–100、两位小数）。

## 错误

格式 `{"error":{"code":"version_conflict","message":"任务已被其他页面更新，请刷新后重试","details":[]}}`。

| 状态 | code |
| --- | --- |
| 400 | validation_error / invalid_json / invalid_assignee / unknown_account / owner_required |
| 401 | unauthorized / invalid_credentials |
| 403 | forbidden（角色 / CSRF） / origin_rejected / invalid_host |
| 404 | not_found，不泄露外部项目存在性 |
| 405 | method_not_allowed |
| 409 | version_conflict / invalid_transition / member_exists |
| 413 | request_too_large，已知长度超过 16 KiB |
| 415 | unsupported_media_type |
| 500 | internal_error，不包含 SQL 或堆栈 |

冲突后重新读取并让用户确认修改；不能自动替换 version 覆盖。
