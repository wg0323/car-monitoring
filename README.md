# car-monitoring

> 车辆监控平台：四个可独立启动的后端部署单元加一个 Vue 3 管理端，覆盖 JT/T 808 终端接入、JT/T 809 平台互联与浏览器端实时监控。

## 📋 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [部署指南](#部署指南)
- [API 文档](#api-文档)
- [常见问题](#常见问题)

## 项目简介

本工程是车辆监控平台：四个可独立启动的后端部署单元（808 网关、809 企业侧、809 监管侧、管理端 API）加一个 Vue 3 管理端，覆盖终端接入、定位与轨迹、围栏与线路、报表导出、报警处理、终端指令与上级平台互通。

技术栈为 JDK 17 / Spring Boot 4.1.2，模块边界由 Maven Enforcer 机械约束，四个后端进程与前端工程都能独立构建和启动；P1~P4 已交付，P5 验收已完成（见 [`docs/p5-acceptance-report.md`](docs/p5-acceptance-report.md)）。

### 历史沿革

本工程 2026-11 由旧系统 `New-Gps-src`（Java 7 / Spring 4 的五个 Maven 模块、156 个 JSP）按 P1~P5 阶段重写，2026-11-17 迁出为独立仓库。旧仓库保留原系统源码、旧库 `gps` / `gps_hisdata` 与 P0 摸底证据，不再演进；迁移计划与旧系统实测基线作为只读快照归档在 [`docs/legacy-baseline/`](docs/legacy-baseline/README.md)。本工程不回填历史数据，不引入 Spring Cloud。

逐项进度、待裁决清单与已知偏差见 [`docs/progress.md`](docs/progress.md)，每交付一个切片后更新；旧系统 122 条实测用例的迁移对照见 [`docs/legacy-parity-matrix.md`](docs/legacy-parity-matrix.md)。

### 核心功能

- [x] 八个 Maven 模块：四个共享模块、四个后端部署单元，边界由 `bannedDependencies` 约束
- [x] 四个可执行 JAR，外置配置、Logback、Actuator、Prometheus、优雅停机
- [x] `cm-admin-api` 安全基线：Spring Security、BCrypt、CSRF、Spring Session Redis、Knife4j/OpenAPI
- [x] `cm-admin-web` 交通调度蓝主题：侧边导航、动态路由入口、按钮级权限、底部实时面板与全局状态栏
- [x] 本地基础环境：MySQL 新库与账号、RabbitMQ 专用 vhost、MongoDB 轨迹库、Nginx 同源代理模板
- [x] CI 流水线与一键验证脚本
- [x] P2：20 张业务表的版本化 SQL 迁移、MyBatis-Plus 映射、复杂 XML、领域服务与 15 项新旧 SQL 逐行对照
- [x] P3：808 网关与指令链路（outbox 租约、confirm/return、ack 状态机、死信重放）、定位链路（MongoDB 时间序列、Redis 最新位置、8 分片）、在线租约对账、809 主从链路（V8 七张表，黄金帧逐字节相等）、报警与里程领域模型（V7）
- [ ] P3-S2：808 多媒体分包未实现（0x0801 / 0x0802 上报、0x8802 拍摄接收），按 2026-11-15 裁决不做，协议不补
- [x] P4：认证与会话、菜单与按钮权限、数据范围过滤、车辆列表与详情、实时监控、历史轨迹与回放导出、围栏与线路（圆形/矩形/多边形）、报表与导出、报警弹窗与处理、809 督办、终端指令与 809 链路命令、消息中心；系统配置按裁决不迁移
- [x] P5：全量协议回归（六个环境开关下 466 个用例全绿）、权限穿透矩阵、29 条旧用例逐条重放、故障注入（网关崩溃与终端重连、809 断链恢复）；长稳压测按裁决跳过，遗留项见验收报告

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| JDK | Oracle JDK（`release=17`） | 17.0.11 |
| 构建 | Maven / Node.js / npm | 3.9.8 / 22.22.2 / 10.9.7 |
| 后端框架 | Spring Boot / Spring Framework | 4.1.2 / 7.0.10 |
| ORM | MyBatis-Plus（Boot 4 starter） | 3.5.18 |
| 结构管理 | 版本化 SQL 脚本（`docs/db/migration`，人工执行） | V1～V11 |
| 关系库 | MySQL 8 / mysql-connector-j | 8.0（容器 `mysql-8.0`） / 9.7.0 |
| TCP | Netty（由 Boot BOM 管理） | 4.2.19.Final |
| 消息 | Spring AMQP / amqp-client | Boot 4.1.2 BOM / 5.31.0 |
| 缓存与会话 | Spring Data Redis / Spring Session | 容器 `redis-6.2.6` |
| 轨迹库 | MongoDB | 6.0.13（容器 `mongodb-6.2.6`） |
| API 文档 | Knife4j Boot4 starter / springdoc | 5.8.2 / 3.0.3 |
| JSON | Jackson 2 / 3（按 Knife4j 安全基线） | 2.22.4 / 3.1.8 |
| 前端 | Vue / Vue Router / Pinia | 3.5.45 / 4.6.5 / 2.3.1 |
| 前端构建 | Vite / TypeScript / Sass | 6.4.6 / 5.8.3 / 1.108.2 |
| UI 与图表 | Element Plus / ECharts | 2.14.8 / 5.6.0 |
| 监控 | Micrometer Prometheus | 由 Boot 4.1.2 BOM 管理 |

数据库、中间件与 Netty 的版本由 `spring-boot-starter-parent:4.1.2` 的 BOM 统一管理；`rabbit-amqp-client.version`、`jackson-bom.version`、`jackson-2-bom.version` 三处属性覆盖是为了收敛第三方依赖上界，理由写在根 `pom.xml` 注释里。

## 项目结构

```text
car-monitoring/
├── pom.xml                        # 父 pom：JDK 17、BOM、依赖版本唯一声明处、Enforcer 门禁
├── cm-protocol/                   # JT808/JT809 编解码边界，纯 Java，禁 Spring/Netty/DB/MQ
├── cm-domain/                     # 领域规则，含 PositionVersion 乱序判定
├── cm-infrastructure/             # MyBatis-Plus、MySQL、Redis、RabbitMQ、MongoDB、指标
├── cm-transport-netty/            # Netty 依赖与 ChannelRoute 连接 fencing
├── cm-gateway-808/                # 终端接入部署单元，管理端口 18081
├── cm-integration-809/            # 企业侧 JT809 部署单元，管理端口 18082
├── cm-gov-809/                    # 监管侧 JT809 部署单元，管理端口 18083
├── cm-admin-api/                  # 管理端 REST API，端口 8081
├── cm-admin-web/                  # Vue 3 管理端，Vite dev 端口 5173
├── deploy/                        # 建库脚本、RabbitMQ 初始化、隔离端口 compose 模板、Nginx 配置
│   └── p2/                        # P2 对照数据装载、新旧成对查询、schema 前置检查
├── scripts/                       # 依赖收敛检查、一键验证、本地启动、P2 逐行对照
├── docs/                          # 阶段报告、进度台账与数据库迁移脚本
│   ├── db/migration/              # 版本化结构脚本 V1～V11，人工按序执行，不随 JAR 发布
│   └── legacy-baseline/           # 旧系统迁移计划与实测基线（只读快照）
├── .github/workflows/ci.yml       # 后端与前端的 CI 门禁
└── .vscode/                       # UTF-8、JDK 17 编译 / JDK 25 语言服务器、调试配置
```

四个部署单元各自带 `application.yml`、`application-dev.yml`、`application-prod.yml` 与 `logback-spring.xml`；数据库迁移脚本不打进 JAR，`V1`～`V11` 统一放在仓库根的 `docs/db/migration/`，数据库结构由人工执行这些脚本维护（开发自己执行，生产由 DBA 执行），应用启动不做迁移。`cm-infrastructure` 的 `resources/mapper/` 放复杂查询 XML，由 `mybatis-plus.mapper-locations` 加载。

## 环境要求

- JDK 17（`JAVA_HOME` 指向 17.0.11），Maven 3.9.8，Node.js 22.22.2，npm 10.9.7。
- 本机常驻容器：`mysql-8.0`（3306，`root/123456`）、`redis-6.2.6`（6379，无密码）、`rabbitmq`（5672 / 15672）、`mongodb-6.2.6`（27017，bitnami MongoDB 6.0.13）。
- Windows 上执行 `.ps1` 用 `powershell -NoProfile -File`，脚本已带 UTF-8 BOM。

## 快速开始

### 本地环境初始化（一次性）

在仓库根目录执行。只在常驻容器里新建本工程专用的库、账号与 vhost，不改动旧库 `gps` / `gps_hisdata` 和 P0 的 `cm_p0_poc` / `cm_p0_capacity`。

```powershell
docker cp deploy\init-mysql.sql mysql-8.0:/tmp/init-mysql.sql
docker exec mysql-8.0 sh -c "mysql -uroot -p123456 < /tmp/init-mysql.sql"
docker cp deploy\init-rabbitmq.sh rabbitmq:/tmp/init-rabbitmq.sh
docker exec rabbitmq sh /tmp/init-rabbitmq.sh
docker exec mongodb-6.2.6 /opt/bitnami/mongodb/bin/mongosh --quiet --eval "db.getSiblingDB('car_monitoring_telemetry').createCollection('cm_p1_probe')"
```

### 执行数据库结构脚本

`cm-admin-api` 启动时不做迁移。空库需先把 `docs/db/migration` 下的脚本按版本号顺序执行一遍；已有库只执行新增版本。本机走容器：

```powershell
Get-ChildItem docs\db\migration\V*.sql | Sort-Object { [int](($_.BaseName -split '__')[0].TrimStart('V')) } | ForEach-Object {
    docker cp $_.FullName mysql-8.0:/tmp/migrate.sql
    docker exec mysql-8.0 sh -c "mysql -uroot -p123456 car_monitoring < /tmp/migrate.sql"
}
```

生产环境由 DBA 按同一顺序在目标库执行，仓库不提供迁移工具。

### 一键验证

```powershell
powershell -NoProfile -File .\scripts\verify.ps1
```

依次执行：子模块依赖版本收敛、Maven 全量打包、单元测试、Maven 依赖上界检查、前端类型检查、前端构建。

### P2 新旧 SQL 对照

前置条件：`mysql-8.0` 容器里旧库 `gps` 完整、`car_monitoring` 已按顺序执行 `docs/db/migration` 的 V1～V4，且 `deploy/init-mysql.sql` 已给 `cm_app` 授过旧库只读权限。MySQL root 口令默认取本机开发值，可用 `CM_P2_ROOT_PASSWORD` 覆盖。

```powershell
powershell -NoProfile -File .\scripts\verify-p2-parity.ps1
```

脚本从旧库只读抽取对照数据、重建新库 20 张 P2 业务表，再比对 15 个结果集的行数和逐行摘要。全 PASS 退出码 0；差异时退出码 1 并打印 case。只重跑比对加 `-SkipLoad`。过程产物在 `p2-evidence/`，不入库。

真实 MySQL 集成测试默认跳过（8 个用例：6 个 Mapper 查询链路对照、1 个事务回滚、1 个严格模式插入），显式开启：

```powershell
$env:CM_P2_DB_TEST = 'true'
mvn -B -ntp test "-Dmaven.test.skip=false"
```

默认门禁跑 P2 的 44 个用例中的 36 个；开启后 44 个全部执行。P3、P4 另有 `CM_P3_DB_TEST`、`CM_P3_MQ_TEST`、`CM_P4_DB_TEST` 三个开关，用法相同。`deploy/p2/parity-*.sql` 只验证装载的表内容，查询语义由 `P2MapperParityTest` 直接调用生产 Service / Mapper 验证。

### 后端启动

```powershell
powershell -NoProfile -File .\scripts\start-local.ps1
```

脚本注入本地开发凭据并启动四个 JAR，健康检查地址：

```text
http://127.0.0.1:18081/actuator/health   cm-gateway-808
http://127.0.0.1:18082/actuator/health   cm-integration-809
http://127.0.0.1:18083/actuator/health   cm-gov-809
http://127.0.0.1:8081/actuator/health    cm-admin-api
http://127.0.0.1:8081/api/v1/system/info 骨架探针
```

数据库结构不在应用启动时迁移：`docs/db/migration` 下的 `V1`～`V11` 由人按版本顺序执行——V1 建 `cm_schema_info`，V2/V3 建 20 张业务表，V4 修正对照实测发现的两处结构问题，V5/V6 建指令可靠性三表并调整 ack 归属唯一键，V7 补报警开单索引，V8 建 809 的 7 张表，V9 给 `cm_function` 加 `function_source`，V10 建地图区域表，V11 给 `cm_jt809_command` 加待下发索引。新库一次执行全部，已有库只执行新增版本；已执行过的脚本不要回改，改了重跑会撞 DDL 冲突。

### 前端启动

```powershell
cd cm-admin-web
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`。Vite 把 `/api` 与 `/ws` 代理到 `http://127.0.0.1:8081`，因此不用配置跨域。生产构建产物在 `cm-admin-web/dist/`，最小宽度 1280px。

## 配置说明

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `CM_DB_URL` | MySQL JDBC URL | `jdbc:mysql://127.0.0.1:3306/car_monitoring?...` |
| `CM_DB_USERNAME` / `CM_DB_PASSWORD` | 新库账号；dev Profile 在 `application-dev.yml` 内置本地口令，生产由环境变量注入 | `cm_app` / `CmApp@2026` |
| `CM_REDIS_HOST` / `CM_REDIS_PORT` / `CM_REDIS_DATABASE` | Redis 连接 | `127.0.0.1` / `6379` / `0` |
| `CM_MQ_HOST` / `CM_MQ_PORT` / `CM_MQ_VHOST` | RabbitMQ 连接，vhost 由 `init-rabbitmq.sh` 创建 | `127.0.0.1` / `5672` / `cm` |
| `CM_MONGO_URI` | 轨迹库连接，P3 接入时间序列集合 | `mongodb://127.0.0.1:27017/car_monitoring_telemetry` |
| `CM_SESSION_STORE` | Spring Session 存储类型 | `redis` |
| `CM_ADMIN_API_PORT` | 管理端 API 端口 | `8081` |
| `CM_GATEWAY_808_HTTP_PORT` 等 | 三个网关进程的管理端口 | `18081` / `18082` / `18083` |
| `CM_ID_WORKER_ID` / `CM_ID_DATACENTER_ID` | 雪花 ID 实例标识，多实例必须唯一（0~31） | `0` / `0` |
| `CM_DOC_PRODUCTION` | 是否屏蔽在线文档 | `false` |
| `SPRING_PROFILES_ACTIVE` | dev / prod Profile | `dev` |
| `CM_P2_DB_TEST` | 是否启用真实 MySQL 事务回滚集成测试；未设置时该测试跳过 | 未设置 |
| `CM_P3_DB_TEST` | 是否启用 P3 真库用例（租约并发、ack 状态机、死信重放） | 未设置 |
| `CM_P3_REDIS_TEST` | 是否启用 P3 真 Redis 用例（在线租约、最新位置、去重集合、里程归集） | 未设置 |
| `CM_P3_MQ_TEST` | 是否启用真实 broker 用例（confirm / mandatory return / 不可达） | 未设置 |
| `CM_P4_DB_TEST` | 是否启用 P4 真库与 HTTP 链路用例 | 未设置 |
| `CM_MAP_KEY` / `CM_MAP_SECURITY_CODE` | 高德地图 JS API 凭据，只走环境变量；缺 key 时围栏页提示地图不可用，区域列表仍可维护 | 空 |
| `CM_P2_DB_URL` / `CM_P2_DB_USERNAME` / `CM_P2_DB_PASSWORD` | 集成测试连接参数，只在 `CM_P2_DB_TEST=true` 时读取 | 本地开发库 / `cm_app` / 本地口令 |
| `CM_P2_ROOT_PASSWORD` | 对照脚本连接 MySQL 容器用的 root 口令，通过 `docker exec -e MYSQL_PWD` 传入 | 本地开发口令 |

把 `application*.yml` 放到 `car-monitoring/config/` 即可覆盖 JAR 内配置：`start-local.ps1` 已带 `--spring.config.additional-location=optional:file:./config/`。`deploy/.env.example` 是目标环境 compose 模板的变量样例，`deploy/.env` 不入库。

开发 Profile 的数据库与 RabbitMQ 口令写在各模块 `application-dev.yml`，IDE 直接启动或 `java -jar` 都不需要再注入环境变量；生产 Profile 仍走环境变量，缺凭据即启动失败。该口令与 `deploy/init-mysql.sql`、`deploy/init-rabbitmq.sh`、`deploy/.env.example` 是同一个值，修改时必须同步。

## 部署指南

```powershell
mvn -B -ntp clean package
```

四个可执行 JAR 分别是各模块 `target/` 下的 `cm-gateway-808.jar`、`cm-integration-809.jar`、`cm-gov-809.jar`、`cm-admin-api.jar`（`finalName` 已去掉版本号）。启动示例：

```powershell
java -Dfile.encoding=UTF-8 -jar cm-gateway-808\target\cm-gateway-808.jar --spring.config.additional-location=optional:file:./config/
```

JAR 里不含任何 SQL：结构变更由 DBA 按 `docs/db/migration` 的版本顺序在目标库执行，应用只负责读写。

日志写到启动目录下的 `logs/cm-<模块>.log`，按时间和大小滚动（10MB / 保留 30 天 / 上限 1GB）；`scripts/start-local.ps1` 另把控制台输出重定向到 `logs/*-stdout.log` 与 `*-stderr.log`。

前端构建产物由 Nginx 托管，`deploy/nginx.conf` 给出 `/api` 与 `/ws` 的同源代理骨架。生产环境设置 `CM_DOC_PRODUCTION=true` 关闭在线文档，并用环境变量提供真实凭据；`deploy/docker-compose.yml` 用隔离端口（3307 / 6380 / 5673 / 27018）提供干净的 P2/P3 测试环境，默认不启动，不与常驻容器冲突。

### 回退说明

回退按部署单元独立进行，不需要回退整库：

1. **只回退应用**：保留上一版四个 JAR 与前端 `dist/`，直接用旧产物重启对应进程；前后端 API 契约向后兼容，新旧版本可短暂共存。
2. **回退数据库变更**：结构脚本没有自动回滚。需要回退 `V10`（地图区域）或 `V11`（索引）时，在确认无数据依赖后手工执行对应 `DROP` / `DROP INDEX`，并把该版本脚本从 `docs/db/migration` 移出或标记作废；`V1`～`V9` 是业务表与 809 链表，不建议回退。
3. **回退种子数据**：权限菜单与演示账号来自 `deploy/p4/seed-auth.sql`（幂等）；重跑 P2 对照装载会清空 `cm_function` / `cm_user` / `cm_role`，必须重跑该脚本恢复。
4. **回退静态资源**：Nginx 换回上一版 `dist/` 目录并 reload；地图 key 为运行时环境变量，换回旧 key 无需改代码。
5. **回退验证**：重启后依次检查四个 `/actuator/health` 返回 UP、登录页可打开、`GET /api/v1/system/info` 返回预期阶段值、状态栏上线数与 Redis 在线租约一致。

## API 文档

启动 `cm-admin-api` 后访问 `http://127.0.0.1:8081/doc.html`（Knife4j），OpenAPI JSON 在 `http://127.0.0.1:8081/v3/api-docs`。当前暴露 15 个 Controller：`Auth`、`Vehicle`、`Department`、`User`、`Monitor`、`Track`、`Report`、`Fence`、`Map`、`Alarm`、`Command`、`GovCommand`、`UrgeTodo`、`Message`、`System`，另有两条 WebSocket：`/ws/monitor`（车辆位置订阅）与 `/ws/message`（聊天与广播）。骨架探针 `GET /api/v1/system/info` 返回应用名、升级阶段、激活 Profile 与 JDK 版本；升级阶段取自 `SystemInfoServiceImpl.STAGE`，当前值为 `P5`。系统配置域按裁决不迁移，`/system` 菜单保留占位页。生产环境用 `CM_DOC_PRODUCTION=true` 屏蔽文档。

`/actuator/prometheus` 受 Spring Security 保护，返回 401；生产接入监控时通过 Nginx 网络策略或专用账号开放。

## 常见问题

**Q: `cm-admin-api` 启动报 `ClassNotFoundException: org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties`？**

A: 引入了 Boot 3.x 专用的 `knife4j-openapi3-jakarta-spring-boot-starter`（内置 springdoc 2.9.1）。Boot 4.1 必须用 `com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.8.2`（内置 springdoc 3.0.3），父 pom 已锁定该坐标。

**Q: `start-local.ps1` 报端口已被占用？**

A: 脚本会打印 `OwningProcess`，按 PID 停止本轮启动的进程即可，不要按映像名批量杀 `java.exe`——VSCode 的 Java 语言服务器也是 Java 进程。

**Q: PowerShell 脚本里中文报错或字符串解析失败？**

A: 三个 `.ps1` 已写为 UTF-8 BOM，用 `powershell -NoProfile -File <脚本>` 执行；`check-dependency-convergence.ps1` 另用 `System.Text.UTF8Encoding` 显式读取子模块 pom，避免 Windows PowerShell 5.1 按 GBK 解码中文描述。

**Q: `mvn test` 没有跑测试？**

A: 根 pom 设了 `maven.test.skip=true`（打包提速）。显式跑测试用 `mvn -B -ntp test "-Dmaven.test.skip=false"`，一键脚本已经包含这一步。

**Q: 依赖上界检查为什么有豁免？**

A: Jackson 2 / 3 与 `amqp-client` 已按 Knife4j 官方基线用属性覆盖收敛，不豁免。只剩 `commons-lang3` 与 `commons-logging` 两处由 Boot 4.1.2 BOM 统一管理的第三方声明差异，在 `dependency-convergence` profile 里显式豁免并在父 pom 写明复核条件。逐条处置见 `docs/p1-skeleton-report.md` 第 8 节。

**Q: P2 为什么没用 MyBatis-Plus 的分页插件，`IService` 的包名也和常见写法不一样？**

A: 3.5.18 把 `IService` / `ServiceImpl` 移到 `mybatis-plus-spring` 模块的 `com.baomidou.mybatisplus.spring.service` 包，并把 `PaginationInnerInterceptor` 拆到 `mybatis-plus-jsqlparser` 模块。本机仓库没有与 3.5.18 匹配的 jsqlparser 构件，因此 P2 用显式 `LIMIT #{offset}, #{size}` 加独立 `COUNT(*)` 查询分页，不引入版本不确定的依赖。分页结果仍用 MyBatis-Plus 的 `IPage` 承载，P3/P4 若补齐该模块可以再切换。

**Q: `verify-p2-parity.ps1` 报 Schema precheck failed？**

A: 前置检查核对三件事：20 张业务表齐备、`cm_operation_log.ip` 已放宽到 512、`cm_dict_item` 的 `uk_dict_type_code` 已按 V4 换成 `idx_dict_type_code`，三项全中才返回 3。缺项说明 `docs/db/migration` 的 V1～V4 没按顺序执行完。旧库 `gps` 必须完整；脚本不会替你重建旧库。
