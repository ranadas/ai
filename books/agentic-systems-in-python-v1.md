# Developing Agentic Systems in Python

*A six-week, project-driven textbook*

---

## How to use this book

This book is written for working software engineers who already know Python and want to build agents that are **reliable enough to ship**, not just demos that work once. Every chapter follows the same shape:

1. **Concepts** — the minimum theory you need, with the trade-offs stated plainly.
2. **Build** — a single, cumulative codebase (`agentkit/`) that you extend each week.
3. **Exercises** — graded easy → hard. Do at least the first two of each chapter.
4. **Checkpoint** — what your code should be able to do before you move on.

The organising principle of the whole book is a single sentence you should be able to recite by Week 3:

> **An agent is a loop in which a model *chooses* the next action, and deterministic code *executes* it.**

Everything else — tools, graphs, multi-agent systems, evaluation — is engineering around that loop: constraining it, observing it, and stopping it.

### Design stance

The code in this book is opinionated, and the opinions are these:

- **The LLM decides; your code executes.** Model output is *input* to your program, never its runtime. Guards (budgets, validation, authorisation) live in code, not in prompts.
- **Everything the model can touch is typed.** Tool inputs are validated with Pydantic before a line of business logic runs. Hallucinated arguments must be rejected at the boundary.
- **Pin your model identifiers.** Never rely on a library's default model. Defaults drift; your evaluations don't.
- **Observability is a feature, not an afterthought.** Every step of every agent run is recorded from Week 1, because by Week 6 you will need to replay it.
- **Readable beats clever.** Small functions, explicit data types, no magic.

### Course map

| Week | Chapter | You will build |
|---|---|---|
| 1 | Agentic design patterns | A ReAct agent from scratch, plus Plan-and-Execute and Reflexion variants |
| 2 | Tool use and function calling | A typed tool registry with validation, retries, timeouts and failure modes |
| 3 | LangGraph fundamentals | The Week 1 agent rebuilt as a persistent, resumable graph |
| 4 | Multi-agent systems | A supervisor-led research team with hand-offs and human approval |
| 5 *(proposed)* | Memory and context engineering | Short- and long-term memory, summarisation, retrieval as a tool |
| 6 | Evaluation and debugging | An eval harness, trajectory graders, runaway-agent controls, replay debugging |
| — | Capstone | A production-shaped agent with evals, budgets and a trace viewer |

> **Note on Week 5.** The original outline jumps from Week 4 to Week 6. Chapter 5 fills that gap with the topic students most often ask for after building multi-agent systems: *what does the agent remember, and how do we keep context under control?* It is optional; Chapter 6 does not depend on it.

### Prerequisites and setup

You need Python 3.12+, an Anthropic API key, and (optionally) a LangSmith key for Chapter 6.

```bash
# Chapter 0 — project skeleton
uv init agentkit && cd agentkit
uv add anthropic pydantic langgraph langchain-core httpx tenacity rich
uv add --dev pytest pytest-asyncio ruff mypy
```

Create `agentkit/config.py`. **This is the only place a model name appears in the whole codebase.**

```python
# agentkit/config.py
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Settings:
    """Runtime configuration. Immutable; constructed once at process start."""

    # Pin explicitly. Check https://docs.claude.com/en/docs/about-claude/models
    # for current identifiers and update this string deliberately, with a
    # corresponding re-run of the eval suite (Chapter 6).
    model: str = os.environ.get("AGENTKIT_MODEL", "claude-sonnet-4-6")
    max_tokens: int = 2048
    max_steps: int = 12          # hard ceiling on agent loop iterations
    max_cost_usd: float = 0.50   # hard ceiling on spend per run


settings = Settings()
```

Final repository layout (you will build towards this):

```
agentkit/
├── config.py          # Ch.0  settings, pinned model
├── llm.py             # Ch.1  thin client wrapper + usage accounting
├── trace.py           # Ch.1  step recorder
├── react.py           # Ch.1  ReAct loop
├── patterns.py        # Ch.1  Plan-and-Execute, Reflexion, Router
├── tools/
│   ├── core.py        # Ch.2  Tool protocol, registry, decorator
│   ├── errors.py      # Ch.2  failure taxonomy
│   ├── resilience.py  # Ch.2  retries, timeouts, circuit breaker
│   └── builtin.py     # Ch.2  calculator, search, file tools
├── graph/
│   ├── state.py       # Ch.3  typed state + reducers
│   ├── react_graph.py # Ch.3  ReAct as a StateGraph
│   ├── team.py        # Ch.4  supervisor + workers
│   └── hitl.py        # Ch.4  human-in-the-loop interrupts
├── memory/            # Ch.5
├── evals/             # Ch.6
└── tests/
```

---

# Chapter 1 — Agentic Design Patterns

*Week 1. What makes an AI agent? Moving from chains to agents. The ReAct framework and its alternatives.*

## 1.1 From chains to agents

A **chain** is a fixed sequence of steps decided by the programmer:

```
prompt → LLM → parse → prompt → LLM → answer
```

The control flow is static. The model fills in text; it never decides *what happens next*.

An **agent** inverts that. The programmer supplies a set of possible actions and a loop; the model decides which action to take, observes the result, and decides again:

```
        ┌──────────────────────────────┐
        │                              ▼
  user ─┤  LLM: think → choose action  ├─► action is "finish"? ── yes ─► answer
        │            │                 │
        │            ▼                 │
        │  code: execute action        │
        │            │                 │
        │            ▼                 │
        └── observation appended ──────┘
```

That is the whole idea. Three consequences follow, and they shape the rest of the book:

1. **Non-determinism moves into control flow.** A chain that fails, fails the same way every time. An agent can take a different path on every run. This is why Chapter 6 exists.
2. **The action set is the agent's capability boundary.** An agent can only do what its tools allow. Tools are therefore your primary safety and correctness lever (Chapter 2).
3. **The loop needs a terminating condition you control.** "The model said it was done" is one condition; "we spent $0.50" and "we ran 12 steps" are the ones that save you at 3 a.m.

### When *not* to build an agent

If you can enumerate the steps in advance, write a chain (or plain code). Agents earn their complexity only when the path genuinely depends on intermediate results — search-then-refine, debug-then-retry, negotiate-then-decide. A common anti-pattern is using an agent loop for deterministic data work (sums, joins, reconciliation). Put that in pandas or SQL and give the agent a tool that calls it.

> **📝 Note — How to tell whether you need an agent**
>
> A useful test before you write any code: try to draw the workflow as a flowchart. If every diamond (decision) can be resolved by looking at data your code already has — a status field, a row count, a regex match — you have a chain, and possibly just a script. If a diamond can only be resolved by *reading and understanding* something (a document, a user's ambiguous request, a tool's unexpected output), that diamond is a candidate for the model. Count the diamonds. One or two model-resolved decisions in an otherwise fixed flow is a Router (Section 1.5). Only when the *number and order* of decisions is unknown in advance do you need a loop.
>
> Students often over-estimate how much of a task is "reasoning". Reconciling two ledgers sounds like reasoning; it is a join and a diff. Summarising why the ledgers differ *is* reasoning. Draw the line at the point where the answer stops being computable, and put the model on the far side of it.

## 1.2 The ReAct pattern

ReAct (Yao et al., 2022) interleaves **Reasoning** traces with **Acting** steps. Each iteration produces:

- **Thought** — a short chain-of-thought about what to do next.
- **Action** — a tool call with arguments.
- **Observation** — the tool's output, fed back to the model.

Modern APIs implement this natively: the model emits structured `tool_use` blocks rather than free text you have to parse. The reasoning is either implicit or returned as a `text` block alongside the call. You no longer regex-parse `Action: search[...]` out of prose — but the loop is the same.

> **📝 Note — What the "Thought" is really for**
>
> The original ReAct paper forced the model to write its reasoning as text because that measurably improved which action it chose next — the thought is scaffolding for the action, not commentary for the human. Two practical consequences:
>
> First, do not strip the text blocks out of the message history to save tokens. The model's next decision depends on being able to see its own earlier reasoning. Trim old *observations* before you trim old thoughts.
>
> Second, do not trust the thought as an explanation of *why* the model did something. It is a plausible narrative generated alongside the action, and it can be confidently wrong about the model's own behaviour. Treat it as a useful debugging signal (Chapter 6), never as an audit record. If you need a defensible reason for an action, make the model produce it as a structured field that your code checks, as the supervisor does in Chapter 4.

## 1.3 Build: the LLM wrapper and the trace

Before writing the agent, we build two small pieces of infrastructure we will keep for the whole course.

### The client wrapper

Wrap the SDK so that (a) the model is pinned, (b) usage is accounted for, and (c) tests can substitute a fake.

```python
# agentkit/llm.py
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

import anthropic

from agentkit.config import settings


@dataclass(slots=True)
class Usage:
    """Cumulative token usage for one agent run."""

    input_tokens: int = 0
    output_tokens: int = 0

    def add(self, other: anthropic.types.Usage) -> None:
        self.input_tokens += other.input_tokens
        self.output_tokens += other.output_tokens

    def estimated_cost_usd(self, in_per_m: float, out_per_m: float) -> float:
        return (self.input_tokens * in_per_m + self.output_tokens * out_per_m) / 1_000_000


class ChatModel(Protocol):
    """Anything that can take a message list + tools and return a Message.

    Defining this as a Protocol lets tests inject a scripted fake without
    monkeypatching the SDK.
    """

    def complete(
        self,
        *,
        system: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> anthropic.types.Message: ...


@dataclass(slots=True)
class AnthropicChatModel:
    client: anthropic.Anthropic = field(default_factory=anthropic.Anthropic)
    usage: Usage = field(default_factory=Usage)

    def complete(
        self,
        *,
        system: str,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> anthropic.types.Message:
        response = self.client.messages.create(
            model=settings.model,
            max_tokens=settings.max_tokens,
            system=system,
            messages=messages,
            tools=tools,
        )
        self.usage.add(response.usage)
        return response
```

