# DMN Skills

A collection of skills for executing and working with DMN (Decision Model and Notation) files using the Apache KIE engine.

## What is DMN?

Decision Model and Notation (DMN) is an industry standard for modeling and executing business decisions. It allows you to define complex business rules, decision tables, and decision logic in a standardized XML format that can be executed programmatically.

## Available Skills

### DMN Executor

A lightweight command-line tool to execute DMN 1.5 files using JBang and the Apache KIE engine.

**Location:** `skills/dmn-executor-skill/`

**Key Features:**
- DMN 1.5 with FEEL 1.5 expression language support
- All boxed expression types and decision tables
- Decision Services support
- Auto-import of DMN files from same directory
- JSON-in/JSON-out interface
- Skill composition for domain-specific use cases

**Quick Start:**
```bash
# Install JBang
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Execute a DMN model
cd skills/dmn-executor-skill
jbang DmnExecutor.java execute test-greeting.dmn '{"Name": "World", "Hour": 14}'
```

See [skills/dmn-executor-skill/README.md](skills/dmn-executor-skill/README.md) for complete documentation.

## Use Cases

Use DMN skills when you need to:
- Automate business rule evaluation
- Calculate pricing, eligibility, or risk scores
- Execute decision tables with complex conditions
- Run FEEL expressions
- Create domain-specific decision services

## Requirements

- Java 17 or higher
- JBang (installed via quick start command above)

## Documentation

| Document | Description |
|----------|-------------|
| [DMN Executor README](skills/dmn-executor-skill/README.md) | Quick start and usage guide |
| [DMN Executor SKILL.md](skills/dmn-executor-skill/SKILL.md) | Complete agent/user documentation |
| [ARCHITECTURE.md](skills/dmn-executor-skill/ARCHITECTURE.md) | Technical architecture details |

## Creating Domain-Specific Skills

The DMN Executor can be used as a foundation for creating domain-specific skills (e.g., loan approval, insurance pricing). This pattern allows you to:
- Bundle a specific DMN file with simplified documentation
- Provide domain-specific interfaces
- Hide DMN complexity from end users

See the DMN Executor documentation for examples and templates.

## License

Apache License 2.0
