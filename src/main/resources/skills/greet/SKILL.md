---
name: greet
title: Greet
description: 通过 echo 工具发送问候（内置示例技能）；在用户需要验证技能链路与 echo 工具时使用。
minSophonVersion: "0.1.0"
requiredTools:
  - echo
---

# Greet

## 调用方式

技能 id：**`greet`**（`invoke_skill` 的 `skill_id`）。

## 参数（JSON）

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 问候对象名称，可选，默认 `there` |

示例：`{"name":"世界"}`
