# DMN Executor Architecture

Technical architecture of the DMN Executor skill and Apache KIE DMN engine integration.

## Table of Contents

1. [Overview](#overview)
2. [Component Architecture](#component-architecture)
3. [Data Flow](#data-flow)
4. [Key KIE API Classes](#key-kie-api-classes)
5. [DMN Processing Pipeline](#dmn-processing-pipeline)
6. [FEEL Engine](#feel-engine)
7. [Decision Services](#decision-services)
8. [Error Handling](#error-handling)
9. [Threading and Performance](#threading-and-performance)
10. [Dependencies](#dependencies)

---

## Overview

The DMN Executor is a lightweight CLI wrapper around the Apache KIE DMN runtime providing JSON-in/JSON-out interface for executing DMN decision models.

**Technical Specifications:**
- **Engine:** Apache KIE 10.1.0 (Kogito/Drools)
- **DMN Version:** 1.5 (backward compatible with 1.1-1.4)
- **Expression Language:** FEEL 1.5

```
┌─────────────────────────────────────┐
│         DMN Executor                │
│  CLI → Executor Logic → JSON Output │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│     Apache KIE DMN Engine           │
│  Parser → Compiler → Runtime → FEEL │
└─────────────────────────────────────┘
```

## Component Architecture

### CLI Commands

| Command | Purpose |
|---------|---------|
| `execute` | Run all or specific decisions |
| `service` | Run a Decision Service |
| `info` | Show model metadata |
| `help` | Show usage |

### KIE DMN Modules

- **kie-dmn-core** — DMN parsing, compilation, runtime
- **kie-dmn-feel** — FEEL expression language
- **kie-dmn-model** — DMN metamodel
- **kie-dmn-api** — Public interfaces

## Data Flow

```
JSON Input → Parse to Map<String,Object>
    ↓
DMN Files → Load & compile models
    ↓
KIE Engine → Evaluate decisions with FEEL
    ↓
JSON Output → Serialize results
```

### Execution Sequence

1. User invokes execute command
2. DmnExecutor loads DMN files via `DMNRuntimeBuilder.fromResources()`
3. KIE parses XML, validates model, compiles decisions
4. DmnExecutor creates DMNContext with input JSON
5. KIE evaluates FEEL expressions
6. DmnExecutor returns JSON output

## Key KIE API Classes

| Class | Purpose |
|-------|---------|
| `DMNRuntimeBuilder` | Creates DMNRuntime from DMN files |
| `DMNRuntime` | Main runtime - evaluates models |
| `DMNModel` | Represents a DMN model with inputs/decisions |
| `DMNContext` | Holds input values and results |
| `DMNResult` | Contains decision results and messages |
| `DMNDecisionResult` | Single decision result with status |

### Key Methods

```java
// Build runtime
DMNRuntime runtime = DMNRuntimeBuilder.fromDefaults()
    .buildConfiguration()
    .fromResources(dmnFiles);

// Get model
DMNModel model = runtime.getModels().get(0);

// Create context and set inputs
DMNContext ctx = runtime.newContext();
ctx.set("Age", 35);

// Evaluate
DMNResult result = runtime.evaluateAll(model, ctx);

// Get results
result.getDecisionResults().forEach(dr -> {
    String name = dr.getDecisionName();
    Object value = dr.getResult();
    Status status = dr.getEvaluationStatus();
});
```

## DMN Processing Pipeline

### 1. Parsing Phase

DMN XML → JAXB Parser → Model Objects (Definitions, Decisions, InputData, ItemDefinitions, BKMs, DecisionServices)

### 2. Compilation Phase

1. Resolve imports (namespace matching)
2. Validate model structure
3. Build dependency graph
4. Compile FEEL expressions
5. Generate evaluation plan

### 3. Evaluation Phase

1. Topological sort of decisions (dependency order)
2. For each decision:
   - Gather required inputs from context
   - Evaluate expression by type
   - Store result in context
3. Return DMNResult

### Expression Types

| Type | Description |
|------|-------------|
| Literal Expression | FEEL expression |
| Decision Table | Rule-based with hit policies |
| Context | Named entries |
| List | Collection |
| Relation | Table data |
| Invocation | BKM call |
| Conditional (1.5) | if-then-else |
| Filter (1.5) | list[predicate] |
| For (1.5) | iteration |
| Some/Every (1.5) | quantified expressions |

## FEEL Engine

```
FEEL Expression → Lexer (ANTLR) → Parser → AST → Compiler → Interpreter → Result
```

### Built-in Function Categories

- **String:** substring, upper/lower case, contains, matches, replace
- **Numeric:** decimal, floor, ceiling, abs, modulo, sqrt
- **Boolean:** not, all, any
- **Date/Time:** date, time, now, today, duration
- **List:** list contains, count, sum, mean, min/max, flatten, distinct values
- **Context:** get value, get entries, context, context merge
- **Range:** before, after, meets, overlaps

## Decision Services

Decision Services encapsulate a set of decisions, exposing only output decisions while hiding internal logic.

```
┌──────────────────────────────────────┐
│  Decision Service: Quote Service     │
├──────────────────────────────────────┤
│  Inputs: Applicant, Vehicle, Type    │
├──────────────────────────────────────┤
│  Encapsulated (Hidden):              │
│    Driver Risk, Vehicle Risk,        │
│    Base Premium, Risk Multiplier     │
├──────────────────────────────────────┤
│  Outputs (Exposed):                  │
│    Final Premium, Eligibility        │
└──────────────────────────────────────┘
```

Invoke with `--service "Service Name"` — only output decisions returned.

## Error Handling

### Error Categories

**Compilation Errors:**
- Invalid DMN structure
- Unresolved imports
- Invalid FEEL syntax
- Type mismatches

**Evaluation Errors:**
- Missing required inputs (`REQ_NOT_FOUND`)
- FEEL runtime errors
- Type coercion failures
- Decision table no-match

All errors produce `DMNMessage` with severity (ERROR/WARN/INFO), type, text, and source location.

## Threading and Performance

### Thread Safety

| Component | Thread-Safe? | Usage |
|-----------|--------------|-------|
| DMNRuntime | ✅ Yes | Share across threads |
| DMNModel | ✅ Yes | Read-only access |
| DMNResult | ✅ Yes | Safe to pass |
| DMNContext | ❌ No | Create new per evaluation |

### Performance Notes

- **First run:** ~30s (downloading dependencies)
- **Subsequent runs:** ~2s (cached)
- **Compilation:** Once per runtime creation — cache runtime for repeated evals
- **FEEL:** Parsed at compile time, fast evaluation at runtime
- **Memory:** Large models may need `-Xmx` for more heap

## Dependencies

### Direct Dependencies

```
org.kie:kie-dmn-core:10.1.0
org.kie:kie-dmn-feel:10.1.0
com.fasterxml.jackson.core:jackson-databind:2.17.0
org.slf4j:slf4j-simple:2.0.9
```

### Key Transitive Dependencies

- kie-dmn-api, kie-dmn-model, kie-api
- drools-util
- antlr4-runtime
- jackson-core, jackson-annotations
- slf4j-api

## Decision Table Hit Policies

### Single Hit

| Policy | Behavior |
|--------|----------|
| U (Unique) | Only one rule can match |
| A (Any) | Multiple rules, same output |
| P (Priority) | First by output priority |
| F (First) | First matching rule |

### Multiple Hit

| Policy | Behavior |
|--------|----------|
| C (Collect) | All matching outputs |
| C+ | Sum of outputs |
| C< | Min of outputs |
| C> | Max of outputs |
| C# | Count of matches |
| R (Rule Order) | All in rule order |
| O (Output Order) | All in output order |

## References

- [DMN 1.5 Specification](https://www.omg.org/spec/DMN/1.5/)
- [Apache KIE Documentation](https://docs.kie.org/)
- [FEEL Specification](https://www.omg.org/spec/DMN/1.5/PDF) (Chapter 10)
- [JBang Documentation](https://www.jbang.dev/documentation/guide/latest/)
