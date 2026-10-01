# PesaFlow

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Offline--First-238636?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Status-Active_Development-ff5e6c?style=for-the-badge" />
</p>

> **Personal finance x student life x adaptive intelligence — built for real-world Kenyan student constraints.**

PesaFlow is an Android/Kotlin personal finance and lifestyle assistant designed to do more than record transactions. It connects financial state with the circumstances that give those numbers meaning.

## Why it exists

A student does not experience money as isolated numbers.

Rent, food, transport, university costs, irregular income, funding, bills, debt, distance and personal habits all interact. PesaFlow is being built around that reality.

The core idea is simple:

**financial calculations should be precise; financial guidance should be contextual.**

## Core system

### Finance
- Income and expense tracking
- Budgets
- Bills and recurring obligations
- Debt tracking
- Cash-flow awareness
- Safe-to-spend calculations
- Financial summaries and insights

### Student context
- University and semester planning
- Housing/living situation
- Food and meal planning
- Transport and distance considerations
- Student funding context
- Personal preferences and changing circumstances

### Intelligence
The project is being extended toward a self-contained intelligence layer for:

- personal-context modeling
- intent understanding
- personalization
- behavioural pattern detection
- adaptive recommendations
- financial reasoning
- confidence-aware explanations
- lightweight ML and neural components
- local/offline processing where practical

No external AI API is intended to be a hard dependency of the intelligence architecture.

## Architecture direction

```text
                 PesaFlow Android UI
                         │
                         ▼
                 ┌───────────────┐
                 │ Finance Engine│
                 │ money + rules │
                 └───────┬───────┘
                         │
                         ▼
                 ┌───────────────┐
                 │Personal Context│
                 │ habits + life │
                 └───────┬───────┘
                         │
                         ▼
                 ┌───────────────┐
                 │ Intelligence  │
                 │ intent + ML   │
                 └───────┬───────┘
                         │
                         ▼
                 ┌───────────────┐
                 │Adaptive Assist│
                 │ insight + next│
                 └───────────────┘
```

A key architectural boundary is maintained between **deterministic financial truth** and **probabilistic interpretation**.

Money calculations should remain testable and explainable. Intelligence can then use those trusted results to understand patterns and personalize communication.

## Design principles

**Financial correctness** — calculations should be deterministic and auditable.

**Context before conclusions** — the same amount can mean different things in different student situations.

**Local intelligence where practical** — useful personalization should not automatically require a cloud service.

**Explainable assistance** — the system should communicate why an insight matters rather than exposing opaque scores.

**Visual clarity** — complex financial state should feel understandable, not like a spreadsheet dumped onto a phone.

## Technology

- Kotlin
- Android
- Gradle / Gradle Kotlin DSL
- Local application state and persistence
- Custom finance logic
- ML / neural components under active development

## Current engineering focus

- Unified personal-context model
- Financial reasoning
- Adaptive personalization
- Local ML components
- Intent understanding
- Context-aware recommendations
- Student-specific financial modeling
- Visual design and information hierarchy
- Reliability and testing

## Roadmap

- [x] Android/Kotlin foundation
- [x] Core finance-oriented screens
- [x] Student feature direction
- [ ] Unified personal-context model
- [ ] Adaptive intelligence layer
- [ ] Robust financial reasoning engine
- [ ] Expanded local ML
- [ ] Deeper personalization
- [ ] Production UI refinement
- [ ] Testing and reliability hardening

## Project status

**Active development**

PesaFlow is the flagship project in this portfolio: a practical attempt to combine **software engineering, financial systems, mobile development and applied AI** into one coherent product.

---

**PesaFlow — understand your money in the context of your life.**