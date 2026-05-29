**项目总体架构**

**技术栈**

- 开发语言：Java 17
- 框架：Spring Boot 3.2.5 + Spring AI 1.0.0
- 大模型：DeepSeek Chat + DashScope Embedding
- 构建工具：Maven（多模块）
- 持久层框架：MyBatis 3.0.3
- 消息队列：RabbitMQ（延时队列 x-delayed-message）
- 缓存：Caffeine 本地缓存 + Redis
- 分布式锁：Redisson
- 限流熔断：Resilience4j
- 实时通信：WebSocket
- 任务调度：Spring Task（兜底容灾）
- 接口文档：Knife4j + OpenAPI 3
- 链路监控：Micrometer Tracing + Prometheus

 

**项目结构**

采用多模块Maven项目架构，实现了良好的模块解耦和代码复用：

 

**详细模块分析**

1. campus-common 公共模块

职责：提供项目通用基础功能

 

包结构详解：

- constant/ - 系统常量定义（状态码、业务常量等）
- context/ - 上下文管理（用户会话、线程本地变量等）
- enumeration/ - 枚举类型（订单状态、支付方式等）
- exception/ - 自定义异常类（业务异常、系统异常等）
- json/ - JSON序列化配置（日期格式、字段映射等）
- properties/ - 配置属性类（阿里云、微信等第三方配置）
- result/ - 统一返回结果封装（成功/失败响应格式）
- utils/ - 工具类（加密、文件处理、HTTP请求等）

 

2. campus-pojo 数据对象模块

职责：定义项目中所有数据传输对象

 

包结构详解：

- dto/ - 数据传输对象（Data Transfer Object）
  - 接收前端请求参数
  - 封装查询条件
  - 处理表单数据
- entity/ - 数据库实体类
  - 对应数据库表结构
  - 包含完整的属性映射
- vo/ - 视图对象（View Object）
  - 返回给前端的数据格式
  - 业务数据的展示层封装

 

3. campus-server 核心业务模块

职责：实现所有业务逻辑和系统功能

 

详细包结构：

 

- 控制层 (controller/)
  - 管理端控制器：员工、分类、菜品、套餐管理
  - 用户端控制器：用户注册登录、下单、购物车
  - 公共控制器：文件上传、数据统计
- 服务层 (service/)
  - 业务逻辑实现：核心业务处理
  - 数据校验：参数验证、业务规则校验
  - 事务管理：数据一致性保证
- 数据访问层 (mapper/)
  - MyBatis映射器接口：数据库操作定义
  - XML映射文件：SQL语句配置
- 配置层 (config/)
  - Redis配置：缓存管理
  - OSS配置：文件存储
  - WebMvc配置：消息转换器、拦截器注册
  - MyBatis配置：分页插件、类型处理器
- 切面编程 (aspect/)
  - 操作日志切面：记录管理员操作
  - 性能监控切面：接口耗时统计
- 拦截器 (interceptor/)
  - JWT令牌验证：用户身份认证
  - 权限控制：接口访问权限
- 异常处理 (handler/)
  - 全局异常处理器：统一异常响应格式
  - SQL异常处理：数据库异常转换
- 实时通信 (websocket/)
  - 订单状态推送：实时通知管理端
  - 消息广播：系统通知推送
- 定时任务 (task/)
  - 订单状态处理：超时订单自动取消
  - 数据清理：过期数据定时清理
  - 核心业务功能模块
- 基础数据管理
  - 员工管理：登录认证、权限控制、员工信息CRUD
  - 分类管理：菜品分类、套餐分类的层级管理
  - 地址簿管理：用户收货地址的增删改查

 

商品管理

- 菜品管理：
  - 菜品基本信息（名称、价格、描述、图片）
  - 菜品口味配置（辣度、温度、规格等）
  - 菜品状态控制（起售/停售）
- 套餐管理：
  - 套餐基本信息管理
  - 套餐菜品关联管理
  - 套餐价格策略

 

用户端功能

- 用户管理：微信登录、用户信息维护
- 购物车：商品添加、数量调整、清空操作

 

- 订单管理：
  - 订单创建（地址选择、支付方式）
  - 订单状态跟踪（待付款、已接单、配送中等）
  - 订单历史查询

 

- 订单处理流程
  - 订单详情：商品明细、价格计算、优惠处理
  - 状态流转：从下单到完成的完整生命周期
  - 实时通知：WebSocket推送订单状态变更
- 配置管理
- 多环境配置
  - application.yml - 主配置文件
  - application-dev.yml - 开发环境配置
  - 支持生产、测试等多环境切换
  - MyBatis映射配置

 

包含11个核心业务的完整SQL映射：

- 员工、分类、菜品、套餐管理的CRUD操作
- 用户、地址、购物车的数据操作
- 订单及订单详情的复杂查询

 

4. campus-ai 智能客服模块

职责：通用 AI 引擎，零业务依赖，可接入任何 Spring Boot 3 系统

核心架构：

- **ReAct Agent 引擎**
  - 自研 Thought → Action → Observation → Final Answer 循环
  - 正则解析 LLM 输出，按标签调度工具执行
  - maxIterations=10 防无限循环，格式纠错重试机制
- **Function Calling 工具调度**
  - ToolRegistry 注册中心 + ToolProvider SPI 扩展点
  - 内置 searchDishes / getOrderStatus / searchKnowledge 三工具
  - 工具描述引导 LLM 精准调用，避免多余工具调用
- **三层记忆系统**
  - 工作记忆：LLM context window 内的当前对话
  - 短期记忆：RedisChatMemory 滑动窗口（max 20条）+ LLM 摘要压缩，24h TTL
  - 长期记忆：LongTermMemoryService 语义检索 + Redis 持久化 + MemoryExtractor 自动提取用户事实，启动时从 Redis 回灌 VectorStore
- **RAG 知识检索**
  - KnowledgeProvider SPI 扩展点，宿主系统播种业务知识
  - SimpleVectorStore 向量存储 + DashScope Embedding
  - KnowledgeBaseService.topK=3 语义检索

**项目特色**

- 架构优势
  - 模块化设计：清晰的分层和模块划分
  - 代码复用：公共组件统一管理
  - 配置外化：环境配置灵活切换
  - 异常统一处理：规范的错误响应
- 技术亮点
  - JWT认证：无状态用户认证
  - AOP切面：横切关注点统一处理
  - WebSocket：实时双向通信
  - RabbitMQ延时队列：实时订单超时取消（x-delayed-message），Spring Task 兜底容灾
  - MyBatis增强：XML配置灵活的SQL操作