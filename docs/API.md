# API

基地址：`http://127.0.0.1:8080/api/v1`。本地演示不要求登录。写入请求使用 `Content-Type: application/json`，浏览器必须同源。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/tasks` | 分页检索 |
| GET | `/tasks/{id}` | 读取任务 |
| POST | `/tasks` | 创建任务，201 与 Location |
| PUT | `/tasks/{id}` | 替换可编辑字段，必须带 version |
| PATCH | `/tasks/{id}/status` | 按规则改变状态，必须带 version |
| GET | `/tasks/{id}/activities` | 最近 100 条记录，按 id 倒序 |
| GET | `/summary` | 全部任务统计，不随列表筛选变化 |

列表参数：`q` 最多 120 字，搜索标题、描述、负责人，不区分英文大小写；`%`、`_` 按字面搜索。可选 `status`、`priority`；`page` 从 0 开始，范围 0–100000；`size` 默认 50，范围 1–100。排序为更新时间和 id 倒序。

创建请求：

```json
{
  "title": "实现任务编辑",
  "description": "补充表单校验和成功提示",
  "assignee": "演示用户",
  "priority": "medium",
  "dueDate": "2026-12-20"
}
```

标题必填，最多 120 字；描述最多 2000 字；负责人最多 60 字。优先级必填，只能 `low`、`medium`、`high`。截止日期可为空。新建任务固定为 `backlog`、version 为 0。创建不接受版本号，未定义字段会被拒绝。

编辑使用相同字段，额外传入当前 `version`。所有编辑字段完整提交；描述或负责人不传时会清空。状态接口只接受 `{"status":"doing","version":0}`。

状态规则：

```text
backlog -> doing
doing   -> backlog / blocked / done
blocked -> doing / backlog
done    -> doing
```

传入相同状态且版本有效时返回当前任务，不增加版本和活动记录。每次成功编辑或改变状态，version 加 1。

单资源成功响应：`{"data": {...}}`。列表响应：

```json
{
  "data": [],
  "meta": {"page": 0, "size": 50, "total": 0, "totalPages": 0}
}
```

任务字段为 `id/title/description/assignee/priority/status/version/dueDate/createdAt/updatedAt`。时间戳为 ISO UTC。统计返回 `total/byStatus/overdue/completionRate`，完成比例以 0–100 百分数表示，最多两位小数。

错误格式统一为：

```json
{"error":{"code":"version_conflict","message":"任务已被其他页面更新，请刷新后重试","details":[]}}
```

| 状态 | 场景与 code |
| --- | --- |
| 400 | `validation_error` / `invalid_json` |
| 403 | `origin_rejected` / `invalid_host` |
| 404 | `not_found` |
| 405 | `method_not_allowed` |
| 409 | `version_conflict` / `invalid_transition` |
| 413 | `request_too_large`，已知长度超过 16 KiB |
| 415 | `unsupported_media_type` |
| 500 | `internal_error`，响应不包含堆栈或 SQL |

冲突后重新读取任务，并让用户重新确认修改内容；不能自动用新版本号覆盖。
