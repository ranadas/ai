# Early-Stage Software Estimation Handbook

*For estimating projects before requirements are settled, before technical design exists, and before there are epics or stories to size.*

---

## 1. Purpose and scope

This handbook covers the situation where you are asked for an estimate and you have:

- A one-paragraph problem statement, or a slide deck, or a conversation
- No signed-off requirements
- No architecture or technical design
- No backlog

It does **not** cover sprint-level estimation of a groomed backlog. That is a different problem with different tools.

The goal is to produce an estimate that is **honest, defensible, and improvable** — one that gets tighter at defined checkpoints rather than being wrong from day one and quietly treated as a commitment.

---

## 2. Principles

1. **Ranges, not numbers.** A single figure at this stage is a fiction. Report optimistic / likely / pessimistic and keep the spread visible.
2. **Every number traces to an assumption.** If you cannot name the assumption behind a figure, you cannot defend the figure.
3. **Separate effort from duration from cost.** They are related but not interchangeable, and stakeholders conflate them constantly.
4. **Production readiness is a first-class workstream.** In a regulated environment it is not a tail activity; it is 15–25% of the total and the phase most often omitted.
5. **Contingency is split by cause, never a blended percentage.** Each cause shrinks at a different rate and needs to be defended separately.
6. **The estimate is a managed artefact.** It has a version, a confidence level, and a schedule for re-baselining.
7. **Buy information before committing.** A week of architecture spikes narrows the range more than any amount of spreadsheet work.

---

## 3. The process at a glance

| Step | Output | Time budget |
|---|---|---|
| 1. Frame the problem | Scope statement, in/out list | ½ day |
| 2. Capability inventory | Workstreams with T-shirt sizes | 1 day |
| 3. Cost-driver survey | Counts of the things that drive effort | ½ day |
| 4. Three-point sizing | Effort ranges per workstream | 1 day |
| 5. Phase breakdown | Effort by phase, incl. production readiness | ½ day |
| 6. Team shape and duration | Composition, calendar, ramp | ½ day |
| 7. Contingency by cause | Three separate reserves | ½ day |
| 8. Assumptions and exclusions register | Signed list | ongoing |
| 9. Confidence and gates | Stated ± band and re-baseline dates | ½ day |
| 10. Present | One-page summary + appendix | ½ day |

Total: roughly one working week, ideally with an architect and a lead engineer in the room for steps 2–5.

---

## 4. Step-by-step

### Step 1 — Frame the problem

Write a scope statement of no more than half a page. Then produce an explicit **in / out / undecided** list. The "undecided" column is the most important — it becomes the scope-volatility contingency in Step 7.

```
IN:        Client onboarding workflow, document capture, approval routing
OUT:       Reporting dashboard (existing tool), mobile client
UNDECIDED: Integration with legacy CRM; data migration from spreadsheet estate
```

### Step 2 — Capability inventory

Without stories, decompose into **capability areas** (workstreams). Typical set for a backend-heavy system:

| Workstream | Typical contents |
|---|---|
| Core domain / features | The business logic the users actually want |
| Integrations | Each external or upstream system, counted individually |
| Data model and persistence | Entities, migrations, reference data |
| Data migration | Only if there is an existing dataset to bring across |
| Security and access | AuthN/AuthZ, roles, entitlements, secrets |
| Audit, compliance, controls | Audit trail, four-eyes, retention, GDPR handling |
| Infrastructure and environments | Dev/test/UAT/prod, IaC, networking |
| Observability and operations | Logging, metrics, alerting, runbooks |
| Non-functional hardening | Performance, resilience, DR |
| Production readiness | Pen test, change control, access reviews, evidence |

T-shirt size each with a nominal engineer-week band. Calibrate the bands once for your organisation and reuse them:

| Size | Engineer-weeks | Meaning |
|---|---|---|
| S | 1–3 | Well understood, done before, low integration |
| M | 3–8 | Some novelty, one or two unknowns |
| L | 8–16 | Significant novelty or several integrations |
| XL | 16–30 | Poorly understood; should be split or spiked |

