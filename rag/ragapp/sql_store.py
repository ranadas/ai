from __future__ import annotations

from sqlalchemy import create_engine, inspect, text

READ_ONLY_PREFIXES = ("select", "with")
FORBIDDEN_KEYWORDS = ("insert", "update", "delete", "drop", "alter", "create", "attach", "pragma")


class SQLStore:
    """Wraps a SQL connection. Defaults to an in-memory SQLite database, but any
    SQLAlchemy connection string (Postgres, MySQL, a real SQLite file, ...) works."""

    def __init__(self, connection_string: str = "sqlite:///:memory:"):
        self.connection_string = connection_string
        self.engine = create_engine(connection_string)

    def seed_demo_data(self) -> None:
        """Populates the in-memory database with sample business data so the app
        is usable out of the box. No-op is expected for a real external database."""
        with self.engine.begin() as conn:
            conn.execute(
                text(
                    """
                    CREATE TABLE customers (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        region TEXT NOT NULL,
                        signup_date TEXT NOT NULL
                    )
                    """
                )
            )
            conn.execute(
                text(
                    """
                    CREATE TABLE orders (
                        id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL,
                        product TEXT NOT NULL,
                        amount REAL NOT NULL,
                        order_date TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """
                )
            )

            customers = [
                (1, "Alice Chen", "alice@example.com", "APAC", "2024-01-15"),
                (2, "Bruno Silva", "bruno@example.com", "LATAM", "2024-03-02"),
                (3, "Carla Mensah", "carla@example.com", "EMEA", "2023-11-20"),
                (4, "Dev Patel", "dev@example.com", "APAC", "2024-06-10"),
            ]
            conn.execute(
                text("INSERT INTO customers VALUES (:id, :name, :email, :region, :signup_date)"),
                [dict(zip(("id", "name", "email", "region", "signup_date"), c)) for c in customers],
            )

            orders = [
                (1, 1, "Pro Plan", 299.0, "2024-02-01", "paid"),
                (2, 1, "Add-on Pack", 49.0, "2024-04-11", "paid"),
                (3, 2, "Starter Plan", 99.0, "2024-03-15", "paid"),
                (4, 3, "Pro Plan", 299.0, "2023-12-01", "refunded"),
                (5, 4, "Enterprise Plan", 999.0, "2024-07-01", "paid"),
                (6, 4, "Add-on Pack", 49.0, "2024-07-20", "pending"),
            ]
            conn.execute(
                text(
                    "INSERT INTO orders VALUES "
                    "(:id, :customer_id, :product, :amount, :order_date, :status)"
                ),
                [
                    dict(zip(("id", "customer_id", "product", "amount", "order_date", "status"), o))
                    for o in orders
                ],
            )

    def get_schema_description(self) -> str:
        inspector = inspect(self.engine)
        lines = []
        for table_name in inspector.get_table_names():
            columns = inspector.get_columns(table_name)
            col_desc = ", ".join(f"{c['name']} {c['type']}" for c in columns)
            lines.append(f"TABLE {table_name}({col_desc})")
        return "\n".join(lines) if lines else "(no tables found)"

    def run_query(self, sql: str, max_rows: int = 50) -> list[dict]:
        """Executes a read-only SELECT/WITH query. Rejects anything else - this
        is a safety net for LLM-generated SQL, not a substitute for a read-only
        database role in production."""
        stripped = sql.strip().rstrip(";").strip().lower()
        if not stripped.startswith(READ_ONLY_PREFIXES):
            raise ValueError("Only read-only SELECT/WITH queries are permitted.")
        if ";" in sql.strip().rstrip(";"):
            raise ValueError("Multiple statements are not permitted.")
        padded = f" {stripped} "
        if any(f" {kw} " in padded for kw in FORBIDDEN_KEYWORDS):
            raise ValueError("Only read-only queries are permitted.")

        with self.engine.connect() as conn:
            result = conn.execute(text(sql))
            rows = result.mappings().fetchmany(max_rows)
            return [dict(row) for row in rows]