### The trace recorder

Every agent step is recorded as a plain dataclass. In Chapter 6 we grade these; in Chapter 4 we replay them. Building it now costs ten lines.

```python
# agentkit/trace.py
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any, Literal

StepKind = Literal["thought", "tool_call", "tool_result", "answer", "error"]


@dataclass(frozen=True, slots=True)
class Step:
    kind: StepKind
    payload: dict[str, Any]
    t: float = field(default_factory=time.monotonic)


@dataclass(slots=True)
class Trace:
    """Append-only record of one agent run."""

    steps: list[Step] = field(default_factory=list)

    def record(self, kind: StepKind, **payload: Any) -> None:
        self.steps.append(Step(kind=kind, payload=payload))

    def tool_calls(self) -> list[Step]:
        return [s for s in self.steps if s.kind == "tool_call"]

    def __len__(self) -> int:
        return len(self.steps)
```

> **📝 Note — Why the trace comes before the agent**
>
> It is tempting to skip `trace.py` and add logging "later". Don't. Every hard problem in this course — evaluation, loop detection, replay, cost attribution — is a question about *what the agent did*, step by step. If that record exists from the first run, each of those problems is a function over a list. If it doesn't, each of them starts with retrofitting instrumentation into a loop you no longer fully remember.
>
> Keep the record boring: plain dataclasses, JSON-serialisable payloads, monotonic timestamps. Resist adding behaviour to it. A trace that can be dumped to a file, diffed, and loaded back in a test is worth more than a clever one.

## 1.4 Build: a ReAct agent from scratch

For this chapter, tools are plain Python functions with a hand-written schema. Chapter 2 replaces this with a proper registry; keep it simple now so the *loop* is the thing you see.

```python
# agentkit/react.py
from __future__ import annotations

import json
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from agentkit.config import settings
from agentkit.llm import ChatModel
from agentkit.trace import Trace

ToolFn = Callable[..., Any]


@dataclass(frozen=True, slots=True)
class ToolSpec:
    name: str
    description: str
    input_schema: dict[str, Any]
    fn: ToolFn

    def to_api(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }


class StepLimitExceeded(RuntimeError):
    """The agent loop hit settings.max_steps without finishing."""


@dataclass(slots=True)
class ReActAgent:
    model: ChatModel
    tools: list[ToolSpec]
    system: str = (
        "You are a careful assistant. Think step by step. Use tools when they "
        "help; when you have enough information, answer directly."
    )
    trace: Trace = field(default_factory=Trace)

    def run(self, task: str) -> str:
        messages: list[dict[str, Any]] = [{"role": "user", "content": task}]
        by_name = {t.name: t for t in self.tools}
        api_tools = [t.to_api() for t in self.tools]

        for _ in range(settings.max_steps):
            response = self.model.complete(
                system=self.system, messages=messages, tools=api_tools
            )
            # Always echo the assistant turn back verbatim — the API requires
            # tool_use blocks to be followed by matching tool_result blocks.
            messages.append({"role": "assistant", "content": response.content})

            if response.stop_reason != "tool_use":
                answer = _text_of(response.content)
                self.trace.record("answer", text=answer)
                return answer

            results = []
            for block in response.content:
                if block.type == "text":
                    self.trace.record("thought", text=block.text)
                elif block.type == "tool_use":
                    self.trace.record("tool_call", name=block.name, input=block.input)
                    output = self._execute(by_name[block.name], block.input)
                    self.trace.record("tool_result", name=block.name, output=output)
                    results.append(
                        {"type": "tool_result", "tool_use_id": block.id, "content": output}
                    )
            messages.append({"role": "user", "content": results})

        raise StepLimitExceeded(f"no answer after {settings.max_steps} steps")

    @staticmethod
    def _execute(tool: ToolSpec, args: dict[str, Any]) -> str:
        # Chapter 2 replaces this with validated, resilient execution.
        try:
            return json.dumps(tool.fn(**args), default=str)
        except Exception as exc:  # noqa: BLE001 — the model must see failures
            return json.dumps({"error": type(exc).__name__, "message": str(exc)})


def _text_of(content: list[Any]) -> str:
    return "".join(b.text for b in content if b.type == "text").strip()
```

Read `run()` twice. Notice what it *is* and what it *isn't*:

- It is a bounded `for` loop, not a `while True`. The step limit is structural.
- It is a pure function of `(task, model, tools)` apart from side effects inside tools.
- Tool errors are returned *to the model* as observations, not raised. An agent that can see "file not found" can recover; one that crashes cannot.

### A first tool and a first run

```python
# examples/ch1_react.py
import ast
import operator as op

from agentkit.llm import AnthropicChatModel
from agentkit.react import ReActAgent, ToolSpec

_OPS = {ast.Add: op.add, ast.Sub: op.sub, ast.Mult: op.mul, ast.Div: op.truediv, ast.Pow: op.pow}


def calculate(expression: str) -> float:
    """Safely evaluate an arithmetic expression. No eval(): we walk the AST."""

    def _ev(node: ast.AST) -> float:
        match node:
            case ast.Expression(body=b):
                return _ev(b)
            case ast.Constant(value=v) if isinstance(v, int | float):
                return float(v)
            case ast.BinOp(left=l, op=o, right=r) if type(o) in _OPS:
                return _OPS[type(o)](_ev(l), _ev(r))
            case ast.UnaryOp(op=ast.USub(), operand=x):
                return -_ev(x)
        raise ValueError(f"unsupported syntax: {ast.dump(node)}")

    return _ev(ast.parse(expression, mode="eval"))


calc = ToolSpec(
    name="calculate",
    description="Evaluate an arithmetic expression, e.g. '(3 + 4) * 12'.",
    input_schema={
        "type": "object",
        "properties": {"expression": {"type": "string"}},
        "required": ["expression"],
    },
    fn=calculate,
)

if __name__ == "__main__":
    agent = ReActAgent(model=AnthropicChatModel(), tools=[calc])
    print(agent.run("A bond pays 4.5% on £250,000. What is 7 years of interest, simple?"))
    for step in agent.trace.steps:
        print(f"{step.kind:12} {step.payload}")
```

The trace output is the point of this exercise. You should see the model *choose* to call `calculate`, receive the observation, and then answer. If it answers without calling the tool, that's a data point too — and in Chapter 6 you'll write a grader that catches it.

> **📝 Note — Reading your first trace**
>
> Run the example three or four times and read every trace, not just the answer. You are looking for three things. *Did it use the tool at all?* Models are willing to do arithmetic in their heads, and they are wrong more often than they look. *Did it use it once or several times?* Multiple calls for one calculation usually means the tool description is unclear about what the tool accepts. *Did the text before the call actually predict the call?* When the thought says "I'll compute the interest" and the action is a date lookup, the model is confused, and the fix is almost always in the prompt or the tool description rather than in the loop.
>
> Make a habit of this now. In Chapter 6 you will turn each of those questions into an automated grader, and it is much easier to write a grader for a failure you have seen with your own eyes.

## 1.5 Other agent architectures

ReAct is the default because it is simple and general. Three alternatives cover most of the remaining design space.

### Plan-and-Execute

Generate a full plan first, then execute each step (with a simpler executor), then optionally re-plan. Cheaper per step, more predictable, worse at adapting mid-task.

```python
# agentkit/patterns.py (excerpt)
from __future__ import annotations

from dataclasses import dataclass

from pydantic import BaseModel

from agentkit.react import ReActAgent


class Plan(BaseModel):
    steps: list[str]


@dataclass(slots=True)
class PlanAndExecute:
    planner: ReActAgent     # tools=[] — planning is pure reasoning
    executor: ReActAgent    # has the real tools

    def run(self, task: str) -> str:
        raw = self.planner.run(
            f"Produce a numbered plan (max 6 steps) to accomplish: {task}\n"
            "Respond ONLY with JSON: {\"steps\": [...]}"
        )
        plan = Plan.model_validate_json(raw)
        results: list[str] = []
        for i, step in enumerate(plan.steps, 1):
            context = "\n".join(f"Step {j}: {r}" for j, r in enumerate(results, 1))
            results.append(self.executor.run(f"{context}\n\nNow do step {i}: {step}"))
        return results[-1]
```

### Reflexion

Run, self-critique, run again with the critique in context. Improves quality on tasks with a verifiable signal (tests pass, numbers reconcile). Doubles cost at minimum.

```python
@dataclass(slots=True)
class Reflexion:
    agent: ReActAgent
    critic: ReActAgent
    max_rounds: int = 2

    def run(self, task: str) -> str:
        attempt = self.agent.run(task)
        for _ in range(self.max_rounds):
            critique = self.critic.run(
                f"Task: {task}\nAttempt: {attempt}\n"
                "List concrete flaws, or reply exactly 'ACCEPT' if none."
            )
            if critique.strip() == "ACCEPT":
                break
            attempt = self.agent.run(f"{task}\n\nPrevious attempt: {attempt}\nCritique: {critique}")
        return attempt
```

