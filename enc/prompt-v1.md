You are a Principal Enterprise Architect, AI/Agentic Systems Architect, and Financial Services Solution Architect with deep expertise in Transfer Agency (TA), Fund Administration (FA), enterprise integration, workflow automation, AI/LLM systems, security, regulatory controls, and large-scale distributed systems.

Your task is to design a vendor-neutral, production-grade, extensible Multi-Agent System (MAS) and enterprise application architecture for Transfer Agency and Fund Administration.

The resulting system must be designed as a coherent enterprise application, not merely as a collection of prompts or LLM agents.

The architecture must include:

* a modern user interface,
* backend APIs,
* workflow/orchestration services,
* deterministic business services,
* agentic/LLM modules where justified,
* enterprise system integrations,
* relational databases,
* document repositories,
* vector databases,
* event/message infrastructure,
* persistent memory,
* RAG capabilities,
* security and governance,
* monitoring and evaluation,
* human-in-the-loop controls,
* CI/CD and deployment architecture.

The design must explicitly avoid using LLMs where deterministic software, rules, workflow engines, document-processing models, APIs, SQL, or conventional services are more appropriate.

---

# 1. Business Context

A modern AI agent architecture consists of four foundational building blocks:

1. Brain — Foundation Model
2. Instructions — Prompts, policies, rules, and operational guidelines
3. Memory — Context management and persistent state
4. Tools — Actions, systems, APIs, databases, retrieval systems, and external perceptions

A Multi-Agent System consists of multiple specialized autonomous or semi-autonomous components that collaborate to solve business problems.

A conceptual model is:

Environment / User Interface
|
v
Specialized Agents and Business Services
[Brain] [Instructions] [Memory] [Tools]
|
v
Coordination / Orchestration Layer
|
v
Enterprise Systems, Databases, APIs, Documents, Models and Workflows

The orchestration layer must coordinate:

* business decisions,
* workflows,
* agents,
* deterministic services,
* tools,
* enterprise APIs,
* retrieval/RAG,
* evaluation,
* persistent state,
* human approvals,
* retries,
* exception handling,
* observability.

Do not assume that every workflow requires an agent or LLM.

---

# 2. Target Domain

The platform is intended for:

* Transfer Agency
* Fund Administration

The system must support regulated financial-services operations and therefore emphasize:

* auditability,
* explainability,
* deterministic controls,
* data lineage,
* segregation of duties,
* human oversight,
* traceability,
* operational resilience,
* security,
* data privacy,
* controlled use of AI.

---

# 3. Initial Business Capabilities

The initial system must support at least these four major end-user features:

## 3.1 TA Operator Questionnaire Generator

Design a capability that assists Transfer Agency operators in generating, completing, reviewing, and managing operational questionnaires.

Determine from the attached documents:

* questionnaire types,
* input sources,
* business rules,
* templates,
* supporting documents,
* historical responses,
* required approvals,
* operator interaction,
* data retrieval needs,
* downstream workflows,
* audit requirements.

Determine which steps should use:

* deterministic templates,
* rules,
* RAG,
* LLM generation,
* validation services,
* human review.

---

## 3.2 Trade File Processing

Design an end-to-end trade-file-processing capability.

Analyze the attached documentation to determine:

* supported file formats,
* ingestion mechanisms,
* file validation,
* schema validation,
* control totals,
* transformation,
* enrichment,
* data matching,
* business validation,
* duplicate detection,
* reconciliation,
* exception identification,
* downstream submission,
* acknowledgement,
* retry,
* rejection,
* operator review,
* audit trail.

Do not assume an LLM is required.

Prefer deterministic processing for:

* schema validation,
* calculations,
* control totals,
* matching,
* business rules,
* transformations,
* routing,
* reconciliation.

Only introduce LLM/agentic capabilities when there is a clear business justification.

---

## 3.3 KYC Identity Document Extraction and Processing

Design an end-to-end KYC identity document processing capability.

Analyze the supplied documents and determine requirements for:

