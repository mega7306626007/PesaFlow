# PesaFlow

> **A personal finance and lifestyle assistant for students — built around KES, real student constraints, and adaptive intelligence.**

PesaFlow is an Android application being developed in Kotlin to make personal finance easier to understand and act on. The project goes beyond a conventional expense tracker: it is designed to combine financial data, user context, budgeting logic, and on-device intelligence into a single student-focused experience.

## Why PesaFlow?

Most finance apps show numbers. PesaFlow is being designed to help a student understand **what those numbers mean for their actual life**.

That means accounting for context such as:

- income and irregular cash flow
- rent or living with family
- HELB or other student funding
- recurring bills and debts
- food and university-related spending
- transport and distance
- budgets and safe-to-spend amounts
- personal preferences and habits
- changing circumstances over time

The goal is not to overwhelm the user with financial figures. The system should translate financial state into clear, contextual guidance.

## Core Product

### Finance
- Expense and income tracking
- Budget planning
- Bills and recurring obligations
- Debt tracking
- Semester-aware financial planning
- Safe-to-spend calculations
- Financial summaries and insights

### Student Context
- University-oriented planning
- Meal and food planning
- Semester context
- Housing/living situation
- Transport and distance considerations
- Personal financial circumstances

### Intelligence Layer
PesaFlow is being extended toward a self-contained intelligence architecture with:

- user-context modeling
- personalization
- intent understanding
- behavioral patterns
- adaptive recommendations
- financial reasoning
- confidence-aware insights
- lightweight ML and neural components
- local/offline-first processing where practical

The architecture is intentionally being developed without making a cloud API or external AI service a hard dependency.

## Architecture Direction

```text
                    ┌─────────────────────┐
                    │     PesaFlow UI     │
                    │  Android / Kotlin   │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Finance Engine    │
                    │ budgets • bills •   │
                    │ cash flow • rules   │
                    └──────────┬──────────┘
                               │
              ┌────────────────▼────────────────┐
              │       Personal Context          │
              │ habits • preferences • student │
              │ situation • lifestyle signals  │
              └────────────────┬────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │ Intelligence Layer │
                    │ intent • ML •       │
                    │ prediction •        │
                    │ personalization     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │ Adaptive Assistant  │
                    │ explanations •      │
                    │ insights • actions  │
                    └─────────────────────┘
```

The long-term architecture is intended to keep **financial truth separate from interpretation**: deterministic calculations handle money and accounting, while ML/intelligence components help understand patterns and personalize explanations.

## Design Philosophy

PesaFlow is being designed around four principles:

**1. Financial correctness**  
Money calculations should remain deterministic, testable, and explainable.

**2. Context before conclusions**  
A number should be interpreted in relation to the user's actual circumstances rather than treated as universally meaningful.

**3. Personalization without unnecessary cloud dependency**  
The application should learn useful preferences and patterns locally where feasible.

**4. Intelligence that explains itself**  
Recommendations should be understandable rather than presenting unexplained scores or predictions.

## Technology

- **Kotlin**
- **Android**
- **Gradle / Gradle Kotlin DSL**
- Local application data and state
- ML/AI components under active development

## Project Status

🚧 **Active development**

The repository is evolving quickly. Some parts of the application are experimental while the architecture is being consolidated around a more robust financial reasoning and personalization system.

## Engineering Focus

Current development is centered on:

- improving financial reasoning
- connecting financial figures with user context
- building a reusable personalization model
- developing lightweight local intelligence
- improving the visual system and information hierarchy
- making complex financial state understandable to students
- keeping the application practical for real-world Kenyan student use

## Screens & Demo

Screenshots, videos, and architecture visuals will be added as the interface stabilizes.

## Roadmap

- [x] Android/Kotlin foundation
- [x] Core finance-oriented screens
- [x] Student-focused feature direction
- [ ] Unified personal-context model
- [ ] Adaptive intelligence layer
- [ ] More robust financial reasoning
- [ ] Expanded local ML components
- [ ] Deeper personalization
- [ ] Polished production UI
- [ ] Testing and reliability hardening

## Repository Notes

This project is intentionally documented as an engineering project rather than presenting experimental features as finished production capabilities.

More implementation details can be found in [PROJECT-REVIEW.md](PROJECT-REVIEW.md).

---

**PesaFlow — understand your money in the context of your life.**