### Router (a.k.a. classifier-then-chain)

One cheap LLM call classifies the request; deterministic code dispatches to a specialised chain or agent. Often the right answer when someone says "we need an agent" and actually needs three well-defined workflows.

### Comparing architectures

| Pattern | Adapts mid-task | Cost | Predictability | Use when |
|---|---|---|---|---|
| ReAct | Yes | Medium | Low | Open-ended tasks, unknown path |
| Plan-and-Execute | Partly (re-plan) | Low–medium | Medium | Long tasks with mostly known structure |
| Reflexion | Yes | High | Medium | A verifier exists (tests, checksums) |
| Router | No | Lowest | High | Bounded set of known workflows |

## 1.6 Exercises

1. **Add a second tool** (`current_date`) and confirm the agent uses both in one run.
2. **Scripted fake model.** Implement `FakeChatModel(ChatModel)` that returns a pre-built list of `Message` objects in order. Write a `pytest` that drives `ReActAgent` through a two-step run with no network.
3. **Bounded loop test.** Make the fake always return `tool_use`; assert `StepLimitExceeded` is raised at exactly `max_steps`.
4. **Architecture bake-off.** Run ReAct, Plan-and-Execute and Reflexion on the same five tasks. Record steps, tokens and correctness in a table. Write one paragraph on which you would ship for each task and why.
5. *(Stretch)* Implement a `Router` that dispatches to a chain or a `ReActAgent` based on a classification call, and show a case where it beats plain ReAct on cost by >50%.

## 1.7 Checkpoint

You can: run a ReAct loop against real tools; inspect its trace; test it without network access; explain to a colleague when you would *not* use an agent.

---

# Chapter 2 — Mastering Tool Use and Function Calling

*Week 2. Defining tools for LLMs, function-calling mechanics, multi-tool agents, and failure handling.*

## 2.1 What a tool actually is

From the model's point of view a tool is three things: a **name**, a **description**, and a **JSON Schema** for its input. The model never sees your code. It reads the description, decides to call the tool, and emits a JSON object it *believes* matches the schema.

That word — *believes* — is the entire discipline of this chapter. The model is a very good guesser and a very bad guarantee. Every tool boundary is therefore a validation boundary:

```
model output (untrusted JSON) → Pydantic validation → typed Python → business logic
```

Nothing hallucinated should ever reach the third arrow.

### Writing descriptions that work

The description is a prompt. Good ones state:

- **What** the tool does, in one sentence.
- **When** to use it (and when not to — "do not use for dates before 2000").
- **What the arguments mean** including units and formats ("ISO-8601 date", "ISIN, not ticker").
- **What it returns**, especially error shapes.

A tool named `search` with the description "search" will be called for everything. A tool named `search_internal_policy_docs` with a two-sentence description will be called correctly.

### Design rules for tool interfaces

1. **Narrow inputs.** Accept identifiers, not free text, wherever the downstream system is deterministic. A tool that takes an `invoice_id` cannot be fed a hallucinated total; a tool that takes `amount: float` can.
2. **Idempotent where possible.** Agents retry. A `create_ticket` tool that isn't idempotent will create three tickets.
3. **Small, composable tools** beat one giant one. The model reasons better over five clear verbs than one `do_everything(action: str, **kwargs)`.
4. **Return structured, bounded output.** Truncate long results and say so (`"...[truncated, 4,200 more chars]"`). The model's context is your scarcest resource.

> **📝 Note — Fewer tools than you think**
>
> Tool selection accuracy degrades as the tool list grows, and it degrades faster when tools overlap. A model choosing between `search_docs` and `search_knowledge_base` will guess. A model choosing between `search_policy_docs` and `search_runbooks` will not. Before adding a tool, ask whether an existing one could take an extra enum argument instead — `retrieve(collection=...)` in Chapter 5 is one tool doing the work of three, with the model's choice constrained to a closed set.
>
> A workable ceiling for a single agent is somewhere around ten to fifteen well-separated tools. Past that, either the tools are too fine-grained or the agent has too many jobs — which is the signal to split it (Chapter 4), not to add more tools.

## 2.2 Build: a typed tool registry

We replace Chapter 1's hand-written `ToolSpec` with a decorator that derives the schema from a Pydantic model, validates every call, and normalises errors.

```python
# agentkit/tools/errors.py
from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class Failure(StrEnum):
    """Every tool failure is classified so the loop can react differently."""

    INVALID_INPUT = "invalid_input"     # model's fault — tell it, let it retry
    NOT_FOUND = "not_found"             # legitimate — tell it
    TRANSIENT = "transient"             # infra — retry silently
    TIMEOUT = "timeout"                 # infra — retry with backoff, then tell it
    PERMISSION = "permission"           # policy — tell it, never retry
    INTERNAL = "internal"               # our bug — tell it briefly, log fully


class ToolError(Exception):
    def __init__(self, kind: Failure, message: str, *, retryable: bool = False) -> None:
        super().__init__(message)
        self.kind = kind
        self.retryable = retryable


@dataclass(frozen=True, slots=True)
class ToolOutcome:
    """What the model sees. Always serialisable, never an exception object."""

    ok: bool
    content: str
    failure: Failure | None = None
```

```python
# agentkit/tools/core.py
from __future__ import annotations

import inspect
import json
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, get_type_hints

from pydantic import BaseModel, ValidationError

from agentkit.tools.errors import Failure, ToolError, ToolOutcome

MAX_OUTPUT_CHARS = 8_000


@dataclass(frozen=True, slots=True)
class Tool:
    name: str
    description: str
    input_model: type[BaseModel]
    fn: Callable[[BaseModel], Any]

    def to_api(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_model.model_json_schema(),
        }

    def invoke(self, raw_args: dict[str, Any]) -> ToolOutcome:
        """Validate → execute → serialise. Never raises."""
        try:
            args = self.input_model.model_validate(raw_args)
        except ValidationError as e:
            return _fail(Failure.INVALID_INPUT, e.errors(include_url=False))
        try:
            result = self.fn(args)
        except ToolError as e:
            return _fail(e.kind, str(e))
        except Exception as e:  # noqa: BLE001
            # Log the full exception here; the model gets a short, safe message.
            return _fail(Failure.INTERNAL, f"{type(e).__name__}: {e}")
        return ToolOutcome(ok=True, content=_bounded(result))


def _fail(kind: Failure, detail: Any) -> ToolOutcome:
    body = json.dumps({"error": kind, "detail": detail}, default=str)
    return ToolOutcome(ok=False, content=body, failure=kind)


def _bounded(result: Any) -> str:
    text = result if isinstance(result, str) else json.dumps(result, default=str)
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return f"{text[:MAX_OUTPUT_CHARS]}\n...[truncated {len(text) - MAX_OUTPUT_CHARS} chars]"


class ToolRegistry:
    """Collects tools and exposes them in the API's shape."""

    def __init__(self) -> None:
        self._tools: dict[str, Tool] = {}

    def tool(self, description: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
        """Decorator. The function must take exactly one Pydantic-model argument."""

        def register(fn: Callable[..., Any]) -> Callable[..., Any]:
            params = list(inspect.signature(fn).parameters)
            hints = get_type_hints(fn)
            if len(params) != 1 or not issubclass(hints[params[0]], BaseModel):
                raise TypeError(f"{fn.__name__} must accept a single Pydantic model argument")
            self._tools[fn.__name__] = Tool(
                name=fn.__name__,
                description=description,
                input_model=hints[params[0]],
                fn=fn,
            )
            return fn

        return register

    def __getitem__(self, name: str) -> Tool:
        try:
            return self._tools[name]
        except KeyError:
            raise ToolError(Failure.NOT_FOUND, f"unknown tool {name!r}") from None

    def to_api(self) -> list[dict[str, Any]]:
        return [t.to_api() for t in self._tools.values()]

    def __iter__(self):
        return iter(self._tools.values())
```

Using it:

```python
# agentkit/tools/builtin.py
from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from agentkit.tools.core import ToolRegistry

registry = ToolRegistry()


class CalcInput(BaseModel):
    expression: str = Field(description="Arithmetic only, e.g. '(3 + 4) * 12'. No variables.")


@registry.tool("Evaluate an arithmetic expression exactly. Use for any numeric computation; never do arithmetic in your head.")
def calculate(args: CalcInput) -> float:
    from examples.ch1_react import calculate as _calc  # the AST evaluator from Ch.1
    return _calc(args.expression)


class TodayInput(BaseModel):
    pass


@registry.tool("Return today's date in ISO-8601. Use before any relative-date reasoning.")
def today(args: TodayInput) -> str:
    return date.today().isoformat()


class LookupInput(BaseModel):
    isin: str = Field(pattern=r"^[A-Z]{2}[A-Z0-9]{9}\d$", description="12-character ISIN, e.g. IE00B4L5Y983")


@registry.tool("Look up a security by ISIN. Returns name, currency and last close. Use ISIN only, never a name or ticker.")
def lookup_security(args: LookupInput) -> dict:
    # Stub. In production: a deterministic call to your reference-data service.
    return {"isin": args.isin, "name": "Example ETF", "ccy": "EUR", "last_close": 81.42}
```

The `pattern=` on `isin` is doing real work: a model that hallucinates `"AAPL"` gets an `INVALID_INPUT` outcome describing the regex, and — in practice — corrects itself on the next step. You have moved a class of error from *silent* to *self-healing* with one line.