* document ingestion,
* document classification,
* OCR,
* document AI,
* field extraction,
* validation,
* confidence scoring,
* identity matching,
* entity resolution,
* data normalization,
* completeness checks,
* cross-document validation,
* sanctions/PEP/KYC service integration if applicable,
* exception management,
* human review,
* record retention,
* evidence and audit trails.

Distinguish between:

* OCR,
* document AI,
* deterministic validation,
* external KYC APIs,
* LLM-based reasoning,
* operator review.

Do not use an LLM for high-confidence deterministic extraction or validation unless there is a defensible reason.

---

## 3.4 Invoice Payment Processing

Design an end-to-end invoice processing and payment-support workflow.

Determine requirements for:

* invoice ingestion,
* document classification,
* field extraction,
* supplier validation,
* invoice validation,
* duplicate detection,
* purchase-order matching where applicable,
* business-rule validation,
* payment approval,
* exception handling,
* downstream payment integration,
* reconciliation,
* audit,
* fraud/anomaly checks,
* operator intervention.

Payment execution and materially sensitive financial actions must be designed with appropriate controls and approval boundaries.

---

# 4. Source Document Analysis

Before proposing the architecture, analyze all attached documents thoroughly.

Do not immediately jump to an architecture.

First extract and organize the requirements.

For every document, identify:

* business processes,
* users/personas,
* business rules,
* inputs,
* outputs,
* systems,
* APIs,
* databases,
* documents,
* events,
* validations,
* exceptions,
* approvals,
* manual steps,
* compliance requirements,
* security requirements,
* SLAs,
* volumes,
* performance requirements,
* dependencies,
* ambiguities.

Create a consolidated requirements inventory.

Where requirements conflict between documents:

1. identify the conflict,
2. reference both interpretations,
3. do not silently choose one,
4. ask for clarification if the difference materially affects the architecture.

Where details are missing, clearly distinguish:

* stated requirement,
* inferred requirement,
* architectural recommendation,
* open question.

Do not invent business rules.

---

# 5. Clarification Requirement

If important information is ambiguous, ask targeted clarification questions before making irreversible architectural assumptions.

However, do not ask unnecessary questions where a sensible vendor-neutral architecture can be proposed using clearly stated assumptions.

Prioritize clarification questions concerning:

* regulatory constraints,
* transaction volumes,
* SLA requirements,
* availability requirements,
* data residency,
* document volumes,
* existing enterprise systems,
* integration protocols,
* approval authority,
* workflow ownership,
* existing IAM,
* existing data platforms,
* cloud/on-premise constraints,
* disaster recovery requirements.

For each unanswered question that does not block the design, explicitly state the assumption used.

---

# 6. Fundamental Design Principle: Classify Before Automating

For every functional step in every business workflow, first classify the implementation pattern.

Use categories such as:

1. Deterministic application logic
2. Rules engine / decision table
3. Workflow/BPM orchestration
4. Database operation
5. SQL/data-processing operation
6. API/service invocation
7. Event-driven processing
8. File-processing component
9. OCR/document AI
10. Search/retrieval
11. RAG
12. Machine-learning model
13. LLM reasoning
14. Agentic decision-making
15. Human-in-the-loop activity

Produce a decision matrix containing:

| Business Step | Pattern | LLM Required? | Rationale | Tools/Systems | Human Approval | Failure Handling |

The architecture must minimize unnecessary LLM use.

---

# 7. Human-in-the-Loop Principle

Human-in-the-loop must be introduced whenever an exception, failure, unresolved ambiguity, policy breach, low-confidence outcome, or material operational risk occurs.

Examples include:

* low-confidence document extraction,
* ambiguous KYC identity,
* unsupported document,
* business-rule exception,
* unreconciled trade,
* downstream rejection,
* failed API transaction,
* data mismatch,
* payment exception,
* policy violation,
* unexpected agent output.

The platform must support:

* work queues,
* case management,
* operator assignment,
* review screens,
* supporting evidence,
* reason codes,
* approve/reject/rework actions,
* escalation,
* comments,
* complete audit history,
* resumption of the workflow after operator action.

Do not design human approval as an ad-hoc chat interaction.

Model it as a first-class workflow capability.

---

# 8. Agent Decomposition

Determine the optimal agent decomposition from the business requirements.

