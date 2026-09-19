# Markdown Module Driven Development (MMDD)

> **模块即契约，文档即源码。**

MMDD 是一种以 Markdown 文件为核心契约的软件开发方法论。每个模块通过三个 Markdown 文件（描述、接口、验证）声明其完整契约，AI 根据契约生成和维护代码，人类只需维护 Markdown。

---

## 核心思想

```
┌─────────────────────────────────────────────────────────────┐
│                     MMDD 核心循环                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│   │  desc.md │───→│interface │───→│ verify.md│             │
│   │  (目的)   │    │  (契约)   │    │ (验证)   │             │
│   └──────────┘    └──────────┘    └──────────┘             │
│        │               │               │                    │
│        └───────────────┴───────────────┘                    │
│                        │                                    │
│                        ▼                                    │
│              ┌──────────────────┐                          │
│              │   AI 代码生成器   │                          │
│              │  (Markdown → 代码)│                          │
│              └──────────────────┘                          │
│                        │                                    │
│                        ▼                                    │
│              ┌──────────────────┐                          │
│              │   impl/ 代码实现  │                          │
│              │  (可重新生成)     │                          │
│              └──────────────────┘                          │
│                        │                                    │
│                        ▼                                    │
│              ┌──────────────────┐                          │
│              │   验证执行器      │                          │
│              │ (verify.md → 测试)│                          │
│              └──────────────────┘                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 三大契约文件

每个模块由三个 Markdown 文件组成，构成完整的模块契约：

| 文件 | 全称 | 作用 | 对应传统开发 |
|------|------|------|-------------|
| `desc.md` | Description | **模块目的** — 为什么存在这个模块 | 架构文档 / README |
| `interface.md` | Interface Definition | **接口契约** — 输入输出、类型、相应环境、规范、错误码 | API 文档 / 接口定义 |
| `verify.md` | Verification Spec | **验证规范** — 测试场景、不变量、验收标准 | 测试计划 / 单元测试 |

---

## 模块嵌套与组合

MMDD 支持模块间的显式依赖和契约引用：

```
system/
├── system.md                          ← 系统总览与模块组合规则，项目入口
├── modules/
│   ├── user/
│   │   ├── docs/                      ← 模块契约（Markdown）
│   │   │   ├── desc.md
│   │   │   ├── interface.md           ← 定义 User 类型
│   │   │   └── verify.md
│   │   └── impl/                      ← AI 生成的代码
│   │
│   ├── task/
│   │   ├── docs/
│   │   │   ├── desc.md
│   │   │   ├── interface.md           ← 引用 ../user/docs/interface.md
│   │   │   └── verify.md
│   │   └── impl/
│   │
│   └── notification/
│       ├── docs/
│       │   ├── desc.md
│       │   ├── interface.md           ← 引用 ../user + ../task
│       │   └── verify.md
│       └── impl/
│
└── .modspec/
    └── config.yml                     ← 框架配置
```

### 依赖声明

在 `desc.md` 的 YAML Front Matter 中显式声明依赖：

```yaml
---
module: task
version: 1.0.0
depends_on: [user]        ← 声明依赖 user 模块
---
```

在 `interface.md` 中引用其他模块的契约：

```markdown
## Dependencies
- [User Module](../user/docs/interface.md) — 需要验证 user_id 存在
```

---

## 工具链工作流

```bash
# 1. 初始化模块
modspec init modules/payment

# 2. 生成代码（Markdown → 代码）
modspec generate modules/user

# 3. 验证模块（执行 verify.md 中的测试规范）
modspec verify modules/user

# 4. 系统组装检查（依赖图 + 接口兼容性）
modspec compose

# 5. 重新生成（MD 变更后同步代码）
modspec regenerate modules/user
```

---

## 与 SDD 的区别

| 维度 | SDD (Spec-Driven) | MMDD (Markdown Module Driven) |
|------|-------------------|------------------------------|
| **管理单元** | Feature（功能） | Module（模块/组件） |
| **Spec 定位** | 一次性开发流程产物 | **持久化契约**，随模块生命周期存在 |
| **文件结构** | `docs/specs/01-feature/` | `modules/name/{docs,impl}/`，契约文件位于 `docs/{desc,interface,verify}.md` |
| **嵌套方式** | 不原生支持 | **核心能力**：模块间显式依赖和契约引用 |
| **代码关系** | Spec → 生成代码 → 代码独立 | **Spec 与代码共生**，Spec 变更自动同步代码 |
| **系统组装** | 按 feature 逐个实现 | **按模块拼接（composition）**组成系统 |

---

## 示例项目

本仓库包含一个完整的示例项目 **TaskFlow**（极简任务管理系统），演示了 MMDD 的完整实践：

- `system.md` — 系统总览
- `modules/user/` — 用户模块（无依赖）
- `modules/task/` — 任务模块（依赖 user）
- `modules/notification/` — 通知模块（依赖 user + task）

---

## 核心原则

1. **Markdown 是唯一的真相来源** — 任何变更从修改 MD 开始，代码只是物化结果
2. **模块契约显式化** — 依赖、接口、验证全部写在 MD 中，不可隐式假设
3. **验证先行** — verify.md 定义"什么算完成"，代码生成必须满足验证清单
4. **组合优于继承** — 系统通过模块拼接组装，而非代码层面的继承耦合
5. **AI 是编译器** — 人类写契约，AI 负责把契约"编译"成可运行代码

---

## License

Apache-2.0