## 2.3 Build: resilience — retries, timeouts, circuit breakers

Real tools call networks. Networks fail. The agent loop must not.

```python
# agentkit/tools/resilience.py
from __future__ import annotations

import time
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
from dataclasses import dataclass, field
from typing import Any

from tenacity import retry, retry_if_exception, stop_after_attempt, wait_exponential

from agentkit.tools.errors import Failure, ToolError

_executor = ThreadPoolExecutor(max_workers=8)


def with_timeout(seconds: float) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    """Run the tool body in a worker thread; raise TIMEOUT if it overruns."""

    def deco(fn: Callable[..., Any]) -> Callable[..., Any]:
        def wrapped(*a: Any, **kw: Any) -> Any:
            future = _executor.submit(fn, *a, **kw)
            try:
                return future.result(timeout=seconds)
            except FutureTimeout:
                raise ToolError(Failure.TIMEOUT, f"exceeded {seconds}s", retryable=True) from None

        wrapped.__name__, wrapped.__doc__ = fn.__name__, fn.__doc__
        wrapped.__annotations__ = fn.__annotations__
        return wrapped

    return deco


def _is_retryable(exc: BaseException) -> bool:
    return isinstance(exc, ToolError) and exc.retryable


retry_transient = retry(
    retry=retry_if_exception(_is_retryable),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=0.5, max=4),
    reraise=True,
)


@dataclass(slots=True)
class CircuitBreaker:
    """Stop calling a dependency that is clearly down. Per-tool instance."""

    failure_threshold: int = 5
    reset_after: float = 30.0
    _failures: int = 0
    _opened_at: float | None = None

    def __call__(self, fn: Callable[..., Any]) -> Callable[..., Any]:
        def wrapped(*a: Any, **kw: Any) -> Any:
            if self._opened_at and time.monotonic() - self._opened_at < self.reset_after:
                raise ToolError(Failure.TRANSIENT, "circuit open; dependency unavailable")
            try:
                result = fn(*a, **kw)
            except ToolError as e:
                if e.retryable:
                    self._failures += 1
                    if self._failures >= self.failure_threshold:
                        self._opened_at = time.monotonic()
                raise
            self._failures, self._opened_at = 0, None
            return result

        wrapped.__name__, wrapped.__doc__ = fn.__name__, fn.__doc__
        wrapped.__annotations__ = fn.__annotations__
        return wrapped
```

Composed, innermost first: timeout → retry → breaker → registry.

```python
class SearchInput(BaseModel):
    query: str = Field(max_length=200)
    k: int = Field(default=5, ge=1, le=20)

_search_breaker = CircuitBreaker()

@registry.tool("Web search. Returns up to k results with title, url and snippet.")
@_search_breaker
@retry_transient
@with_timeout(10.0)
def web_search(args: SearchInput) -> list[dict]:
    import httpx
    try:
        r = httpx.get("https://example-search/api", params={"q": args.query, "k": args.k})
    except httpx.TransportError as e:
        raise ToolError(Failure.TRANSIENT, str(e), retryable=True) from e
    if r.status_code == 429:
        raise ToolError(Failure.TRANSIENT, "rate limited", retryable=True)
    r.raise_for_status()
    return r.json()["results"]
```

### What the model should and shouldn't see

| Failure kind | Handled in code | Shown to model |
|---|---|---|
| `TRANSIENT`, `TIMEOUT` | Retry with backoff (up to 3) | Only after retries exhausted |
| `INVALID_INPUT` | — | Yes, with validation detail |
| `NOT_FOUND` | — | Yes |
| `PERMISSION` | Never retry | Yes, short |
| `INTERNAL` | Full stack trace to logs | Short type + message |

The principle: **infrastructure failures are your problem; semantic failures are the model's problem.** Don't burn model steps on things `tenacity` can fix, and don't hide from the model things only it can fix.

> **📝 Note — Error messages are prompts**
>
> Whatever string you return in the `error` field is read by the model and shapes its next action. A message like `KeyError: 'isin'` teaches it nothing; `"expected a 12-character ISIN such as IE00B4L5Y983; got 'AAPL'"` teaches it exactly what to do next. Write tool errors the way you would write a lint message for a junior engineer: what was wrong, what would have been right, and — if the tool is genuinely unavailable — what alternative exists.
>
> Be equally careful about what you *don't* say. Stack traces, internal hostnames, and raw upstream responses leak into the model's context and from there into logs and, potentially, answers. The `INTERNAL` branch in `Tool.invoke` returns only the exception type and message for this reason; the full traceback belongs in your logging pipeline, keyed by the run ID from the trace.

## 2.4 Build: the multi-tool agent

Upgrade `ReActAgent` to use the registry. The loop is unchanged except that execution goes through `Tool.invoke` and we surface `is_error` to the API.

```python
# agentkit/react.py — replace _execute and the tools field
from agentkit.tools.core import ToolRegistry

@dataclass(slots=True)
class ReActAgent:
    model: ChatModel
    tools: ToolRegistry
    ...

    # inside run(), replacing the tool_use branch:
    outcome = self.tools[block.name].invoke(block.input)
    self.trace.record("tool_result", name=block.name, ok=outcome.ok, output=outcome.content)
    results.append({
        "type": "tool_result",
        "tool_use_id": block.id,
        "content": outcome.content,
        "is_error": not outcome.ok,
    })
```

### Parallel tool calls

The model may emit several `tool_use` blocks in one turn. They are independent by construction, so execute them concurrently:

```python
from concurrent.futures import ThreadPoolExecutor

calls = [b for b in response.content if b.type == "tool_use"]
with ThreadPoolExecutor() as pool:
    outcomes = list(pool.map(lambda b: self.tools[b.name].invoke(b.input), calls))
```

Return the `tool_result` blocks in the same order as the `tool_use` blocks. Order is a hard API requirement.

## 2.5 Exercises

1. **Port Chapter 1's tools** to the registry and confirm the validation error path by asking the agent something that tempts a bad argument (e.g. "look up Apple" against `lookup_security`).
2. **Failure injection.** Write a `flaky_tool` that raises `TRANSIENT` on the first two calls. Assert (with the fake model) that the agent sees one success and zero errors.
3. **Breaker test.** Drive `flaky_tool` to open the circuit; assert the sixth call fails fast without invoking the body.
4. **Output bounding.** Return a 100 KB string from a tool; verify the truncation notice and that the agent still completes.
5. *(Stretch)* Add a `requires_approval: bool` flag to `Tool`. When set, `invoke` returns a `PENDING_APPROVAL` outcome instead of executing. Chapter 4 will wire this to a human.

## 2.6 Checkpoint

Your agent runs against 4+ tools; every input is validated; transient failures are invisible to the model; permanent ones are reported to it in a structured form; nothing in the loop can raise except the step limit.

---

# Chapter 3 — LangGraph Fundamentals

*Week 3. State management, nodes, edges. Rebuilding the agent as a graph.*

## 3.1 Why a graph?

Chapter 1's loop is fine until you need to:

- **pause** and resume a run (for human approval, or because the process restarted);
- **branch** into different sub-workflows based on state;
- **observe** each node independently;
- **compose** agents into larger systems.

You can bolt all of that onto a hand-written loop — and you'd end up re-implementing a graph runtime. LangGraph *is* that runtime. Its core abstraction:

- **State** — a typed dictionary that flows through the graph.
- **Nodes** — functions `State → partial State`.
- **Edges** — static (`A → B`) or conditional (`A → f(state)`).
- **Checkpointer** — persists state after every node, keyed by `thread_id`.

The mental model is a state machine where each transition may involve an LLM. Deterministic control flow, stochastic content — which is exactly the split we want.

## 3.2 State and reducers

```python
# agentkit/graph/state.py
from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    messages: Annotated[list[AnyMessage], add_messages]
    steps: int
```

`Annotated[..., add_messages]` declares a **reducer**: when a node returns `{"messages": [new]}`, LangGraph *appends* rather than overwrites. Fields without a reducer are replaced. Getting this wrong is the single most common LangGraph bug — you return one message and the whole history vanishes.

Rule of thumb: lists that accumulate get a reducer (`add_messages`, or `operator.add`); scalars and "current value" fields don't.

> **📝 Note — Designing state**
>
> State is the interface between every node in your graph, so it deserves the same care as a public API. Keep it small. A common failure is a "god state" with thirty fields, half of them scratch space for one node, which makes every node coupled to every other. If a value is only used inside a single node, keep it local. If it is used by two nodes that run in sequence, consider whether it should live in the message list instead, where the model can see it.
>
> Prefer explicit fields over stuffing structured data into message text. `steps: int` is checkable in `route()`; "Step 4 of 12" buried in an AI message is not. The rule from Chapter 2 applies here too — anything code needs to make a decision on must be a typed field, not prose.

## 3.3 Build: ReAct as a StateGraph

We reuse the Chapter 2 registry by adapting our `Tool` objects into LangChain tools — a five-line shim — so `ToolNode` can execute them.

