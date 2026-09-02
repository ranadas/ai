import argparse
from pathlib import Path

from ragapp import config
from ragapp.indexer import build_index
from ragapp.pipeline import RAGPipeline
from ragapp.sql_store import SQLStore


def _build_pipeline(docs_dir: Path) -> RAGPipeline:
    print(f"Indexing documents from {docs_dir} ...")
    store, raw_docs = build_index(docs_dir, config.CHUNK_SIZE, config.CHUNK_OVERLAP)
    print(f"Indexed {len(raw_docs)} document(s) into {store.size} chunk(s).")

    sql_store = SQLStore(config.SQL_CONNECTION_STRING)
    if config.SQL_CONNECTION_STRING.strip() == "sqlite:///:memory:":
        sql_store.seed_demo_data()
        print("Seeded in-memory demo SQL database (customers, orders).")

    return RAGPipeline(store, sql_store, config.MODEL)


def main():
    parser = argparse.ArgumentParser(description="RAG over a document cache + SQL database")
    parser.add_argument("--docs-dir", default=str(config.DOCS_DIR), help="Directory of source documents")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("index", help="Index the document cache and print a summary")

    ask_parser = sub.add_parser("ask", help="Ask a single question")
    ask_parser.add_argument("question")

    sub.add_parser("chat", help="Interactive Q&A session")

    args = parser.parse_args()
    docs_dir = Path(args.docs_dir)

    if args.command == "index":
        store, raw_docs = build_index(docs_dir, config.CHUNK_SIZE, config.CHUNK_OVERLAP)
        print(f"Indexed {len(raw_docs)} document(s) into {store.size} chunk(s).")
        for doc in raw_docs:
            print(f"  - {doc.source}")
        return

    pipeline = _build_pipeline(docs_dir)

    if args.command == "ask":
        print()
        print(pipeline.ask(args.question))
        return

    if args.command == "chat":
        print("\nRAG chat ready. Type 'exit' to quit.")
        while True:
            try:
                question = input("\n> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
            if question.lower() in {"exit", "quit"}:
                break
            if not question:
                continue
            print()
            print(pipeline.ask(question))


if __name__ == "__main__":
    main()
