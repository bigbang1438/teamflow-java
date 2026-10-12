# TeamFlow · Java 任务协作平台

这是 **AI 辅助开发的个人学习作品**，用于展示可复现的工程实现与测试，不代表商业生产系统或实习经历。

一个面向本地演示的全栈任务管理项目。通过中文看板管理任务，记录任务修改和状态变化；两个页面同时修改同一任务时，后提交的旧版本会收到冲突提示。

仓库：[bigbang1438/teamflow-java](https://github.com/bigbang1438/teamflow-java)

## 已实现

- 新建和编辑任务：标题、描述、负责人、优先级、截止日期；前后端长度与取值校验。
- 待办、进行中、受阻、已完成四列看板；服务端执行明确的状态流转规则。
- 标题、描述和负责人关键词搜索，状态与优先级筛选，分页查询。
- 任务版本号与条件更新：`UPDATE ... WHERE id = ? AND version = ?` 防止旧数据覆盖新数据。
- 任务修改和活动记录处于同一数据库事务，失败操作不会生成活动记录。
- 任务总数、各状态数量、逾期未完成数、完成比例统计。
- 文件型 H2 保存数据；首次启动为空，可通过界面添加标明用途的演示任务。
- 详情与最近 100 条活动记录，加载、空状态、提交失败和冲突提示。

## 技术栈

Java 21、Spring Boot 4.1.1、Spring JDBC、Bean Validation、H2；Vue 3、Vite。前端构建后作为 Spring Boot 静态资源，浏览器和 API 使用同一来源。

Spring Boot 版本依据：[官方系统要求](https://docs.spring.io/spring-boot/system-requirements.html)。项目使用公开依赖，没有付费服务或 API Key。

## 运行

需要 Java 21、Maven 3.6.3+，以及符合 `frontend/package.json` 中 `engines` 要求的 Node.js。

```sh
git clone https://github.com/bigbang1438/teamflow-java.git
cd teamflow-java/frontend
npm ci
npm test
npm run build
cd ..
mvn verify
java -jar target/teamflow-java-1.0.0.jar
```

打开 `http://127.0.0.1:8080`。默认只监听本机。`data/teamflow.mv.db` 保存本地任务，重启不会清空。请勿向演示数据库填写敏感信息。

Windows PowerShell 如果命令名被脚本执行策略限制，可使用 `npm.cmd`、`mvn.cmd` 和 `java.exe`，无需更改系统策略。若端口占用，用 `java -jar target/teamflow-java-1.0.0.jar --server.port=8081` 指定空闲端口。

前端开发时，在仓库根目录启动 `mvn spring-boot:run`，另一个终端进入 `frontend` 执行 `npm run dev`。Vite 仅绑定本机，通过开发代理连接后端；正式构建使用同源请求。

## 测试

```sh
mvn test
cd frontend
npm test
npm run build
npm audit
```

后端测试启动真实 HTTP 服务，覆盖任务读写、输入校验、检索筛选与分页、状态流转、重复状态、版本冲突、两个并发写入者、活动记录、统计与安全响应。文件数据库测试会关闭应用并重新启动，确认任务及创建记录仍存在。前端规则测试使用 Node 内置测试框架，验证状态规则、分组、提交内容和错误提示；它不代替浏览器交互验证。

## 接口与设计

见 [API 文档](docs/API.md) 和 [实现与验证记录](docs/IMPLEMENTATION.md)。

## 演示边界

这是本地单用户作品，没有登录、多租户、真实成员账户或权限分级。负责人是任务上的文字字段。协作冲突可通过多个标签页模拟，没有实时推送或生产压力测试。

默认 H2 用户 `sa` 与空密码仅用于本机演示，数据库控制台关闭；数据库文件也受操作系统权限约束。可以用 `TEAMFLOW_DB_PASSWORD` 设置本地密码，并用 `TEAMFLOW_DB_URL` 指定其他 H2 文件位置。已建数据库修改密码需要单独迁移，不能直接更换环境变量。

服务检查本机 Host、跨来源写入和已知请求长度，使用参数化 SQL、文本插值和安全响应头；这些措施不构成公网部署认证体系。若要公网部署，需另行设计认证授权、CSRF、HTTPS、限流、审计保留、数据库迁移和备份策略。请求没有 `Content-Length` 时，当前大小检查不提供完整的流式请求限制。

所有日期按 ISO 格式传输，逾期判断采用 UTC 日期；活动时间为 UTC 时间戳，由界面按浏览器时区显示。

## 许可

[MIT](LICENSE)。
