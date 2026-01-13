---
name: dmn-executor
description: >
  Execute DMN (Decision Model and Notation) 1.5 decision models to automate 
  business rule evaluation. Use when you need to (1) Evaluate business rules 
  defined in DMN files, (2) Calculate pricing, eligibility, risk scores, or 
  other decision logic, (3) Execute decision tables with complex conditions, 
  (4) Run FEEL (Friendly Enough Expression Language) expressions. Do NOT use 
  for general programming, data processing without DMN models, or tasks without 
  pre-defined decision logic. Requires Java 17+ and JBang.
---

# DMN Executor Skill

Execute DMN 1.5 decision models via a JSON-in/JSON-out CLI interface wrapping Apache KIE engine.

## Installation

```bash
# 1. Verify Java 17+
java -version

# 2. Install JBang
curl -Ls https://sh.jbang.dev | bash -s - app setup

# 3. Test installation (first run ~30s for deps, then ~2s)
jbang scripts/DmnExecutor.java help

# 4. Test with example (greeting.dmn in assets/)
jbang scripts/DmnExecutor.java execute assets/greeting.dmn '{"Name": "Claude", "Hour": 14}'
# Expected: {"success": true, "results": {"Greeting": "Good afternoon, Claude"}}
```

## Workflow

```
1. LOCATE → Find the .dmn file
2. INSPECT → jbang scripts/DmnExecutor.java info <dmn-file>
3. PREPARE → Build JSON with exact input names and correct types
4. EXECUTE → jbang scripts/DmnExecutor.java execute <dmn-file> '<json>'
5. REPORT → Extract results and present in human-readable format
```

## Commands

### info - Inspect a Model

```bash
jbang scripts/DmnExecutor.java info <dmn-file>
```

Shows required inputs (name/type), available decisions, and decision services.

### execute - Run a Model

```bash
jbang scripts/DmnExecutor.java execute <dmn-file> '<json-input>'
```

**Options:**
- `-d, --decision <name>` — Execute only this decision
- `-s, --service <name>` — Execute a Decision Service
- `-m, --model <name>` — Select model by name
- `-i, --import <path>` — Add DMN file or directory
- `--no-auto-import` — Don't auto-load .dmn files from same directory
- `--no-typecheck` — Disable DMN runtime input type checking (more permissive)

### help - Show Help

```bash
jbang scripts/DmnExecutor.java help
```

## Input Format

Input names are **case-sensitive**. Match exactly as shown in `info` output.

| DMN Type | JSON Format | Example |
|----------|-------------|---------|
| number | JSON number | `35`, `75000.50` |
| string | JSON string | `"Gold"`, `"Approved"` |
| boolean | JSON boolean | `true`, `false` |
| date | ISO string | `"2024-01-15"` |
| time | ISO string | `"14:30:00"` |
| date and time | ISO string | `"2024-01-15T14:30:00"` |
| Complex | JSON object | `{"name": "John", "age": 35}` |
| List | JSON array | `[{"item": "A"}, {"item": "B"}]` |

## Output Format

**Success:**
```json
{
  "success": true,
  "decisions": {
    "Decision Name": {"result": "value", "status": "SUCCEEDED"}
  },
  "results": {"Decision Name": "value"}
}
```

**Error:**
```json
{
  "success": false,
  "errors": [{"message": "Required input 'Age' is missing", "type": "REQ_NOT_FOUND"}]
}
```

## Error Handling

| Error | Cause | Solution |
|-------|-------|----------|
| `REQ_NOT_FOUND` | Missing required input | Add the missing input to JSON |
| `FEEL_EVALUATION_ERROR` | Invalid expression | Check input types match expected |
| `DMN file not found` | Wrong path | Verify the DMN file path |
| `Could not find main DMN model` | Wrong model name | Use `info` to see model names |

**Debugging:** Run `info` first, check input names match exactly, verify types, try minimal input.

## FEEL Syntax Notes

**Variable names with spaces ARE allowed** — reference directly: `customer data.address`

**NOT supported:**
- Backticks: `` `customer data` `` ❌
- Single quotes for variables: `'customer data'` ❌

**Context variable ordering** — Variables must be defined before use in context expressions.

**Information requirements** — When a decision references another decision, it must declare an `informationRequirement`.

## Multiple DMN Files

- **Auto-import (default):** All `.dmn` files in same directory loaded automatically
- **Explicit import:** `--import common-types.dmn --import shared/`
- **Disable auto-import:** `--no-auto-import`

## Complete Example

```bash
# 1. Inspect
jbang scripts/DmnExecutor.java info loan-approval.dmn

# 2. Execute
jbang scripts/DmnExecutor.java execute loan-approval.dmn \
  '{"Age": 35, "Income": 75000, "Loan Amount": 200000, "Credit Score": 720}'

# 3. Output
# {"success": true, "results": {"Risk Category": "Medium", "Loan Approval": "Manual Review"}}
```

## Advanced Topics

**See [references/ARCHITECTURE.md](references/ARCHITECTURE.md) for:**
- FEEL expression errors and syntax → [FEEL Engine](references/ARCHITECTURE.md#feel-engine)
- Decision Services usage → [Decision Services](references/ARCHITECTURE.md#decision-services)
- Performance tuning and threading → [Threading and Performance](references/ARCHITECTURE.md#threading-and-performance)
- API integration details → [Key KIE API Classes](references/ARCHITECTURE.md#key-kie-api-classes)
- Error type reference → [Error Handling](references/ARCHITECTURE.md#error-handling)
