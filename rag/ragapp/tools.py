SEARCH_DOCUMENTS_TOOL = {
    "name": "search_documents",
    "description": (
        "Search the indexed document cache (PDF, DOCX, CSV/XLSX files) for passages "
        "relevant to a query. Returns the most relevant text chunks along with the "
        "source file name each one came from."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "Natural language search query"},
            "top_k": {
                "type": "integer",
                "description": "Number of chunks to return (default 5)",
            },
        },
        "required": ["query"],
        "additionalProperties": False,
    },
}

QUERY_DATABASE_TOOL = {
    "name": "query_database",
    "description": (
        "Run a single read-only SQL SELECT statement against the connected SQL "
        "database to fetch structured records. Only SELECT/WITH statements are "
        "permitted."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "sql": {"type": "string", "description": "A single read-only SQL SELECT statement"},
        },
        "required": ["sql"],
        "additionalProperties": False,
    },
}

ALL_TOOLS = [SEARCH_DOCUMENTS_TOOL, QUERY_DATABASE_TOOL]
