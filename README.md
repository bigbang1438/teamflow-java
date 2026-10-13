# TeamFlow · Java 项目协作平台

**AI 辅助开发的个人工程实践作品**，用于演示和学习，不代表实习、商业上线或生产团队使用经历。

本机可运行的 Java + Vue 协作应用。负责人创建项目并添加成员；编辑者创建、分派和推进任务；只读成员查看任务与讨论。任务修改、评论和权限变化记录登录账号。两个页面编辑同一任务时，旧版本提交收到 409，避免静默覆盖。

仓库：[bigbang1438/teamflow-java](https://github.com/bigbang1438/teamflow-java)

## 实现范围

- **账号与会话**：Spring Security 表单登录，数据库 BCrypt 密码哈希，HttpOnly / SameSite=Strict Session Cookie；登录轮换会话，登出失效，所有写入需 CSRF Token。
- **项目隔离**：账号只列出参与的项目；任务、成员、统计、评论与活动接口逐次校验成员关系，猜测外部项目或任务编号返回 404。
- **成员角色**：owner 管理成员并维护任务；editor 维护任务与评论；viewer 只读。客户端角色不作为服务端授权依据。
- **分派闭环**：负责人必须是当前项目的 owner / editor。编辑者改为 viewer 时，清除其任务分派、递增任务版本、生成任务及项目审计记录，同一事务完成。
- **任务工作流**：待开始、进行中、有阻碍、已完成；服务端限定合法迁移。可编辑标题、说明、优先级、截止日期与真实项目成员分派。
- **并发与一致性**：条件更新 `UPDATE ... WHERE project_id = ? AND id = ? AND version = ?`；修改与活动记录共用事务，过期写入不会留下成功记录。
- **讨论与审计**：评论绑定实际登录身份，任务和项目各展示最近 100 条记录。失败或重复角色变更不重复写审计。
- **检索与统计**：参数化 SQL，字面关键词搜索、状态 / 优先级筛选与分页；每个项目独立统计总数、逾期数与完成比例。
- **持久化**：文件 H2 保留账号、项目、成员和任务。旧版本机任务采用增量迁移，归入初始演示项目并清除原自由文本分派。

## 技术栈

Java 21、Spring Boot 4.1.1、Spring Security 7.1.1、Spring JDBC、Bean Validation、H2；Vue 3、Vite、Node 原生测试框架。前端构建为 Spring Boot 静态资源，正式本机演示使用同源 API。

## 本机运行

需要 Java 21、Maven 3.6.3+，以及符合 `frontend/package.json` 中 `engines` 的 Node.js。

```sh
git clone https://github.com/bigbang1438/teamflow-java.git
cd teamflow-java/frontend
npm ci
npm test
npm run build
cd ..
mvn verify
```

首次启动空数据库时必须设置 `TEAMFLOW_DEMO_PASSWORD`：至少 12 个字符，UTF-8 编码最多 72 字节。仅初始化以下**虚拟本机账号**，不会写入源码或控制台。四个账号共用此本机配置密码，便于演示角色差异。

| 账号 | 初始演示项目权限 |
| --- | --- |
| `demo_owner` | 项目负责人 |
| `demo_editor` | 编辑者 |
| `demo_viewer` | 只读成员 |
| `demo_guest` | 未加入；可创建自己的项目 |

Windows PowerShell：

```powershell
$demoPassword = Read-Host '设置虚拟演示账号密码（至少 12 字符）' -AsSecureString
$env:TEAMFLOW_DEMO_PASSWORD = [System.Net.NetworkCredential]::new('', $demoPassword).Password
java -jar target/teamflow-java-1.0.0.jar
```

macOS / Linux：

```sh
read -r -s -p 'Local demo password: ' TEAMFLOW_DEMO_PASSWORD
export TEAMFLOW_DEMO_PASSWORD
java -jar target/teamflow-java-1.0.0.jar
```

打开 `http://127.0.0.1:8080`。账号已初始化后，重启使用已存哈希，不会通过环境变量自动重置密码。数据库默认 `data/teamflow.mv.db`；新库可通过 `TEAMFLOW_DB_URL` 指定另一个文件路径。请勿在学习数据库录入真实敏感业务数据。

PowerShell 若限制脚本执行，可直接用 `npm.cmd`、`mvn.cmd` 和 `java.exe`，无须更改系统策略。端口占用可追加 `--server.port=8081`。

## 完整协作演示

1. 用 owner 登录创建项目，添加 editor 为编辑者、viewer 为只读成员。
2. 新建任务并分派给编辑者，设置优先级、说明与截止日期。
3. 用编辑者登录：进入同一项目，从待开始推进至进行中，在讨论中发布验收说明，再完成任务。
4. 用只读成员登录：可查阅任务和讨论，不能创建、编辑、评论或管理成员；服务端也会拒绝这些请求。
5. 用 guest 登录：看不到该项目，不能凭任务 ID 访问任务或统计。
6. 用负责人将编辑者改为只读：分派清除、版本递增，活动记录保留操作者。
7. 两个页面打开同一任务，先保存其中一个，再用旧页面保存，确认冲突；加载最新内容后再次确认修改。
8. 重启后重新登录，确认项目、任务、成员、评论与活动保留。

## 验证

```sh
mvn verify
cd frontend
npm test
npm run build
```

后端测试发出真实 HTTP 请求，包含 Cookie / CSRF 登录流程，覆盖角色授权、资源隔离、角色变更与分派一致性、评论操作者、CAS 版本冲突、两个并发写入者、事务日志、过滤分页、输入校验及文件数据库关闭重启。前端规则和 API 测试验证角色动作、项目路径、分派选项、会话 Token 更新与错误提示；不替代浏览器交互验证。

详细设计与验证记录见 [实现记录](docs/IMPLEMENTATION.md)，接口见 [API](docs/API.md)。

## 设计取舍与边界

默认绑定 `127.0.0.1`，额外检查本机 Host 与跨 Origin 写入，使用安全响应头和纯文本插值。Cookie 在本机 HTTP 下没有 Secure 标志；公网需要 HTTPS 和 Secure Cookie。密码 / Token 不存浏览器 localStorage，错误响应不返回 SQL 或堆栈。

为保证小规模本机演示中角色撤销与分派的一致性，写入事务先锁定所属项目，再校验成员角色。不同项目独立写入，同项目写入短暂串行；CAS 版本号处理客户端旧视图。未通过压力测试宣称吞吐量或性能提升。

四个固定虚拟账号，不包含公众注册、邀请邮件、密码找回、生产限流、HTTPS 终止、分布式会话、实时推送、附件或生产迁移平台。未知长度的流式请求没有完整应用层限流。H2 账号 sa 和默认空数据库密码仅用于本机；`TEAMFLOW_DB_PASSWORD` 可在新库首次创建时设置。文件数据受系统权限约束。

## 官方参考与许可

- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Security 会话管理](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

[MIT](LICENSE)。不需要付费服务或外部 API Key。
