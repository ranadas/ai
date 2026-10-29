from __future__ import annotations

import json

from openai import OpenAI

from .sql_store import SQLStore
from .tools import ALL_TOOLS
from .vector_store import InMemoryVectorStore

SYSTEM_PROMPT_TEMPLATE = """You are a retrieval-augmented assistant. Answer the user's \
question using only information you obtain from the `search_documents` and \
`query_database` tools, plus ordinary reasoning over that information - never invent \
facts. Call whichever tools are relevant, possibly more than once and in combination, \
before writing your final answer. When you use retrieved information, mention the \
source file name(s) and/or note that a figure came from the database. If neither \
source has the answer, say so plainly instead of guessing.

SQL database schema available via query_database:
{schema}
"""


class RAGPipeline:
    def __init__(
        self,
        vector_store: InMemoryVectorStore,
        sql_store: SQLStore,
        model: str,
        client: OpenAI | None = None,
    ):
        self.vector_store = vector_store
        self.sql_store = sql_store
        self.model = model
        self.client = client or OpenAI()

    def _dispatch_tool(self, name: str, tool_input: dict) -> str:
        if name == "search_documents":
            top_k = tool_input.get("top_k") or 5
            results = self.vector_store.search(tool_input["query"], top_k)
            if not results:
                return "No matching document passages found."
            return "\n\n".join(f"[{chunk.source}] {chunk.text}" for chunk, _ in results)

        if name == "query_database":
            try:
                rows = self.sql_store.run_query(tool_input["sql"])
            except Exception as exc:
                return f"Query failed: {exc}"
            return json.dumps(rows, default=str)

        return f"Unknown tool: {name}"

    def ask(self, question: str, max_turns: int = 6) -> str:
        system = SYSTEM_PROMPT_TEMPLATE.format(schema=self.sql_store.get_schema_description())
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": question},
        ]

        for _ in range(max_turns):
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                tools=ALL_TOOLS,
            )
            message = response.choices[0].message

            if response.choices[0].finish_reason != "tool_calls" or not message.tool_calls:
                return message.content or ""

            messages.append(
                {
                    "role": "assistant",
                    "content": message.content,
                    "tool_calls": [
                        {
                            "id": tc.id,
                            "type": "function",
                            "function": {
                                "name": tc.function.name,
                                "arguments": tc.function.arguments,
                            },
                        }
                        for tc in message.tool_calls
                    ],
                }
            )

            for tool_call in message.tool_calls:
                args = json.loads(tool_call.function.arguments)
                output = self._dispatch_tool(tool_call.function.name, args)
                messages.append(
                    {"role": "tool", "tool_call_id": tool_call.id, "content": output}
                )

        return "Reached the maximum number of tool-use turns without a final answer."
