# DMN Skills

A collection of skills for executing and working with DMN (Decision Model and Notation) files using the Apache KIE engine.

## What is DMN?

Decision Model and Notation (DMN) is an industry standard for modeling and executing business decisions. It allows you to define complex business rules, decision tables, and decision logic in a standardized XML format that can be executed programmatically.

## Installation

### Installing Skills with Claude Code

To install these skills with Claude Code CLI:

```bash
# Install a skill from the directory
claude skills add skills/dmn-executor-skill/

# Or install from a packaged .skill file
claude skills add dmn-executor.skill
```

Verify installation:
```bash
claude skills list
```

### Installing Skills with GitHub Copilot

To install these skills with GitHub Copilot:

```bash
# Add a skill from directory
/skills add skills/dmn-executor-skill/

# Or specify the full path
/skills add /path/to/dmn-skills/skills/dmn-executor-skill/
```

### Manual Installation

You can also manually copy the skill directory to your AI assistant's skills folder:

**Claude Code:**
```bash
cp -r skills/dmn-executor-skill ~/.claude/skills/
```

**GitHub Copilot:**
```bash
cp -r skills/dmn-executor-skill ~/.copilot/skills/
```

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
jbang scripts/DmnExecutor.java execute assets/greeting.dmn '{"Name": "World", "Hour": 14}'
```

See [skills/dmn-executor-skill/SKILL.md](skills/dmn-executor-skill/SKILL.md) for complete documentation.

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
| [SKILL.md](skills/dmn-executor-skill/SKILL.md) | Complete usage guide and workflow |
| [ARCHITECTURE.md](skills/dmn-executor-skill/references/ARCHITECTURE.md) | Technical architecture details |

## Creating Domain-Specific Skills

The DMN Executor can be used as a foundation for creating domain-specific skills (e.g., loan approval, insurance pricing). This pattern allows you to:
- Bundle a specific DMN file with simplified documentation
- Provide domain-specific interfaces
- Hide DMN complexity from end users

See the DMN Executor documentation for examples and templates.