Anything XL is a signal to run a spike (Section 8) before estimating further.

### Step 3 — Cost-driver survey

These are countable now and correlate strongly with total effort. Record the count and the confidence in the count.

| Driver | Count | Confidence | Notes |
|---|---|---|---|
| External integrations | | | Each is at least an M on its own |
| Distinct user roles | | | Drives auth complexity and test matrix |
| Core domain entities | | | Rough proxy for data model and API surface |
| Environments required | | | Each beyond three adds infra and release effort |
| Data migration (Y/N, volume) | | | If Y, budget a separate workstream |
| Regulatory / control touchpoints | | | GDPR, audit trail, segregation of duties, retention |
| Third-party dependencies with SLAs | | | Vendors, upstream teams — schedule risk, not just effort |
| Concurrent users / throughput target | | | Determines whether hardening is S or L |

### Step 4 — Three-point sizing

For each workstream, record **Optimistic (O), Most Likely (M), Pessimistic (P)** in engineer-weeks. Roll up with PERT:

```
Expected  E = (O + 4M + P) / 6
Std dev   σ = (P − O) / 6
```

Sum the E values for the total expected effort. Sum the variances (σ²) and take the square root for the total standard deviation — do not simply add the σ values, that overstates it.

Report the total as `E ± 2σ` for a ~95% band.

**Pre-design rule of thumb:** if your P/O ratio is below 2, you are probably being optimistic about how much you know. Pre-design ranges of 0.5x–2x around the likely figure are normal and honest.

### Step 5 — Phase breakdown

Estimate phases separately because they have different uncertainty characteristics.

| Phase | Nature of estimate | Typical share of total |
|---|---|---|
| Discovery and requirements | Time-boxed → fixed cost | 5–10% |
| Architecture and technical design | Time-boxed → fixed cost | 5–10% |
| Build | From Step 4 | 35–45% |
| Integration and system test | Proportional to integrations count | 10–15% |
| UAT support | Calendar-driven, low effort, high elapsed | 5% |
| Non-functional hardening | From Step 3 throughput/resilience targets | 5–10% |
| Production readiness | Checklist-driven (Section 6) | 15–25% |
| Hypercare | Fixed: usually 2–4 weeks reduced team | 3–5% |

The shares are for sanity-checking, not for deriving numbers. If your build is 70% of the total, something has been missed.

### Step 6 — Team shape and duration

State the assumed team explicitly. Example:

```
1.0  Tech lead / architect
3.0  Backend engineers
1.0  Frontend engineer
1.0  QA / test automation
0.5  DevOps / platform
0.5  BA / product
```

Then derive duration:

```
Duration (weeks) = Total effort (engineer-weeks) / Effective team size
Effective team size = Nominal headcount × Utilisation (0.7–0.8)
```

Add a ramp-up allowance: 2–4 weeks at reduced productivity for a new team, longer if onboarding into a regulated estate with access provisioning lead times.

Report **effort, duration, and cost as three separate lines.** A four-person team over twelve months and an eight-person team over six months are not the same project.

### Step 7 — Contingency by cause

| Reserve | Driven by | Typical pre-design value | Shrinks when |
|---|---|---|---|
| Estimation uncertainty | Design maturity | 30–50% | Technical design is complete |
| Scope volatility | "Undecided" list from Step 1 | 15–30% | Requirements signed off |
| Dependency / integration risk | Third parties, upstream teams, access lead times | 10–20% | Contracts and interface specs agreed |

Apply each to the base expected effort separately and show them as separate lines. Never present a single "30% contingency" — it cannot be defended and it will be the first thing cut.

### Step 8 — Assumptions and exclusions register

Maintain this from day one. Every row is a condition under which the estimate is valid.

| # | Assumption | Impact if false | Owner | Status |
|---|---|---|---|---|
| A1 | Greenfield; no data migration | +1 workstream (M–L) | | Open |
| A2 | Existing SSO reused for authentication | +S–M security work | | Confirmed |
| A3 | Two external integrations, both with documented APIs | +M per additional integration | | Open |
| A4 | Non-prod environments provisioned within 2 weeks of request | Schedule slip 1:1 with delay | | Open |
| A5 | Pen test slot available in month N | Go-live slip | | Open |