Do not mechanically create one agent per feature.

Do not duplicate functionality across agents.

Prefer reusable shared capabilities.

Potential examples include, but are not limited to:

* Orchestration Agent
* Business Process Agent
* Retrieval Agent
* Document Intelligence Agent
* Data Analysis Agent
* Exception Triage Agent
* Operator Assistance Agent
* Validation Agent
* Policy Agent
* Evaluation Agent

Only create an agent where an agent is architecturally justified.

For every proposed agent provide:

* name,
* responsibility,
* reason it exists,
* business scope,
* triggers,
* inputs,
* outputs,
* tools,
* allowed actions,
* prohibited actions,
* model access,
* prompt/instruction strategy,
* short-term context,
* persistent memory,
* RAG access,
* enterprise systems accessed,
* permissions,
* decision authority,
* human escalation rules,
* error handling,
* retry behavior,
* evaluation criteria,
* audit requirements.

Also identify responsibilities that belong in reusable non-agent services.

---

# 9. Orchestration Architecture

Design an orchestration model capable of coordinating:

* agents,
* deterministic business services,
* workflow engines,
* task queues,
* tools,
* APIs,
* event consumers,
* human tasks,
* model calls,
* RAG,
* state transitions.

The orchestration system must support long-running workflows.

Do not rely on LLM context to maintain workflow state.

Workflow state must be durable.

The design should address:

* state machines,
* workflow identifiers,
* correlation IDs,
* task states,
* checkpointing,
* pause/resume,
* timeouts,
* retries,
* exponential backoff,
* circuit breakers,
* dead-letter queues,
* compensating transactions,
* idempotency,
* duplicate prevention,
* recovery,
* replay,
* manual intervention.

Explain whether orchestration should be:

* centralized,
* hierarchical,
* event-driven,
* hybrid,

and justify the choice.

---

# 10. Extensibility and New Feature Design

The architecture must make adding new business capabilities straightforward.

A new feature should be addable without redesigning the platform.

Define a standard feature/module pattern.

For example, a new capability should plug into reusable platform services such as:

* orchestration,
* workflow,
* UI shell,
* authentication,
* authorization,
* API gateway,
* agent runtime,
* tool registry,
* document service,
* relational database service,
* vector retrieval,
* rules engine,
* event bus,
* exception management,
* case management,
* audit,
* observability,
* evaluation,
* model gateway,
* configuration,
* secrets management.

Define a recommended structure for a new feature module such as:

Feature

* UI module
* API contract
* workflow definition
* business services
* domain rules
* optional agent
* tools
* event definitions
* persistence
* retrieval configuration
* security policy
* exception rules
* evaluation suite
* monitoring
* tests
* deployment configuration

Explain how new capabilities can be introduced with minimal impact to existing ones.

---

# 11. Tool Architecture

Design a reusable Tool Layer or Tool Registry.

Possible tool classes include:

* relational database tools,
* document repository tools,
* vector database tools,
* search tools,
* file tools,
* enterprise APIs,
* KYC providers,
* payment systems,
* TA platforms,
* FA platforms,
* market-data systems,
* messaging systems,
* OCR/document AI,
* model APIs,
* rules engines,
* notification services,
* identity systems.

Every tool must have:

* explicit schema,
* versioning,
* authentication,
* authorization,
* input validation,
* output validation,
* timeout policy,
* retry policy,
* observability,
* audit,
* error contract.

Agents must not directly receive unrestricted enterprise-system credentials.

Use controlled service identities and least privilege.

---

# 12. Model Architecture

The solution must remain vendor-neutral.

Assume an internally hosted or internally managed enterprise foundation model will be available.

Create a Model Gateway abstraction.

It should support:

* multiple internal models,
* capability-based routing,
* versioning,
* model upgrades,
* fallback,
* timeout handling,
* token/context management,
* structured output,
* model-policy enforcement,
* evaluation,
* observability,
* cost or compute metering,
* future model replacement.

Avoid embedding vendor-specific APIs into domain components.

---

# 13. Prompt and Instruction Architecture

Define how agent instructions are managed.

Address:

* system prompts,
* agent role prompts,
* task prompts,
* business policies,
* reusable prompt fragments,
* dynamic context,
* retrieved context,
* tool descriptions,
* output schemas.

Prompts must be version controlled.

Describe:

* prompt lifecycle,
* testing,
* approval,
* deployment,
* rollback,
* evaluation.

Business rules that should be deterministic must not be hidden inside prompts.

---

# 14. Memory Architecture

Do not treat all stored information as "agent memory."

Explicitly separate:

## A. Workflow State

Persistent process state.

## B. Session Context

Temporary conversation/task context.

## C. Domain Data

Authoritative business records.

## D. Document Store

Original business documents and generated documents.

## E. Retrieval Knowledge

Indexed/chunked content for semantic search.

## F. Agent Experience / Evaluation Data

Historical outcomes, feedback, scores and traces.

## G. Audit History

Immutable or controlled operational evidence.

For each memory category specify:

* purpose,
* datastore type,
* retention,
* lifecycle,
* access controls,
* consistency requirements,
* whether it can be supplied to an LLM.

Persistent memory must not automatically mean that historical interactions are added to future prompts.

---

# 15. RAG Architecture

Design a production-grade RAG subsystem.

Cover:

* document ingestion,
* parsing,
* classification,
* OCR,
* normalization,
* chunking,
* metadata extraction,
* embeddings,
* indexing,
* access-control-aware retrieval,
* hybrid retrieval,
* semantic retrieval,
* keyword retrieval,
* reranking,
* filtering,
* context assembly,
* citations,
* provenance,
* document lineage,
* freshness,
* re-indexing,
* deletion,
* versioning.

RAG must honor the user's authorization context.

A user or agent must never retrieve information it is not authorized to access.

---

# 16. Data Architecture

Define the major data stores and their responsibilities.

Consider:

* operational relational database,
* workflow database,
* document/object store,
* vector database,
* cache,
* event store where justified,
* audit store,
* analytics/telemetry store,
* configuration store.

Produce a conceptual data model covering major entities such as:

* User
* Role
* Operator
* Case
* Workflow
* WorkflowTask
* Document
* DocumentVersion
* Questionnaire
* QuestionnaireResponse
* TradeFile
* TradeRecord
* ValidationResult
* KYCCase
* IdentityDocument
* ExtractedField
* Invoice
* PaymentInstruction
* Exception
* Approval
* AgentExecution
* ToolInvocation
* RetrievalResult
* EvaluationResult
* AuditEvent.

Determine the actual entities from the supplied business documents.

Include entity relationships and data ownership.

---

# 17. API Architecture

Design backend APIs that encapsulate agentic and deterministic functionality.

Do not expose agent internals directly to the UI.

Provide API groupings such as:

* feature APIs,
* workflow APIs,
* task/case APIs,
* document APIs,
* questionnaire APIs,
* trade-processing APIs,
* KYC APIs,
* invoice APIs,
* exception APIs,
* approval APIs,
* configuration APIs,
* administrative APIs.

For representative APIs specify:

* method,
* endpoint,
* request,
* response,
* authorization,
* idempotency requirements,
* synchronous/asynchronous behavior,
* error responses.

Also identify APIs that should be internal rather than externally accessible.

---

# 18. Event Architecture

Determine whether the architecture requires an event bus/message broker.

If appropriate, identify major domain events, for example:

* DocumentUploaded
* DocumentClassified
* ExtractionCompleted
* ExtractionFailed
* QuestionnaireGenerated
* TradeFileReceived
* TradeValidationFailed
* TradeProcessingCompleted
* KYCExceptionRaised
* InvoiceValidated
* PaymentApprovalRequired
* WorkflowFailed
* HumanReviewCompleted.

Define:

* publisher,
* consumer,
* delivery expectations,
* idempotency,
* retry,
* dead-letter behavior.

Avoid event-driven architecture where synchronous APIs are simpler.

---

# 19. UI Architecture

Design a coherent application experience.

Potential UI capabilities include:

* role-based landing page,
* dashboard,
* workflow status,
* task inbox,
* exception queue,
* questionnaire workspace,
* trade processing monitor,
* KYC review workspace,
* invoice review/payment workspace,
* document viewer,
* AI-assisted operator panel,
* audit timeline,
* search,
* notifications,
* administration.

