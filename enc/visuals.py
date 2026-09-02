"""
MS Forms Pulse Survey Analyser
==============================

Reads an MS Forms CSV export and generates:
    output/
        charts/
            Q01_question_name.png
            Q02_question_name.png
            ...
        open_text/
            Q05_comments.csv
        summary.csv

Install:
    pip install pandas matplotlib

Run:
    python pulse_survey.py survey_results.csv

Optional:
    python pulse_survey.py survey_results.csv --output pulse_results
"""

from __future__ import annotations

import argparse
import re
import textwrap
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


# ----------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------

# MS Forms metadata fields that usually shouldn't be charted.
IGNORE_COLUMNS = {
    "ID",
    "Start time",
    "Completion time",
    "Email",
    "Name",
    "Last modified time",
}

# Delimiters commonly used when multiple answers are stored in one cell.
MULTI_SELECT_DELIMITERS = [";", ","]

# A column with this many or fewer unique values may be categorical.
MAX_CATEGORICAL_VALUES = 15

# Long strings / high-cardinality fields are treated as open text.
OPEN_TEXT_AVG_LENGTH = 35
OPEN_TEXT_UNIQUE_RATIO = 0.60

# Figure settings.
FIGURE_DPI = 180


# ----------------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------------

def clean_filename(value: str, max_length: int = 80) -> str:
    """Convert a question into a filesystem-safe filename."""
    value = re.sub(r"[^\w\s-]", "", str(value))
    value = re.sub(r"\s+", "_", value.strip())
    return value[:max_length] or "question"


def wrap_label(value: str, width: int = 45) -> str:
    """Wrap long chart labels."""
    return "\n".join(
        textwrap.wrap(str(value), width=width)
    )


def clean_series(series: pd.Series) -> pd.Series:
    """Remove blanks and normalise strings."""
    series = series.dropna().astype(str).str.strip()

    blanks = {"", "nan", "none", "n/a", "na", "null"}
    return series[~series.str.lower().isin(blanks)]


# ----------------------------------------------------------------------
# Detect question type
# ----------------------------------------------------------------------

def detect_question_type(series: pd.Series) -> str:
    """
    Guess question type.

    Returns:
        numeric
        single_select
        multi_select
        open_text
        empty
    """

    cleaned = clean_series(series)

    if cleaned.empty:
        return "empty"

    # Try numeric first.
    numeric = pd.to_numeric(cleaned, errors="coerce")

    if numeric.notna().mean() >= 0.95:
        unique_numeric = numeric.nunique()

        # Ratings such as 1-5 or 1-10 still work well as categorical charts.
        if unique_numeric <= 10:
            return "single_select"

        return "numeric"

    unique_count = cleaned.nunique()
    unique_ratio = unique_count / len(cleaned)
    avg_length = cleaned.str.len().mean()

    # Detect multi-select:
    # if many cells contain delimiters AND splitting produces repeated options.
    for delimiter in MULTI_SELECT_DELIMITERS:
        cells_with_delimiter = cleaned.str.contains(
            re.escape(delimiter),
            regex=True,
        ).mean()

        if cells_with_delimiter >= 0.15:
            exploded = (
                cleaned.str.split(delimiter)
                .explode()
                .astype(str)
                .str.strip()
            )

            exploded = exploded[exploded != ""]

            if exploded.nunique() < len(cleaned) * 1.5:
                return "multi_select"

    # Open-ended responses usually have many unique values and longer text.
    if (
        unique_ratio >= OPEN_TEXT_UNIQUE_RATIO
        and avg_length >= OPEN_TEXT_AVG_LENGTH
    ):
        return "open_text"

    if unique_count <= MAX_CATEGORICAL_VALUES:
        return "single_select"

    return "open_text"


# ----------------------------------------------------------------------
# Chart functions
# ----------------------------------------------------------------------