Exclusions are stated separately and equally explicitly — "reporting dashboard is out of scope" prevents the most common source of later dispute.

### Step 9 — Confidence and gates

State the confidence band and commit to re-baselining at gates:

| Gate | Trigger | Target confidence |
|---|---|---|
| G0 — Initial | This estimate | ±50% |
| G1 — Requirements sign-off | Scope frozen, undecided list resolved | ±25% |
| G2 — Technical design complete | Architecture, integrations, data model agreed | ±10–15% |
| G3 — First increment delivered | Real velocity data | ±10% |

Each gate produces a new version of the estimate document. Keep the history — the trajectory of the range is itself useful evidence for future projects.

### Step 10 — Present

Lead with a **one-page summary**:

1. Scope statement (three lines)
2. Expected effort with range: `E engineer-weeks (range O–P)`
3. Duration and cost, with team shape
4. Contingency, three lines
5. Confidence and next re-baseline date
6. Top five assumptions and what happens if they break
7. What would reduce the range (usually: fund a design spike)

Everything else goes in an appendix. If the audience only reads one page, that page must contain the range and the assumptions, not just the number.

---

## 5. Templates

### 5.1 Estimate summary sheet

```
Project:            ____________________
Version / date:     v0.1  ____________
Gate:               G0 — Initial
Confidence:         ±50%
Next re-baseline:   ____________ (trigger: ____________)

Scope (in):         ____________________________________
Scope (out):        ____________________________________
Undecided:          ____________________________________

Base effort (E):    ____ eng-weeks   (O ____ / M ____ / P ____)
Contingency:
  Estimation        ____ %   ____ eng-weeks
  Scope volatility  ____ %   ____ eng-weeks
  Dependency risk   ____ %   ____ eng-weeks
Total effort:       ____ eng-weeks

Team:               ____ FTE (composition below)
Effective size:     ____ (utilisation ____)
Duration:           ____ weeks + ____ ramp
Cost:               ____ (rate card ref: ____)

Top assumptions:    A1, A2, A3 (see register)
Range reducers:     ____________________________________
```

### 5.2 Workstream sizing table

| Workstream | Size | O | M | P | E | σ | Assumptions |
|---|---|---|---|---|---|---|---|
| | | | | | | | |
| **Total** | | | | | **Σ E** | **√Σσ²** | |

### 5.3 Production readiness checklist (regulated environment)

Use this to size the production-readiness workstream. Tick what applies; each ticked line is at minimum a few days, several are weeks.

- [ ] Penetration test scheduled and remediation window budgeted
- [ ] Security architecture review / threat model signed off
- [ ] Change advisory board submission and lead time
- [ ] Access control review and joiner/mover/leaver process
- [ ] Audit trail verified against control requirements
- [ ] GDPR / data protection impact assessment
- [ ] Data retention and deletion implemented and evidenced
- [ ] Secrets management and key rotation in place
- [ ] Backup and restore tested
- [ ] DR / failover tested
- [ ] Monitoring, alerting, and on-call rota agreed
- [ ] Runbooks and operational handover
- [ ] Performance test against stated targets
- [ ] Vendor / third-party due diligence complete
- [ ] Business continuity sign-off
- [ ] Go / no-go criteria and rollback plan

---

## 6. Worked example (illustrative)

**Scenario:** Internal workflow system for approving and tracking client documents. Two integrations (document store, notification service), four user roles, modest volume, existing SSO, regulated environment.

**Step 2–4 sizing (engineer-weeks):**

