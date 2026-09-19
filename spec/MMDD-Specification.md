# MMDD 框架规范 v1.0

> Markdown Module Driven Development Specification

---

## 1. 概述

MMDD（Markdown Module Driven Development）是一种以 Markdown 文件作为模块核心契约的软件开发方法论。它将模块的**目的\需求**、**接口\调用方法**和**验证\模块测试**三个维度显式声明为 Markdown 文件，由 AI 根据契约生成和维护代码实现。

### 1.1 设计哲学

- **契约即代码**：模块的 Markdown 文件是模块的"源代码"，impl/ 目录下的代码是"编译产物"
- **显式优于隐式**：所有依赖、接口、约束必须在 Markdown 中显式声明
- **验证驱动**：verify.md 定义模块完成的唯一标准
- **组合构建系统**：系统通过模块的显式依赖图拼接而成

### 1.2 术语定义

| 术语 | 定义 |
|------|------|
| **模块 (Module)** | 具有独立职责的软件单元，由 `docs/` 下的 desc.md + interface.md + verify.md 定义 |
| **契约 (Contract)** | 模块对外暴露的承诺，包括接口签名、行为约束、错误契约 |
| **物化 (Materialization)** | AI 根据 Markdown 契约生成 impl/ 目录下可运行代码的过程 |
| **验证清单 (Verification Checklist)** | verify.md 中的可勾选条目，对应测试用例 |
| **依赖图 (Dependency Graph)** | 模块间 depends_on 关系构成的有向无环图 |

---

## 2. 文件规范

### 2.1 目录结构标准

```
<project-root>/
├── system.md                          # 系统级规范（必须，定义入口）
├── modules/
│   └── <module-name>/
│       ├── docs/                      # 模块契约（Markdown，必须）
│       │   ├── desc.md                # 模块描述（必须）
│       │   ├── interface.md           # 接口定义（必须）
│       │   └── verify.md              # 验证规范（必须）
│       └── impl/                      # 代码实现（AI 生成，可重新生成）
│           ├── __init__.py
│           ├── <module>.py
│           └── test_<module>.py
└── .modspec/
    └── config.yml                     # 框架配置
```

每个模块目录下必须且仅有两类内容：`docs/` 存放契约文件，`impl/` 存放物化代码。

### 2.2 desc.md 规范

**文件名**：`desc.md`

**作用**：声明模块的目的、职责边界、非目标、依赖关系。

**格式**：

```markdown
---
module: <模块标识符>
version: <语义化版本>
depends_on: [<依赖模块列表>]
---

# <Module Name>

## Purpose
<一段清晰的描述，说明这个模块为什么存在>

## Responsibilities
- <职责 1>
- <职责 2>

## Non-Goals
- <明确声明不在本模块范围内的事项>

## Dependencies
- [模块名](../<模块名>/docs/interface.md) — <依赖原因>
```

**约束**：
- `module` 字段必须全局唯一
- `depends_on` 中的模块必须在 `modules/` 下存在
- 依赖图必须是有向无环图（DAG）

### 2.3 interface.md 规范

**文件名**：`interface.md`

**作用**：定义模块的对外接口，包括类型、函数/方法、错误契约、使用示例。

**格式**：

```markdown
---
module: <模块标识符>
---

# <Module Name> Interface

## Types

### <TypeName>
| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| <字段> | <类型> | <是/否> | <约束条件> |

## API

### <function_name>(<参数>) → <返回类型>
<功能描述>

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| <参数> | <类型> | <是/否> | <描述> |

**Errors:**
- `<ErrorType>("<message>")` — <触发条件>

**Usage Example:**
```<语言>
<代码示例>
```

## Storage
<存储方式说明>
```

**约束**：
- 所有公共 API 必须在 interface.md 中声明
- 类型定义必须使用表格形式，确保 AI 可解析
- 错误码/错误消息必须精确匹配 verify.md 中的测试预期

### 2.4 verify.md 规范

**文件名**：`verify.md`

**作用**：定义模块的验证标准，是模块"完成"的唯一依据。

**格式**：

```markdown
---
module: <模块标识符>
---

# <Module Name> Verification

## Unit Tests

### <function_name>
- [ ] <测试场景 1>
- [ ] <测试场景 2>

## Integration Tests
- [ ] <端到端场景 1>

## Invariants（不变量）
- <必须在所有情况下保持为真的约束>
```

**约束**：
- 每个 `- [ ]` 条目必须对应一个可自动化的测试用例
- Invariants 是模块的"永恒真理"，代码实现必须保证
- verify.md 的 checklist 是模块发布的门禁

---

## 3. 模块依赖与引用机制

### 3.1 依赖声明

模块依赖通过两个层面声明：

1. **desc.md 的 `depends_on`**：声明模块级别的依赖关系，用于构建依赖图
2. **interface.md 的 `Dependencies` 章节**：显式引用其他模块的契约文件

### 3.2 契约引用语法

在 interface.md 中引用其他模块的类型或接口：