def save_single_select_chart(
    series: pd.Series,
    question: str,
    output_path: Path,
):
    cleaned = clean_series(series)

    counts = cleaned.value_counts()
    total = counts.sum()

    if counts.empty:
        return None

    percentages = counts / total * 100

    # Reverse so largest appears at the top in horizontal bar charts.
    counts = counts.sort_values()
    percentages = percentages.loc[counts.index]

    height = max(4.5, len(counts) * 0.55)

    fig, ax = plt.subplots(figsize=(10, height))

    bars = ax.barh(
        [wrap_label(x) for x in counts.index],
        percentages.values,
    )

    ax.set_title(
        wrap_label(question, 70),
        fontsize=14,
        fontweight="bold",
        pad=16,
    )

    ax.set_xlabel("Percentage of responses")
    ax.set_xlim(0, max(100, percentages.max() * 1.15))

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    for bar, pct, count in zip(
        bars,
        percentages.values,
        counts.values,
    ):
        ax.text(
            bar.get_width() + 1,
            bar.get_y() + bar.get_height() / 2,
            f"{pct:.1f}%  (n={count})",
            va="center",
            fontsize=9,
        )

    ax.text(
        0,
        -0.12,
        f"Responses: {total}",
        transform=ax.transAxes,
        fontsize=9,
    )

    fig.tight_layout()
    fig.savefig(output_path, dpi=FIGURE_DPI, bbox_inches="tight")
    plt.close(fig)

    top_answer = counts.idxmax()

    return {
        "question": question,
        "type": "single_select",
        "responses": int(total),
        "top_response": str(top_answer),
        "top_response_pct": round(
            percentages.loc[top_answer],
            1,
        ),
    }


def determine_multi_delimiter(series: pd.Series) -> str:
    cleaned = clean_series(series)

    delimiter_scores = {
        delimiter: cleaned.str.contains(
            re.escape(delimiter),
            regex=True,
        ).sum()
        for delimiter in MULTI_SELECT_DELIMITERS
    }

    return max(delimiter_scores, key=delimiter_scores.get)


def save_multi_select_chart(
    series: pd.Series,
    question: str,
    output_path: Path,
):
    cleaned = clean_series(series)

    if cleaned.empty:
        return None

    respondent_count = len(cleaned)

    delimiter = determine_multi_delimiter(cleaned)

    answers = (
        cleaned.str.split(delimiter)
        .explode()
        .astype(str)
        .str.strip()
    )

    answers = answers[answers != ""]

    counts = answers.value_counts()

    if counts.empty:
        return None

    percentages = counts / respondent_count * 100

    percentages = percentages.sort_values()
    counts = counts.loc[percentages.index]

    height = max(4.5, len(percentages) * 0.55)

    fig, ax = plt.subplots(figsize=(10, height))

    bars = ax.barh(
        [wrap_label(x) for x in percentages.index],
        percentages.values,
    )

    ax.set_title(
        wrap_label(question, 70),
        fontsize=14,
        fontweight="bold",
        pad=16,
    )

    ax.set_xlabel("Percentage of respondents selecting option")

    # Multi-select percentages can conceptually reach 100 for each option.
    ax.set_xlim(0, max(100, percentages.max() * 1.15))

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    for bar, pct, count in zip(
        bars,
        percentages.values,
        counts.values,
    ):
        ax.text(
            bar.get_width() + 1,
            bar.get_y() + bar.get_height() / 2,
            f"{pct:.1f}%  (n={count})",
            va="center",
            fontsize=9,
        )

    ax.text(
        0,
        -0.12,
        (
            f"Respondents: {respondent_count} · "
            "Multiple selections allowed"
        ),
        transform=ax.transAxes,
        fontsize=9,
    )

    fig.tight_layout()
    fig.savefig(output_path, dpi=FIGURE_DPI, bbox_inches="tight")
    plt.close(fig)

    top_answer = percentages.idxmax()

    return {
        "question": question,
        "type": "multi_select",
        "responses": respondent_count,
        "top_response": str(top_answer),
        "top_response_pct": round(
            percentages.loc[top_answer],
            1,
        ),
    }