```python
# agentkit/graph/react_graph.py
from __future__ import annotations

from langchain_anthropic import ChatAnthropic
from langchain_core.tools import StructuredTool
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode

from agentkit.config import settings
from agentkit.graph.state import AgentState
from agentkit.tools.builtin import registry
from agentkit.tools.core import Tool


def as_langchain_tool(tool: Tool) -> StructuredTool:
    """Bridge our registry into LangGraph. Validation still happens in Tool.invoke."""
    return StructuredTool.from_function(
        name=tool.name,
        description=tool.description,
        args_schema=tool.input_model,
        func=lambda **kw: tool.invoke(kw).content,
    )


lc_tools = [as_langchain_tool(t) for t in registry]
llm = ChatAnthropic(model=settings.model, max_tokens=settings.max_tokens).bind_tools(lc_tools)

SYSTEM = "You are a careful assistant. Use tools when they help; answer when you have enough."


def agent_node(state: AgentState) -> dict:
    response = llm.invoke([("system", SYSTEM), *state["messages"]])
    return {"messages": [response], "steps": state["steps"] + 1}


def route(state: AgentState) -> str:
    last = state["messages"][-1]
    if state["steps"] >= settings.max_steps:
        return "halt"
    return "tools" if getattr(last, "tool_calls", None) else "finish"


def build() -> "CompiledGraph":
    g = StateGraph(AgentState)
    g.add_node("agent", agent_node)
    g.add_node("tools", ToolNode(lc_tools))
    g.add_node("halt", lambda s: {"messages": [("ai", "Stopped: step budget exhausted.")]})

    g.add_edge(START, "agent")
    g.add_conditional_edges("agent", route, {"tools": "tools", "finish": END, "halt": "halt"})
    g.add_edge("tools", "agent")
    g.add_edge("halt", END)

    return g.compile(checkpointer=MemorySaver())


if __name__ == "__main__":
    graph = build()
    config = {"configurable": {"thread_id": "demo-1"}}
    out = graph.invoke({"messages": [("user", "What's 7 years of 4.5% on 250000?")], "steps": 0}, config)
    print(out["messages"][-1].content)
```

Draw it:

```
START → agent ─┬─ (tool_calls) ──► tools ──► agent
               ├─ (no calls)  ──► END
               └─ (steps ≥ N) ──► halt ──► END
```

The `route` function is the Chapter 1 loop's `if response.stop_reason != "tool_use"` turned into an explicit, testable edge. The step budget is now a *node* — it shows up in traces and in the diagram, which is exactly where a safety control belongs.

## 3.4 Persistence and resumption

Because we compiled with a checkpointer, the same `thread_id` continues the conversation:

```python
graph.invoke({"messages": [("user", "And compounded?")]}, config)   # remembers everything
```

Swap `MemorySaver` for `SqliteSaver` / `PostgresSaver` and the run survives process restarts. Inspect history:

```python
for snap in graph.get_state_history(config):
    print(snap.metadata["step"], snap.next, len(snap.values["messages"]))
```

Every snapshot is a point you can **fork** from (`graph.update_state`) — the foundation of Chapter 6's replay debugging.

> **📝 Note — Checkpoints are data you are storing**
>
> The moment you compile with a persistent checkpointer, every message, tool input and tool output in every run is written to a database. In many organisations that has consequences: personal data now sits in a store that needs retention rules; a tool that returned a customer record has copied it into the checkpoint; a run that a user asked you to delete is now spread across dozens of snapshots.
>
> Decide early who owns that store, how long snapshots live, and which tools are allowed to return data that should never be persisted. A practical pattern is to have such tools return an opaque reference (an ID) and have the *rendering* layer, not the graph, resolve it for the user. This is the same narrow-input principle from Chapter 2, applied to outputs.
>
> Thread IDs also deserve a moment's thought. Use opaque, unguessable identifiers, and never derive them from something a user could type — a thread ID is effectively a bearer token for the conversation it names.

## 3.5 Streaming

Agents are slow. Stream node outputs so users see progress:

```python
for event in graph.stream(inputs, config, stream_mode="updates"):
    for node, delta in event.items():
        print(f"[{node}] {delta}")
```

`stream_mode="messages"` yields token-level chunks for UIs; `"updates"` is what you want in logs.

## 3.6 Exercises