AI should be embedded into workflows rather than forcing all functionality into a chatbot.

Where conversational interaction is valuable, identify it explicitly.

Where forms, tables, workflow screens, document viewers, dashboards or queues are superior, use those instead.

---

# 20. Security Architecture

Provide a detailed enterprise security design.

Address:

* SSO,
* MFA,
* OAuth/OIDC,
* RBAC,
* ABAC where applicable,
* service identities,
* least privilege,
* segregation of duties,
* tenant/data segregation where applicable,
* API authentication,
* authorization,
* encryption at rest,
* encryption in transit,
* secrets management,
* key management,
* document access control,
* vector-store access control,
* PII handling,
* financial data,
* logging policies,
* data masking,
* retention,
* deletion,
* privileged administration.

For AI specifically address:

* prompt injection,
* malicious documents,
* tool abuse,
* excessive agency,
* hallucination,
* unauthorized retrieval,
* data leakage,
* model output validation,
* unsafe tool chaining,
* poisoned retrieval content,
* instruction hierarchy,
* sandboxing where applicable.

---

# 21. Governance and Audit

Design complete traceability.

The system should be able to determine:

* what happened,
* when,
* who initiated it,
* which workflow executed,
* which agent participated,
* which model/version was used,
* which prompt/version was used,
* what context was retrieved,
* which tools were called,
* what business rules ran,
* what decision was made,
* whether a human approved it,
* what data changed.

Separate operational logging from compliance/audit evidence.

---

# 22. Evaluation Architecture

Create a multi-layer evaluation framework.

Evaluate:

## LLM quality

* correctness,
* faithfulness,
* groundedness,
* hallucination,
* completeness,
* instruction adherence,
* structured-output validity.

## Retrieval

* precision,
* recall,
* context relevance,
* ranking quality,
* citation accuracy.

## Agent operation

* correct tool selection,
* unnecessary tool calls,
* task completion,
* failure recovery,
* escalation accuracy.

## Business quality

* questionnaire quality,
* trade-processing accuracy,
* extraction accuracy,
* KYC review accuracy,
* invoice-processing accuracy,
* exception rate,
* straight-through-processing rate,
* operator productivity,
* turnaround time.

## Operational metrics

* latency,
* throughput,
* retries,
* failures,
* queue depth,
* availability,
* cost/compute utilization.

Describe:

* offline evaluation,
* regression suites,
* golden datasets,
* synthetic test cases,
* production monitoring,
* human feedback,
* controlled experimentation.

Do not propose automatic model fine-tuning from raw production feedback.

Explain the governance process before prompts, models, rules or retrieval strategies are changed.

---

# 23. Observability

Define observability across:

* UI,
* API,
* workflow,
* agent,
* model,
* retrieval,
* database,
* events,
* enterprise integrations.

Use correlation IDs across the entire execution chain.

Include:

* logs,
* metrics,
* traces,
* AI traces,
* workflow timelines,
* dashboards,
* alerts,
* SLO monitoring.

Sensitive data must be excluded or masked in telemetry.

---

# 24. Failure and Recovery Architecture

Create a structured failure model.

Classify failures such as:

* business validation failure,
* user input failure,
* document-processing failure,
* LLM failure,
* malformed LLM output,
* retrieval failure,
* API timeout,
* downstream outage,
* workflow failure,
* database failure,
* security denial,
* unexpected exception.

For each class define:

* retry?
* fallback?
* compensation?
* human intervention?
* alert?
* audit?
* workflow state?

The system must degrade safely.

---

# 25. Non-Functional Requirements

Produce recommended NFRs and clearly mark assumptions where exact values are unavailable.

Include:

* availability,
* scalability,
* throughput,
* performance,
* concurrency,
* resiliency,
* recovery objectives,
* durability,
* security,
* auditability,
* maintainability,
* extensibility,
* operability,
* testability,
* accessibility,
* interoperability,
* observability,
* model latency,
* retrieval latency.

Where possible give measurable targets.

---

# 26. Deployment Topology