| Workstream | Size | O | M | P | E | σ |
|---|---|---|---|---|---|---|
| Core workflow and approvals | L | 8 | 12 | 20 | 12.7 | 2.0 |
| Integrations (×2) | M+M | 6 | 10 | 18 | 10.7 | 2.0 |
| Data model and persistence | M | 3 | 5 | 8 | 5.2 | 0.8 |
| Security and access (SSO reused) | S | 1 | 2 | 4 | 2.2 | 0.5 |
| Audit, controls, GDPR | M | 4 | 6 | 10 | 6.3 | 1.0 |
| Infra and environments | M | 3 | 5 | 9 | 5.3 | 1.0 |
| Observability and ops | S | 2 | 3 | 5 | 3.2 | 0.5 |
| NFR hardening | S | 2 | 3 | 6 | 3.3 | 0.7 |
| Production readiness | L | 8 | 12 | 18 | 12.3 | 1.7 |
| **Total** | | 37 | 58 | 98 | **61.2** | **√Σσ² ≈ 4.2** |

Note the summed σ would be 10.2; the root-sum-square is 4.2. Both are shown because the pre-design honest band should still be reported as the O–P spread (37–98), not the statistical ±2σ (53–70) — the PERT variance assumes independent, well-characterised workstreams, which is not true before design.

**Contingency:** estimation 40% (24.5), scope volatility 20% (12.2 — CRM integration undecided), dependency 10% (6.1). Total with contingency ≈ 104 engineer-weeks.

**Team:** 4.5 FTE nominal, 0.75 utilisation → 3.4 effective. Duration ≈ 104 / 3.4 ≈ 31 weeks + 3 ramp ≈ **34 weeks**, presented as a 26–44 week range.

**Headline:** "Roughly 60 engineer-weeks base effort, 100 with contingency, 7–11 months elapsed with a team of 4–5. Confidence ±50%. Funding a two-week design spike would bring this to ±25% before commitment."

---

## 7. Anti-patterns

| Anti-pattern | Why it hurts | Instead |
|---|---|---|
| Giving a single number "because that's what they asked for" | It becomes the commitment; the range is forgotten | Give the range on page one, the number in the appendix |
| Blended contingency percentage | Indefensible, first thing cut | Three named reserves |
| Estimating build only | Misses 40–60% of total | Phase breakdown with production readiness |
| Silent assumptions | Estimate is "wrong" when they change | Register with owners and impact |
| Confusing effort with duration | Team size and calendar get negotiated independently | Three separate lines |
| Precision theatre (e.g. "247.5 days") | Implies knowledge you don't have | Round to the confidence level |
| No re-baseline plan | Initial guess lives forever | Gates with target confidence |
| Skipping the spike to "save time" | Spends the saved week many times over | Fund one to two weeks of discovery |

---

## 8. Design spikes: buying down uncertainty

When any workstream is XL, or the O–P ratio exceeds 3, propose a time-boxed spike (1–2 weeks) producing:

- System context diagram
- Integration inventory with interface maturity assessment (documented API / undocumented / doesn't exist yet)
- Core entity sketch
- Environment and access lead-time confirmation
- Identified regulatory touchpoints

Frame it to stakeholders as a cost-of-information decision: "Two weeks now to move from ±50% to ±25% on a project we currently think is 7–11 months."

---

## 9. Re-baselining checklist

At each gate:

1. Re-run Steps 3–4 with the new information
2. Resolve or reclassify every "undecided" scope item
3. Close or re-open assumptions in the register
4. Recompute each contingency reserve against its trigger
5. Issue a new version; keep the old one
6. Record the delta and the reason — this is the calibration data for the next project

---

## 10. Quick reference card

```
PERT      E = (O + 4M + P) / 6      σ = (P − O) / 6      Total σ = √Σσ²
SIZES     S 1–3   M 3–8   L 8–16   XL 16–30 (spike it)
PHASES    Disc 5–10  Design 5–10  Build 35–45  Test 10–15
          UAT 5  NFR 5–10  Prod-ready 15–25  Hypercare 3–5
RESERVES  Estimation 30–50%  Scope 15–30%  Dependency 10–20%
GATES     G0 ±50%  G1 ±25%  G2 ±10–15%  G3 ±10%
DURATION  effort / (headcount × 0.7–0.8) + ramp
RULE      Every number → an assumption → an owner
```