1. **Rebuild** the Chapter 2 multi-tool agent as a graph. Compare the trace of the same task under both implementations.
2. **SQLite checkpointer.** Start a run, kill the process mid-tool-call, restart, and resume the thread.
3. **Add a `summarise` node** that runs when `len(messages) > 30` and replaces the history with a summary (hint: you'll need `RemoveMessage`).
4. **Unit-test `route`** in isolation — it's a pure function of state. This is the payoff of graphs.
5. *(Stretch)* Replace `ToolNode` with your own node that executes tool calls in parallel and records `ToolOutcome.failure` into a new `state["failures"]` list with an `operator.add` reducer.

## 3.7 Checkpoint

Your agent is a compiled graph with typed state, a persistent checkpointer, a routed step budget, and streaming output. You can explain reducers without looking them up.

---

# Chapter 4 — Multi-Agent Systems

*Week 4. Complex graphs and multi-agent collaboration workflows.*

## 4.1 When one agent isn't enough

A single ReAct agent degrades as you add tools and responsibilities: the system prompt grows, tool selection gets noisier, and context fills with irrelevant history. Splitting into specialised agents buys:

- **Focused context** — each worker sees only its tools and its slice of the task.
- **Independent iteration** — you can eval and tune the researcher without touching the writer.
- **Parallelism** — independent workers run concurrently.

It costs coordination overhead, more LLM calls, and a new failure mode: agents arguing in circles. Reach for multi-agent only when a single agent's evals show it failing *because of* breadth.

## 4.2 Topologies

```
Supervisor                 Hierarchical                Handoff (swarm)
                                                        
   ┌────► researcher          supervisor                 A ──► B
   │                             │                       ▲     │
 super ───► analyst           ┌──┴──┐                    └── C ◄┘
   │                       lead_A lead_B        (any agent may hand to any other;
   └────► writer            │  │    │  │         no central coordinator)
(central router;          w1 w2   w3 w4
 workers report back)
```

| Topology | Control | Best for | Watch out for |
|---|---|---|---|
| Supervisor | Central | Bounded teams, clear roles | Supervisor becomes a bottleneck / single point of bad judgement |
| Hierarchical | Layered | Large task trees | Latency; context lost between layers |
| Handoff | Distributed | Conversational flows (support, sales) | Ping-pong loops; unclear ownership |

Supervisor is the default. It maps cleanly to a graph and keeps the termination decision in one place.

## 4.3 Build: a supervisor-led research team

Three workers — `researcher`, `analyst`, `writer` — and a supervisor that decides who acts next, or that the job is done. Workers are Chapter 3 graphs embedded as **subgraphs**; they share one message channel with the supervisor but keep private scratch state.

```python
# agentkit/graph/team.py
from __future__ import annotations

from typing import Annotated, Literal, TypedDict

from langchain_anthropic import ChatAnthropic
from langchain_core.messages import AnyMessage, HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import create_react_agent
from pydantic import BaseModel

from agentkit.config import settings
from agentkit.graph.react_graph import as_langchain_tool
from agentkit.tools.builtin import registry

Worker = Literal["researcher", "analyst", "writer"]
Next = Worker | Literal["FINISH"]


class TeamState(TypedDict):
    messages: Annotated[list[AnyMessage], add_messages]
    next: Next
    turns: int


class Route(BaseModel):
    """Structured output forces the supervisor to choose from a closed set."""

    next: Next
    reason: str


llm = ChatAnthropic(model=settings.model, max_tokens=settings.max_tokens)
tools = {t.name: as_langchain_tool(t) for t in registry}

# --- workers: each a small ReAct graph with a narrow toolset and prompt ------
researcher = create_react_agent(
    llm, [tools["web_search"], tools["today"]],
    prompt="You gather facts. Return bullet points with sources. Do not analyse.",
)
analyst = create_react_agent(
    llm, [tools["calculate"], tools["lookup_security"]],
    prompt="You compute and compare. Use the calculator for every number. Return a short table.",
)
writer = create_react_agent(
    llm, [],
    prompt="You write the final answer for a senior audience. Concise, no hedging, cite the analyst's numbers.",
)

WORKERS = {"researcher": researcher, "analyst": analyst, "writer": writer}

SUPERVISOR_PROMPT = """You coordinate a team: researcher (facts), analyst (numbers), writer (final prose).
Given the conversation so far, choose who should act next, or FINISH once the writer has produced a final answer.
Do not choose the same worker twice in a row unless their last output was incomplete."""


# --- nodes -------------------------------------------------------------------
def supervisor(state: TeamState) -> dict:
    decision = llm.with_structured_output(Route).invoke(
        [("system", SUPERVISOR_PROMPT), *state["messages"]]
    )
    return {"next": decision.next, "turns": state["turns"] + 1}


def make_worker_node(name: Worker):
    def node(state: TeamState) -> dict:
        result = WORKERS[name].invoke({"messages": state["messages"]})
        # Only the worker's final message crosses back into shared state,
        # tagged with its name so the supervisor knows who spoke.
        final = result["messages"][-1]
        return {"messages": [HumanMessage(content=final.content, name=name)]}

    return node


def route(state: TeamState) -> str:
    if state["turns"] > settings.max_steps:
        return "FINISH"
    return state["next"]


def build():
    g = StateGraph(TeamState)
    g.add_node("supervisor", supervisor)
    for name in WORKERS:
        g.add_node(name, make_worker_node(name))
        g.add_edge(name, "supervisor")     # every worker reports back
    g.add_edge(START, "supervisor")
    g.add_conditional_edges(
        "supervisor", route, {**{n: n for n in WORKERS}, "FINISH": END}
    )
    return g.compile(checkpointer=MemorySaver())
```

Three deliberate choices here, each worth a minute:

1. **Structured output for routing.** `Route` is a Pydantic model with a `Literal` type. The supervisor *cannot* route to a worker that doesn't exist. Compare to parsing "I think the analyst should go next" out of prose.
2. **Only final messages cross the boundary.** Workers' internal tool chatter stays in the subgraph. This is context hygiene: the writer does not need to see the researcher's failed searches.
3. **The turn budget is enforced in `route`, not in the prompt.** "Do not loop forever" in a system prompt is a wish. `if state["turns"] > N` is a guarantee.

> **📝 Note — The hidden cost of multi-agent**
>
> Every hand-off between agents is a lossy compression. The researcher's twelve tool calls become one message; the supervisor reads that message and writes a one-line routing decision; the analyst starts from that line. Information that was obvious inside one worker's context is invisible to the next. Before splitting an agent, write down what each worker *must* know that only another worker could tell it, and make sure that crosses the boundary as a structured field or a clearly labelled message — not as an implicit assumption.
>
> The other cost is latency. A supervisor round trip is an extra LLM call between every worker turn, so a task that a single agent finishes in six steps may take fifteen calls in a team. If your evals show the single agent succeeding, keep the single agent. Multi-agent is a response to a measured failure of breadth, not a default architecture.

## 4.4 Human-in-the-loop

Some actions need a person: sending an email, moving money, deleting data. LangGraph's `interrupt()` pauses the graph, persists state, and returns control to the caller. Resume with a `Command`.

```python
# agentkit/graph/hitl.py
from __future__ import annotations

from langgraph.types import Command, interrupt


def approval_gate(state: TeamState) -> dict:
    """Pause before any side-effecting action; resume with the human's verdict."""
    proposed = state["messages"][-1].content
    verdict = interrupt({"proposed_action": proposed, "question": "Approve? (yes/no + reason)"})
    if not verdict.get("approved", False):
        return {"messages": [("user", f"Rejected by reviewer: {verdict.get('reason', '')}")]}
    return {}


# Caller side:
#   result = graph.invoke(inputs, config)            # returns with __interrupt__ set
#   ...show result["__interrupt__"] to a human...
#   graph.invoke(Command(resume={"approved": True}), config)
```

Insert `approval_gate` between the supervisor and any worker whose tools have `requires_approval=True` (Chapter 2, exercise 5). Because state is checkpointed, the approval can arrive hours later, from a different process, via a web form.

> **📝 Note — Designing the approval step**
>
> An approval gate is only as good as what the human is shown. Present the *proposed action with its concrete arguments* — "send email to j.smith@… with subject 'Q3 pack'" — not a summary of the model's intent. The reviewer is approving the tool call, not the plan.
>
> Record the verdict, the reviewer's identity and the timestamp into state alongside the action. Because state is checkpointed, this gives you an audit trail for free; because it is in state, the model can also see that an action was rejected and why, which is what lets it propose a corrected alternative rather than repeating itself.
>
> Finally, decide what happens on *no answer*. An interrupted run parked for a week is not a decision; it is a leak. Put an expiry on pending approvals and route expired ones to a `halt` with a clear message.

## 4.5 Handoffs and `Command`

For swarm-style flows, a node can both update state *and* choose the next node by returning `Command(goto=..., update=...)`. Tools can do this too, which lets an agent "transfer" a conversation:

```python
from langchain_core.tools import tool
from langgraph.types import Command

@tool
def transfer_to_analyst() -> Command:
    """Hand the conversation to the analyst when numbers are needed."""
    return Command(goto="analyst", graph=Command.PARENT)
```

Use sparingly. Handoff graphs are harder to reason about and — because no node owns termination — are the most common source of runaway runs.

## 4.6 Concurrency: fan-out / fan-in

Independent subtasks should run in parallel. `Send` dispatches N copies of a node with different inputs; a reducer on the collecting field merges results.

```python
from operator import add
from langgraph.types import Send

class MapState(TypedDict):
    queries: list[str]
    findings: Annotated[list[str], add]

def fan_out(state: MapState):
    return [Send("research_one", {"query": q}) for q in state["queries"]]

def research_one(state: dict) -> dict:
    out = researcher.invoke({"messages": [("user", state["query"])]})
    return {"findings": [out["messages"][-1].content]}

g = StateGraph(MapState)
g.add_node("research_one", research_one)
g.add_conditional_edges(START, fan_out, ["research_one"])
g.add_edge("research_one", END)
```

## 4.7 Exercises

1. **Run the team** on "Compare the 5-year return of two ETFs by ISIN and write a two-paragraph summary." Inspect who spoke, in what order, and how many turns it took.
2. **Break the supervisor.** Remove the "not twice in a row" instruction and the turn budget. Observe the loop. Restore only the budget. Write down why the budget alone is sufficient.
3. **Wire approval.** Add `send_report` (requires approval) to the writer. Demonstrate interrupt → resume from a second Python process using a SQLite checkpointer.
4. **Fan-out research.** Have the supervisor split a question into 3 sub-queries, run them with `Send`, and merge.
5. *(Stretch)* Implement hierarchical: two supervisors (research lead, delivery lead) under a top-level coordinator. Measure latency vs the flat team.

## 4.8 Checkpoint

You have a persistent, interruptible multi-agent graph with a closed-set router, private worker context, a code-enforced turn budget, and at least one human approval gate.

---

# Chapter 5 — Memory and Context Engineering *(proposed Week 5)*

*What the agent remembers, what it forgets, and how to keep the context window from becoming the bottleneck.*

## 5.1 Three kinds of memory

| Kind | Scope | Mechanism | Lives in |
|---|---|---|---|
| **Working** | This run | The message list | Graph state |
| **Episodic** | This user / thread, across runs | Checkpointer + summaries | Thread store |
| **Semantic** | Everyone, forever | Vector / keyword retrieval | External store, accessed as a *tool* |

The recurring mistake is to stuff all three into the prompt. Working memory is the only thing that belongs there by default. Everything else should be *retrieved* when relevant.

> **📝 Note — Write policy before write mechanism**
>
> The question with long-term memory is never "how do we store it" — the store is a dictionary — but "what is allowed in". A model that decides for itself what to remember will remember its own mistakes, other users' data it happened to see in a tool result, and confident guesses that were never confirmed.
>
> Write the policy as code: a short list of *named* facts the agent may persist (`reporting_currency`, `preferred_format`), each with a validator, and a rule that a fact is written only after the user has stated it explicitly or confirmed it. Everything else stays in the checkpoint for the thread and expires with it. This is a small amount of engineering that prevents a class of bug you cannot eval your way out of, because the wrong memory looks perfectly reasonable in every individual run.

## 5.2 Managing working memory

Context grows linearly with steps; cost and latency grow with it; quality *falls* once the window is crowded. Three tools:

**Trim.** Keep the system prompt, the original task, and the last *k* messages.

```python
from langchain_core.messages import trim_messages

def trimmed(messages, max_tokens=6_000):
    return trim_messages(messages, max_tokens=max_tokens, strategy="last",
                         token_counter=llm, include_system=True, start_on="human")
```

**Summarise.** When the history exceeds a threshold, replace older messages with an LLM-written summary (Chapter 3, exercise 3). Keep the summary *structured* — decisions made, facts established, open questions — not narrative.

**Bound tool outputs.** You did this in Chapter 2 (`MAX_OUTPUT_CHARS`). It is the cheapest and most effective context control there is.

## 5.3 Episodic memory: the store

LangGraph provides a key-value `BaseStore` alongside the checkpointer, namespaced per user. Use it for stable preferences and facts the agent has confirmed, not for raw transcripts.

```python
from langgraph.store.memory import InMemoryStore

store = InMemoryStore()
graph = g.compile(checkpointer=MemorySaver(), store=store)

def remember(state, *, store, config):
    ns = ("user", config["configurable"]["user_id"])
    store.put(ns, "reporting_currency", {"value": "EUR"})

def recall(state, *, store, config):
    ns = ("user", config["configurable"]["user_id"])
    prefs = {i.key: i.value for i in store.search(ns)}
    return {"messages": [("system", f"Known user preferences: {prefs}")]}
```

Decide *in code* what gets written. An agent that writes to long-term memory whenever it feels like it will remember hallucinations forever.

## 5.4 Semantic memory: retrieval as a tool

Treat your document store as just another tool with a narrow input:

```python
class RetrieveInput(BaseModel):
    query: str = Field(max_length=300)
    collection: Literal["policies", "runbooks", "product_docs"]
    k: int = Field(default=4, ge=1, le=10)

@registry.tool("Retrieve passages from internal documents. Choose the collection carefully; policies for rules, runbooks for procedures.")
def retrieve(args: RetrieveInput) -> list[dict]:
    hits = vector_store.similarity_search(args.query, k=args.k, filter={"collection": args.collection})
    return [{"source": h.metadata["source"], "text": h.page_content[:1_000]} for h in hits]
```

This is "agentic RAG": the model decides *whether* and *what* to retrieve, and the retrieval itself is deterministic, bounded and testable. The `Literal` collection is the same trick as the ISIN regex — it makes a class of bad calls impossible.

## 5.5 Exercises

1. Add trimming to the Chapter 3 graph and measure token usage on a 20-turn conversation before and after.
2. Implement structured summarisation with a `Summary` Pydantic model (`facts`, `decisions`, `open_questions`).
3. Wire a `BaseStore` and show a preference surviving across two threads for the same user.
4. Add `retrieve` and write an eval (peek at Chapter 6) that checks the correct collection is chosen for 10 questions.

---

# Chapter 6 — Evaluation and Debugging

*Week 6. Evaluating agent performance, debugging complex graphs, and the runaway-agent problem.*

## 6.1 Why agent evaluation is different

Unit tests assume determinism: same input, same output. Agents violate that in two ways — the *answer* varies, and the *path* varies. So we evaluate two things separately:

- **Outcome quality** — was the final answer right?
- **Trajectory quality** — did it get there sensibly? (right tools, no wasted steps, no forbidden actions)

And we evaluate them **statistically**: run each task N times, report pass rates and distributions, not a single green tick.

> **📝 Note — How many runs is enough?**
>
> With `repeats=3` you can tell a 90% agent from a 30% agent. You cannot tell a 90% agent from an 80% one — the noise on three samples swamps the difference. Before you conclude that a prompt change "improved" pass rate from 0.83 to 0.87, ask how many runs that is based on. On 15 tasks × 3 repeats (45 runs), a 4-point swing is well inside random variation.
>
> A practical rule: use small repeats during development to catch gross regressions quickly, and a larger nightly run (`repeats=10` or more) to make decisions about model or prompt changes. When two configurations are close, run both on the same tasks, the same day, and look at *which tasks* differ, not just the mean. Per-task deltas are far more informative than aggregate scores, and they often point directly at a tool description or a routing rule.

## 6.2 Build: the eval harness

Three concepts: a `Task`, a `Grader`, and a `Runner` that produces a `Report`.

```python
# agentkit/evals/core.py
from __future__ import annotations

import statistics
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any, Protocol

from agentkit.trace import Trace


@dataclass(frozen=True, slots=True)
class Task:
    id: str
    prompt: str
    expected: Any = None                 # ground truth if it exists
    tags: frozenset[str] = frozenset()


@dataclass(frozen=True, slots=True)
class RunResult:
    task: Task
    answer: str
    trace: Trace
    cost_usd: float
    error: str | None = None


@dataclass(frozen=True, slots=True)
class Score:
    name: str
    value: float                         # 0.0 – 1.0
    detail: str = ""


class Grader(Protocol):
    name: str
    def __call__(self, result: RunResult) -> Score: ...


AgentFn = Callable[[str], tuple[str, Trace, float]]   # prompt → (answer, trace, cost)


@dataclass(slots=True)
class Report:
    results: list[RunResult] = field(default_factory=list)
    scores: dict[str, list[Score]] = field(default_factory=dict)   # task_id → scores

    def summary(self) -> dict[str, float]:
        by_grader: dict[str, list[float]] = {}
        for task_scores in self.scores.values():
            for s in task_scores:
                by_grader.setdefault(s.name, []).append(s.value)
        out = {f"{k}/mean": statistics.fmean(v) for k, v in by_grader.items()}
        out["cost/mean_usd"] = statistics.fmean(r.cost_usd for r in self.results)
        out["steps/p95"] = _p95([len(r.trace) for r in self.results])
        out["errors"] = sum(r.error is not None for r in self.results)
        return out


def _p95(xs: list[int]) -> float:
    xs = sorted(xs)
    return float(xs[min(len(xs) - 1, int(0.95 * len(xs)))]) if xs else 0.0


def run_suite(agent: AgentFn, tasks: list[Task], graders: list[Grader], *, repeats: int = 3) -> Report:
    report = Report()
    for task in tasks:
        for i in range(repeats):
            try:
                answer, trace, cost = agent(task.prompt)
                result = RunResult(task, answer, trace, cost)
            except Exception as e:  # noqa: BLE001 — an eval must never crash on one task
                result = RunResult(task, "", Trace(), 0.0, error=f"{type(e).__name__}: {e}")
            report.results.append(result)
            report.scores[f"{task.id}#{i}"] = [g(result) for g in graders]
    return report
```

## 6.3 Graders

Layer them from cheap and exact to expensive and fuzzy.

**Exact / numeric** — when ground truth exists, use it.

```python
# agentkit/evals/graders.py
import re
from dataclasses import dataclass

from agentkit.evals.core import Grader, RunResult, Score


@dataclass(frozen=True, slots=True)
class NumericMatch:
    name: str = "numeric"
    tolerance: float = 0.01

    def __call__(self, r: RunResult) -> Score:
        if r.task.expected is None:
            return Score(self.name, 1.0, "no expectation")
        nums = [float(x.replace(",", "")) for x in re.findall(r"-?[\d,]+\.?\d*", r.answer)]
        hit = any(abs(n - r.task.expected) <= self.tolerance * abs(r.task.expected) for n in nums)
        return Score(self.name, float(hit), f"found {nums}")
```

**Trajectory** — assert facts about the *path*, from the trace.

```python
@dataclass(frozen=True, slots=True)
class UsedTool:
    tool: str
    name: str = "used_tool"

    def __call__(self, r: RunResult) -> Score:
        used = {s.payload["name"] for s in r.trace.tool_calls()}
        return Score(f"{self.name}:{self.tool}", float(self.tool in used), f"used={sorted(used)}")


@dataclass(frozen=True, slots=True)
class NoRepeatedCalls:
    """Same tool + same args twice is almost always a loop."""
    name: str = "no_repeats"

    def __call__(self, r: RunResult) -> Score:
        seen, repeats = set(), 0
        for s in r.trace.tool_calls():
            key = (s.payload["name"], str(sorted(s.payload["input"].items())))
            repeats += key in seen
            seen.add(key)
        return Score(self.name, float(repeats == 0), f"{repeats} repeats")


@dataclass(frozen=True, slots=True)
class StepBudget:
    max_steps: int
    name: str = "step_budget"

    def __call__(self, r: RunResult) -> Score:
        n = len(r.trace.tool_calls())
        return Score(self.name, float(n <= self.max_steps), f"{n} calls")
```

**LLM-as-judge** — for open-ended quality. Use a *rubric*, structured output, and a different (ideally stronger) model than the agent under test.

```python
from pydantic import BaseModel, Field

class Verdict(BaseModel):
    score: int = Field(ge=1, le=5)
    justification: str

JUDGE_PROMPT = """Rate the ANSWER to the TASK on a 1–5 scale using this rubric:
5 = correct, complete, concise; 3 = mostly correct with a material omission; 1 = wrong or unusable.
{reference_clause}
TASK: {task}
ANSWER: {answer}"""

@dataclass(frozen=True, slots=True)
class LLMJudge:
    judge_llm: "ChatAnthropic"
    name: str = "judge"

    def __call__(self, r: RunResult) -> Score:
        ref = f"REFERENCE (may help): {r.task.expected}" if r.task.expected else ""
        v = self.judge_llm.with_structured_output(Verdict).invoke(
            JUDGE_PROMPT.format(reference_clause=ref, task=r.task.prompt, answer=r.answer)
        )
        return Score(self.name, (v.score - 1) / 4, v.justification)
```

Judges drift and have biases (length, position, self-preference). Calibrate: hand-label 30 examples, check agreement, and re-check whenever you change the judge model.

### Assembling a suite

```python
# tests/eval_react.py
from agentkit.evals.core import Task, run_suite
from agentkit.evals.graders import NumericMatch, UsedTool, NoRepeatedCalls, StepBudget

TASKS = [
    Task("simple_interest", "7 years of 4.5% simple interest on 250000?", expected=78750.0, tags={"maths"}),
    Task("compound", "250000 at 4.5% compounded annually for 7 years, total value?", expected=340247.06),
    Task("isin", "What currency is IE00B4L5Y983 denominated in?", expected=None, tags={"lookup"}),
]

GRADERS = [NumericMatch(), UsedTool("calculate"), NoRepeatedCalls(), StepBudget(6)]

def agent_fn(prompt):
    agent = ReActAgent(model=AnthropicChatModel(), tools=registry)
    answer = agent.run(prompt)
    return answer, agent.trace, agent.model.usage.estimated_cost_usd(3.0, 15.0)

if __name__ == "__main__":
    report = run_suite(agent_fn, TASKS, GRADERS, repeats=5)
    for k, v in report.summary().items():
        print(f"{k:24} {v:.3f}")
```

Commit the report's summary to the repo on every model or prompt change. A pinned model plus a versioned eval summary is how you know a change was an improvement rather than noise.

## 6.4 The runaway agent problem

An agent "runs away" when the loop stops converging: repeated identical calls, oscillation between two states, ever-expanding plans, or a supervisor that keeps delegating. Left alone it burns money until something external stops it. The fix is layered — every layer independent of the model's judgement.

```
Layer 0   The model is asked nicely in the prompt         ← necessary, worthless alone
Layer 1   Step budget            for _ in range(N)         ← Ch.1
Layer 2   Turn budget per node   route(): turns > N        ← Ch.4
Layer 3   Token / cost budget    Usage.estimated_cost_usd  ← below
Layer 4   Loop detection         repeated (tool, args)     ← below
Layer 5   Wall-clock deadline    per run and per tool      ← Ch.2 timeouts + below
Layer 6   External kill switch   cancel by thread_id       ← ops
```

### The budget guard

Consolidate layers 1, 3, 4, 5 into one object checked once per iteration:

```python
# agentkit/guard.py
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any

from agentkit.config import settings
from agentkit.llm import Usage


class BudgetExceeded(RuntimeError):
    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason


@dataclass(slots=True)
class RunGuard:
    usage: Usage
    max_steps: int = settings.max_steps
    max_cost_usd: float = settings.max_cost_usd
    deadline_s: float = 120.0
    max_identical_calls: int = 2
    _started: float = field(default_factory=time.monotonic)
    _steps: int = 0
    _calls: dict[tuple[str, str], int] = field(default_factory=dict)

    def tick(self) -> None:
        """Call once per loop iteration, before the model call."""
        self._steps += 1
        if self._steps > self.max_steps:
            raise BudgetExceeded(f"steps>{self.max_steps}")
        if time.monotonic() - self._started > self.deadline_s:
            raise BudgetExceeded(f"deadline {self.deadline_s}s")
        if (c := self.usage.estimated_cost_usd(3.0, 15.0)) > self.max_cost_usd:
            raise BudgetExceeded(f"cost ${c:.2f}>${self.max_cost_usd}")

    def observe_call(self, name: str, args: dict[str, Any]) -> None:
        key = (name, str(sorted(args.items())))
        self._calls[key] = self._calls.get(key, 0) + 1
        if self._calls[key] > self.max_identical_calls:
            raise BudgetExceeded(f"loop: {name}{args} x{self._calls[key]}")
```

In `ReActAgent.run`, replace `for _ in range(settings.max_steps)` with `while True: guard.tick(); ...` and call `guard.observe_call` before each execution. In the LangGraph version, `tick()` lives in `agent_node` and `BudgetExceeded` routes to `halt`.

When a budget trips, **return a useful partial result**, not a stack trace: the last assistant text plus the reason. Users forgive "I ran out of budget after finding X and Y" far more than a 500.

### Loop detection beyond exact repeats

Exact-repeat detection misses paraphrased loops (`search("bond yields")` → `search("yields on bonds")`). Two cheap upgrades:

- **Normalise arguments** (lowercase, strip whitespace, sort lists) before keying.
- **Semantic near-duplicate**: embed the last *k* tool-call strings and trip if cosine similarity to any previous > 0.95. Costs one embedding call per step; worth it for search-heavy agents.

> **📝 Note — Degrade gracefully, and say so**
>
> The difference between an agent people trust and one they switch off is mostly what happens on the bad path. When a budget trips, the agent should hand back everything it established so far, name the reason it stopped, and suggest what the user could do next — narrow the question, raise the budget, try again later. Never hide a stop behind a generic "something went wrong", and never let the model *pretend* it finished: a confident, complete-sounding answer that was actually cut short is worse than an honest partial.
>
> Make the stopped state visible to your evals too. A run that ended on `BudgetExceeded` should score zero on outcome graders even if the partial answer happens to contain the right number, otherwise you will optimise towards agents that get lucky early and never learn to finish.

## 6.5 Debugging complex graphs

### Tracing

Set `LANGSMITH_TRACING=true` and every node, LLM call and tool call is captured with inputs, outputs, latency and tokens. If you can't use a hosted tracer, the `Trace` object you've carried since Chapter 1 is the same data; dump it as JSON per run and keep it for 30 days.

### Reading a bad trace: a checklist

1. **Where did the plan go wrong?** Find the first step whose *thought* is inconsistent with the observation before it. That's the real failure; everything after is symptom.
2. **Did a tool lie?** Check the tool output at that step. Truncated? Stale? Error swallowed as `ok=True`?
3. **Did context get crowded?** Look at message count and tokens at the failing step. Past ~60% of the window, quality drops before you see errors.
4. **Was the tool description ambiguous?** If the wrong tool was picked between two plausible ones, the fix is usually the description, not the prompt.
5. **Is it reproducible?** Run the same task 5 times. A 1/5 failure is a distribution problem (fix with structure — narrower tools, structured output). A 5/5 failure is a bug.

### Time-travel debugging

Because the graph checkpoints after every node, you can rewind to the step before the failure, edit state, and replay:

```python
history = list(graph.get_state_history(config))
bad = next(s for s in history if s.next == ("analyst",))          # the step you suspect
forked = graph.update_state(bad.config, {"messages": [("user", "Use lookup_security first.")]})
graph.invoke(None, forked)                                          # continue from the fork
```

This is the single most valuable debugging technique in the book. Combined with a fake model (Chapter 1, exercise 2) you can turn any production failure into a deterministic regression test: capture the trace, script the fake to replay the model's outputs, assert the guard trips where it should.

### Testing pyramid for agents

```
          ┌──────────────┐
          │  Eval suite  │   statistical, real model, nightly / on change
          ├──────────────┤
          │ Replay tests │   fake model scripted from real traces, CI
          ├──────────────┤
          │  Node tests  │   route(), reducers, guards — pure functions, ms
          ├──────────────┤
          │  Tool tests  │   validation, errors, timeouts — no LLM at all
          └──────────────┘
```

Most teams invert this and only run the top layer. Don't.

> **📝 Note — The pyramid in practice**
>
> The bottom two layers are where most of the value is and most of the neglect happens. Tool tests run in milliseconds, need no API key, and catch the majority of production incidents — a schema that let a bad value through, a timeout that wasn't applied, an error that was swallowed as success. Node tests are almost as cheap: `route()` is a pure function of state, and so is every guard.
>
> Replay tests are the layer that most teams have never built and that pays for itself fastest. Every real failure you investigate produces a trace; scripting a fake model from that trace turns a one-off investigation into a permanent regression test. Within a few months you will have a suite that encodes every way your agent has ever gone wrong, and it will run in CI in seconds.
>
> Reserve the top layer — real-model evals — for what only it can answer: is the agent, overall, getting better or worse.

## 6.6 Exercises

1. **Build the suite** above for your Chapter 2 agent with ≥10 tasks. Run `repeats=5`. Report mean scores and p95 steps.
2. **Break it deliberately.** Change one tool description to be vague. Re-run. Which graders caught it?
3. **Runaway.** Give the agent a tool that always returns "try again". Confirm `RunGuard` stops it, log the reason, and assert the partial answer is returned.
4. **Judge calibration.** Hand-label 20 answers 1–5. Compare to `LLMJudge`. Report agreement; adjust the rubric until Cohen's κ > 0.6.
5. **Replay test.** Take one real failing trace, script a `FakeChatModel` from it, and turn it into a pytest that fails before your fix and passes after.
6. *(Stretch)* Time-travel: reproduce a multi-agent failure from Chapter 4, fork at the supervisor's bad decision, and show the corrected run.

## 6.7 Checkpoint

You can quantify whether a change improved your agent; you have four independent stop conditions; and you can turn a production failure into a deterministic test in under an hour.

---

# Capstone — A Production-Shaped Agent

Choose a domain you know. Build an agent that meets every line of this checklist:

- [ ] ≥ 5 tools, all Pydantic-validated, at least one with a narrow identifier-only input
- [ ] Transient-failure handling invisible to the model; semantic failures visible to it
- [ ] Implemented as a LangGraph with a persistent checkpointer and a routed halt node
- [ ] At least one human approval gate on a side-effecting action
- [ ] Context control: bounded tool outputs + trimming or summarisation
- [ ] `RunGuard` with step, cost, loop and deadline limits, returning partial results
- [ ] Eval suite with ≥ 15 tasks, ≥ 3 grader types, `repeats ≥ 3`, summary committed to the repo
- [ ] One replay regression test built from a real trace
- [ ] A one-page architecture note: where the LLM decides, where code decides, and why

Present the eval summary before and after one deliberate improvement. If the numbers didn't move, say so — that is also a result.

---

# Appendix A — Glossary

- **Agent** — a loop in which a model chooses actions and code executes them.
- **Chain** — a fixed sequence of LLM calls with no model-driven control flow.
- **ReAct** — Reason + Act: interleaved reasoning and tool calls with observations fed back.
- **Tool / function calling** — the model emitting a structured request that your code executes.
- **Reducer** — a function defining how a state field merges updates (e.g. append vs replace).
- **Checkpointer** — persistence layer that saves graph state after each node.
- **Interrupt** — pausing a graph to await external input; resumed with a `Command`.
- **Trajectory** — the sequence of steps an agent took; evaluated separately from the outcome.
- **Runaway agent** — a loop that has stopped converging and will consume resources until stopped externally.

# Appendix B — Further reading

- Yao et al., *ReAct: Synergizing Reasoning and Acting in Language Models* (2022)
- Shinn et al., *Reflexion: Language Agents with Verbal Reinforcement Learning* (2023)
- Anthropic, *Building Effective Agents* and the tool-use documentation at docs.claude.com
- LangGraph documentation — concepts: state, persistence, human-in-the-loop, multi-agent
- Zheng et al., *Judging LLM-as-a-Judge* (2023) — on judge biases and calibration

# Appendix C — Instructor notes

- **Pacing.** Weeks 1–2 are the foundation; do not let students skip the fake-model tests in Week 1 — everything in Week 6 depends on them.
- **Cost.** With `max_cost_usd=0.50` and `repeats=3`, a full eval run on 15 tasks is under $25. Set organisational spend caps at the API key level anyway.
- **Assessment.** Weight the capstone 50%, weekly exercises 30%, and the Week 6 eval report 20%. Grade the architecture note on whether the LLM/code boundary is defensible, not on whether it matches this book.