```markdown
## Dependencies
- [User Module](../user/docs/interface.md) — 需要 User 类型和 get_user 函数

## Types

### Order
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| user_id | str | yes | 引用 [User.id](../user/docs/interface.md#user) |
```

### 3.3 依赖图构建规则

1. 解析所有 `desc.md` 的 `depends_on` 字段
2. 检查依赖图中无环（DAG 验证）
3. 按拓扑排序确定代码生成顺序
4. 生成模块时，自动加载依赖模块的 `interface.md` 作为上下文

---

## 4. 代码生成规范

### 4.1 物化过程

```
输入: modules/<name>/docs/{desc.md, interface.md, verify.md}
      + 依赖模块的 docs/interface.md
      + .modspec/config.yml

处理:
  1. 解析 Markdown → 结构化契约数据
  2. 加载依赖模块的契约作为上下文
  3. 构建 Prompt（契约 + 生成规则 + 语言模板）
  4. AI 生成 impl/ 目录下的代码
  5. 根据 verify.md 生成测试代码

输出: modules/<name>/impl/
```

### 4.2 生成规则

- **类型映射**：interface.md 中的表格类型映射为目标语言的类型系统
- **错误契约**：interface.md 中声明的错误必须在代码中精确抛出
- **存储实现**：desc.md / interface.md 中声明的存储方式决定持久化策略
- **不变量保障**：verify.md 中的 Invariants 必须内建于代码逻辑中

### 4.3 增量更新

当 Markdown 变更时，框架应支持增量重新生成：

1. 对比新旧契约，识别变更范围
2. 仅重新生成受影响的代码文件
3. 保留未变更的实现逻辑
4. 更新测试以覆盖新增场景

---

## 5. 验证执行规范

### 5.1 验证流程

```
输入: modules/<name>/docs/verify.md
      + modules/<name>/impl/

处理:
  1. 解析 verify.md 的 checklist
  2. 将每个 - [ ] 条目映射为测试用例
  3. 执行测试（单元测试 + 集成测试）
  4. 检查 Invariants（静态分析或运行时断言）
  5. 输出覆盖率报告

输出: 验证报告（通过/失败 + 覆盖率）
```

### 5.2 验证门禁

模块只有通过以下检查才能标记为"完成"：

1. verify.md 中所有 `- [ ]` 变为 `- [x]`
2. 单元测试通过率 100%
3. 集成测试通过率 100%
4. Invariants 检查通过

---

## 6. 系统组装规范

### 6.1 compose 检查项

`modspec compose` 执行以下系统级检查：

1. **依赖完整性**：所有 `depends_on` 的模块都存在
2. **接口兼容性**：模块 A 引用模块 B 的类型时，B 必须定义该类型
3. **类型一致性**：同名类型在不同模块中的定义必须兼容
4. **循环依赖**：依赖图必须无环
5. **入口完整性**：system.md 中声明的模块都存在于 modules/ 下

### 6.2 集成测试生成

compose 阶段自动生成跨模块的集成测试：

- 遍历 system.md 中的业务流程
- 按依赖图拓扑排序串联模块调用
- 验证端到端数据一致性

---

## 7. 配置规范

### 7.1 .modspec/config.yml

```yaml
framework: MMDD
version: "1.0.0"

# 目标语言配置
target:
  language: python
  version: "3.11"
  package_manager: uv

# 模块规范
module:
  contract_dir: docs        # 契约文件所在子目录
  required_files:
    - docs/desc.md
    - docs/interface.md
    - docs/verify.md

# 代码生成配置
generation:
  ai_provider: openai
  model: gpt-4o
  temperature: 0.2

# 验证配置
verification:
  test_framework: pytest
  coverage_threshold: 100  # verify.md checklist 覆盖率

# 依赖解析
dependency:
  max_depth: 10            # 依赖图最大深度
  allow_cycles: false      # 禁止循环依赖
```

---

## 8. 版本与演进

### 8.1 模块版本管理

- 模块版本遵循语义化版本（SemVer）
- 版本声明在 desc.md 的 YAML Front Matter 中
- 模块升级时，依赖该模块的其他模块需重新验证兼容性

### 8.2 契约变更流程

1. 修改 Markdown 契约文件
2. 更新版本号（如需要）
3. 执行 `modspec regenerate`
4. 执行 `modspec verify`
5. 执行 `modspec compose`（检查系统级影响）
6. 提交变更（契约文件 + 重新生成的代码）

---

## 附录 A：与相关概念对比

| 特性 | MMDD | SDD | API-First | TDD |
|------|------|-----|-----------|-----|
| 契约语言 | Markdown | Markdown | OpenAPI/Swagger | 代码 |
| 管理单元 | Module | Feature | Endpoint | Function |
| 代码生成 | AI 物化 | AI 辅助 | 代码生成器 | 手工 |
| 验证驱动 | verify.md | 验收标准 | 契约测试 | 单元测试 |
| 模块组合 | 原生支持 | 不支持 | 不支持 | 不支持 |
| 嵌套引用 | 支持 | 不支持 | 不支持 | 不支持 |

---

## 附录 B：完整示例

参见本仓库 `modules/` 目录下的 TaskFlow 示例项目。