Produce a logical deployment topology.

Remain vendor-neutral.

Consider:

* web application,
* API gateway,
* backend-for-frontend,
* feature services,
* workflow runtime,
* agent runtime,
* tool gateway,
* model gateway,
* retrieval services,
* relational databases,
* vector database,
* document/object store,
* event bus,
* cache,
* secrets platform,
* IAM,
* monitoring stack.

Discuss:

* containerization,
* orchestration,
* scaling,
* networking,
* network segmentation,
* private endpoints,
* ingress/egress,
* HA,
* DR,
* environment separation.

Cover:

* development,
* test,
* QA,
* staging,
* production.

---

# 27. CI/CD and AI Engineering

Define CI/CD for both conventional software and AI artifacts.

Include:

## Software

* source control,
* build,
* unit tests,
* integration tests,
* security scans,
* container scans,
* deployment,
* rollback.

## AI artifacts

* prompts,
* agent configuration,
* model configuration,
* retrieval configuration,
* evaluation datasets,
* evaluation thresholds.

Require automated evaluation gates before AI changes are promoted.

---

# 28. Testing Strategy

Create a testing pyramid covering:

* unit tests,
* rule tests,
* contract tests,
* API tests,
* integration tests,
* workflow tests,
* document-processing tests,
* agent tests,
* RAG tests,
* security tests,
* performance tests,
* resilience tests,
* UAT,
* regression tests.

For nondeterministic model output, define appropriate tolerance-based and evaluation-based testing.

---

# 29. Implementation Roadmap

Create a realistic phased implementation roadmap.

Suggested phases:

## Phase 0 — Discovery and Architecture

* document analysis,
* requirements,
* architecture,
* integration discovery,
* security architecture,
* domain modelling,
* proof-of-concept decisions.

## Phase 1 — Shared Platform Foundation

* UI shell,
* IAM,
* APIs,
* workflow,
* database,
* documents,
* vector retrieval,
* model gateway,
* agent runtime,
* tool layer,
* observability,
* exception management.

## Phase 2 — First Business Capability

Choose the capability that gives the best balance of business value and architectural validation.

Explain the choice.

## Phase 3 — Additional Capabilities

Introduce the remaining initial use cases.

## Phase 4 — Production Hardening

* scale,
* resilience,
* security,
* DR,
* operational readiness,
* compliance,
* performance.

## Phase 5 — Platform Expansion

Use the established patterns to onboard additional TA/FA capabilities.

Identify dependencies and critical path.

---

# 30. Delivery Estimation

Provide an indicative delivery estimate.

Estimate separately:

* architecture/design,
* business analysis,
* UX/UI,
* frontend development,
* backend development,
* workflow development,
* integration development,
* AI/agent development,
* RAG/document work,
* data engineering,
* security,
* QA,
* performance testing,
* platform/DevOps,
* CI/CD,
* deployment,
* UAT,
* production readiness.

Show estimates preferably using:

* person-weeks,
* team size,
* elapsed duration.

Provide:

* optimistic,
* expected,
* conservative ranges.

State assumptions.

Take into account that the delivery team uses modern AI coding assistants such as Claude Code, OpenCode, or equivalent AI-assisted development tools.

Do not assume these tools eliminate:

* architecture,
* requirements analysis,
* integration complexity,
* testing,
* security,
* governance,
* UAT,
* deployment,
* operational readiness.

Explicitly identify which development activities are likely to receive the greatest productivity benefit from AI-assisted coding.

---

# 31. Required Architecture Diagrams

Produce diagrams using Mermaid wherever practical.

At minimum provide:

1. Enterprise Context Diagram
2. Logical Architecture Diagram
3. Component Architecture
4. Multi-Agent / Agent Collaboration Diagram
5. Orchestration Architecture
6. Data Architecture
7. RAG Architecture
8. Security Architecture
9. Deployment Topology
10. Integration Architecture
11. Human-in-the-Loop Flow

For each of the four initial features provide:

12. End-to-End Workflow
13. Sequence Diagram

Add other diagrams where they improve clarity.

---

# 32. Required Tables

At minimum provide:

### A. Functional Requirements Matrix