def save_numeric_chart(
    series: pd.Series,
    question: str,
    output_path: Path,
):
    values = pd.to_numeric(series, errors="coerce").dropna()

    if values.empty:
        return None

    fig, ax = plt.subplots(figsize=(10, 5.5))

    ax.hist(values, bins="auto")

    ax.set_title(
        wrap_label(question, 70),
        fontsize=14,
        fontweight="bold",
        pad=16,
    )

    ax.set_xlabel("Response")
    ax.set_ylabel("Number of respondents")

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    mean = values.mean()
    median = values.median()

    ax.text(
        0.98,
        0.95,
        f"Mean: {mean:.2f}\nMedian: {median:.2f}\nn={len(values)}",
        transform=ax.transAxes,
        ha="right",
        va="top",
        fontsize=10,
    )

    fig.tight_layout()
    fig.savefig(output_path, dpi=FIGURE_DPI, bbox_inches="tight")
    plt.close(fig)

    return {
        "question": question,
        "type": "numeric",
        "responses": len(values),
        "mean": round(mean, 2),
        "median": round(median, 2),
    }


# ----------------------------------------------------------------------
# Open comments
# ----------------------------------------------------------------------

def export_open_text(
    series: pd.Series,
    question: str,
    output_path: Path,
):
    cleaned = clean_series(series)

    if cleaned.empty:
        return None

    comments = pd.DataFrame(
        {
            "response_number": range(1, len(cleaned) + 1),
            "comment": cleaned.values,
        }
    )

    comments.to_csv(output_path, index=False)

    return {
        "question": question,
        "type": "open_text",
        "responses": len(comments),
    }


# ----------------------------------------------------------------------
# Main analyser
# ----------------------------------------------------------------------

def analyse_survey(csv_path: Path, output_dir: Path):
    output_dir.mkdir(parents=True, exist_ok=True)

    charts_dir = output_dir / "charts"
    comments_dir = output_dir / "open_text"

    charts_dir.mkdir(exist_ok=True)
    comments_dir.mkdir(exist_ok=True)

    # utf-8-sig handles the BOM frequently included in Microsoft exports.
    try:
        df = pd.read_csv(csv_path, encoding="utf-8-sig")
    except UnicodeDecodeError:
        df = pd.read_csv(csv_path, encoding="latin-1")

    print(f"\nLoaded {len(df)} survey responses.")
    print(f"Found {len(df.columns)} columns.\n")

    summary_rows = []

    question_number = 0

    for column in df.columns:

        if column.strip() in IGNORE_COLUMNS:
            print(f"Skipping metadata: {column}")
            continue

        question_number += 1

        question_type = detect_question_type(df[column])

        print(
            f"Q{question_number:02d} "
            f"[{question_type}] "
            f"{column}"
        )

        safe_name = clean_filename(column)

        prefix = f"Q{question_number:02d}_{safe_name}"

        result = None

        if question_type == "single_select":
            result = save_single_select_chart(
                df[column],
                column,
                charts_dir / f"{prefix}.png",
            )

        elif question_type == "multi_select":
            result = save_multi_select_chart(
                df[column],
                column,
                charts_dir / f"{prefix}.png",
            )

        elif question_type == "numeric":
            result = save_numeric_chart(
                df[column],
                column,
                charts_dir / f"{prefix}.png",
            )

        elif question_type == "open_text":
            result = export_open_text(
                df[column],
                column,
                comments_dir / f"{prefix}.csv",
            )

        if result:
            result["question_number"] = question_number
            summary_rows.append(result)

    summary = pd.DataFrame(summary_rows)

    # Put question number first.
    if not summary.empty:
        columns = ["question_number"] + [
            c for c in summary.columns
            if c != "question_number"
        ]

        summary = summary[columns]

    summary.to_csv(
        output_dir / "summary.csv",
        index=False,
    )

    print("\nAnalysis complete.")
    print(f"Output folder: {output_dir.resolve()}")
    print(f"Charts:        {charts_dir.resolve()}")
    print(f"Comments:      {comments_dir.resolve()}")
    print(f"Summary:       {(output_dir / 'summary.csv').resolve()}")


# ----------------------------------------------------------------------
# Command line
# ----------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Analyse an MS Forms pulse survey CSV."
    )

    parser.add_argument(
        "csv",
        type=Path,
        help="Path to the MS Forms CSV export.",
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=Path("survey_output"),
        help="Output directory. Default: survey_output",
    )

    args = parser.parse_args()

    if not args.csv.exists():
        raise FileNotFoundError(
            f"CSV file not found: {args.csv}"
        )

    analyse_survey(args.csv, args.output)


if __name__ == "__main__":
    main()