### B. Agent Catalogue

| Agent | Purpose | Trigger | Inputs | Outputs | Tools | Memory | Authority | HITL |

### C. Service Catalogue

| Service | Responsibility | Agentic? | State | Interfaces |

### D. Tool Catalogue

| Tool | Type | Used By | Access | Security | Failure Handling |

### E. LLM Usage Matrix

| Workflow Step | LLM? | Why/Why Not | Alternative |

### F. Integration Matrix

| System | Purpose | Protocol | Direction | Data | Failure Handling |

### G. Data Store Matrix

| Store | Data | Source of Truth? | Retention | AI Accessible? |

### H. Security Control Matrix

### I. NFR Matrix

### J. Evaluation Matrix

### K. Delivery Estimate

---

# 33. Required Final Output Structure

Produce the final architecture document in this exact broad order:

1. Executive Summary
2. Source Document Analysis
3. Requirements and Assumptions
4. Open Questions
5. Architectural Principles
6. LLM vs Deterministic Decision Framework
7. Proposed Logical Architecture
8. Component Architecture
9. Agent Architecture
10. Orchestration Architecture
11. Tool Architecture
12. Four Business Capability Designs
13. Workflow and Sequence Diagrams
14. API Architecture
15. Data Architecture
16. Memory Architecture
17. RAG Architecture
18. Integration Architecture
19. UI Architecture
20. Human-in-the-Loop Architecture
21. Security Architecture
22. Governance and Audit
23. Evaluation Architecture
24. Observability
25. Failure and Recovery
26. Non-Functional Requirements
27. Deployment Topology
28. CI/CD
29. Testing Strategy
30. Extensibility / New Feature Pattern
31. Phased Implementation Roadmap
32. Delivery Estimation
33. Key Risks and Mitigations
34. Architecture Decisions
35. Outstanding Questions
36. Recommended Next Steps

---

# 34. Architecture Decision Records

For major architectural choices, create concise Architecture Decision Records.

For example:

* centralized vs distributed orchestration,
* workflow engine vs custom state management,
* REST vs event-driven integration,
* relational vs document persistence,
* vector-store strategy,
* RAG approach,
* tool gateway,
* model gateway,
* shared vs specialized agents,
* long-running workflow implementation.

Use:

Decision:
Context:
Options:
Recommendation:
Reason:
Trade-offs:
Risks:

---

# 35. Architecture Quality Rules

The architecture must comply with these rules.

1. Do not use an LLM simply because this is an AI platform.
2. Prefer deterministic processing for deterministic requirements.
3. Agents must not become generic microservices with LLM labels.
4. Avoid overlapping agent responsibilities.
5. Agents must use controlled tools rather than unrestricted system access.
6. Persist business state outside model context.
7. Model interactions must be observable and auditable.
8. Human intervention must be first-class.
9. New capabilities must reuse common platform components.
10. Business-domain components must remain loosely coupled.
11. The architecture must remain vendor-neutral.
12. Internal models must be accessed through an abstraction layer.
13. Security authorization must apply to RAG and tools.
14. Prompt logic must not replace business-rule engines.
15. Model outputs must be validated before causing downstream actions.
16. High-risk financial actions must have explicit authorization controls.
17. Every asynchronous flow must be recoverable and traceable.
18. Architecture diagrams and descriptions must agree with one another.
19. Clearly distinguish current requirements from recommendations.
20. Challenge unnecessary complexity.

---

# 36. Critical Thinking Requirement

Do not simply satisfy the requested phrase "Multi-Agent System."

Challenge whether each proposed agent is necessary.

For each proposed component ask:

* Why is this an agent?
* Could this instead be deterministic software?
* Could this be a reusable platform service?
* Does it duplicate another component?
* Does it need memory?
* Does it need an LLM?
* Does it need autonomy?
* What happens when it fails?
* What authority should it have?

Prefer the simplest architecture that meets business, operational, regulatory, and extensibility requirements.

The final solution should feel like an enterprise TA/FA operations platform enhanced by AI, not an AI demonstration searching for use cases.

Begin by analysing the supplied documents and producing the requirements inventory and ambiguity list before designing the target architecture.
