#!/usr/bin/env python3
"""A desktop Markdown editor with a live rendered preview.

Run with:  python3 markdown_editor.py [optional/path/to/file.md]
"""
from __future__ import annotations

import os
import re
import sys
from html import escape
from html.parser import HTMLParser
from urllib.parse import unquote, urlparse

os.environ.setdefault("TK_SILENCE_DEPRECATION", "1")

import tkinter as tk
from tkinter import ttk, filedialog, messagebox

import markdown
from tkhtmlview import HTMLScrolledText

APP_NAME = "Markdown Editor"
MD_EXTENSIONS = ["extra", "sane_lists", "toc", "nl2br", "codehilite"]
AUTOSAVE_DELAY_MS = 350  # debounce for re-rendering the preview

FILE_TYPES = [
    ("Markdown files", "*.md *.markdown *.mdown *.mkd"),
    ("Text files", "*.txt"),
    ("All files", "*.*"),
]

IMAGE_FILE_TYPES = [
    ("Image files", "*.png *.jpg *.jpeg *.gif *.bmp *.webp"),
    ("All files", "*.*"),
]

REMOTE_IMAGE_SCHEMES = {"http", "https", "ftp", "data"}


class ImageSourceResolver(HTMLParser):
    """Rewrite generated HTML image sources without requiring extra packages."""

    def __init__(self, resolve_src):
        super().__init__(convert_charrefs=False)
        self.resolve_src = resolve_src
        self.parts: list[str] = []
        self.changed = False

    def _rewrite_attrs(self, tag: str, attrs) -> list[tuple[str, str | None]]:
        if tag.lower() != "img":
            return attrs
        rewritten = []
        for name, value in attrs:
            if name.lower() == "src" and value:
                resolved = self.resolve_src(value)
                if resolved != value:
                    self.changed = True
                rewritten.append((name, resolved))
            else:
                rewritten.append((name, value))
        return rewritten

    def _format_attrs(self, attrs) -> str:
        rendered = []
        for name, value in attrs:
            if value is None:
                rendered.append(f" {name}")
            else:
                rendered.append(f' {name}="{escape(value, quote=True)}"')
        return "".join(rendered)

    def handle_starttag(self, tag, attrs) -> None:
        attrs = self._rewrite_attrs(tag, attrs)
        self.parts.append(f"<{tag}{self._format_attrs(attrs)}>")

    def handle_startendtag(self, tag, attrs) -> None:
        attrs = self._rewrite_attrs(tag, attrs)
        self.parts.append(f"<{tag}{self._format_attrs(attrs)} />")

    def handle_endtag(self, tag) -> None:
        self.parts.append(f"</{tag}>")

    def handle_data(self, data) -> None:
        self.parts.append(data)

    def handle_entityref(self, name) -> None:
        self.parts.append(f"&{name};")

    def handle_charref(self, name) -> None:
        self.parts.append(f"&#{name};")

    def handle_comment(self, data) -> None:
        self.parts.append(f"<!--{data}-->")

    def handle_decl(self, decl) -> None:
        self.parts.append(f"<!{decl}>")

    def handle_pi(self, data) -> None:
        self.parts.append(f"<?{data}>")

    def html(self) -> str:
        return "".join(self.parts)


class LineNumbers(tk.Canvas):
    """A canvas that mirrors line numbers next to a Text widget."""

    def __init__(self, master, text_widget: tk.Text, **kwargs):
        super().__init__(master, width=44, highlightthickness=0, **kwargs)
        self.text_widget = text_widget

    def redraw(self, *_args) -> None:
        self.delete("all")
        i = self.text_widget.index("@0,0")
        while True:
            dline_info = self.text_widget.dlineinfo(i)
            if dline_info is None:
                break
            y = dline_info[1]
            line_num = str(i).split(".")[0]
            self.create_text(
                38, y, anchor="ne", text=line_num,
                font=("Menlo", 10), fill="#8a8a8a",
            )
            i = self.text_widget.index(f"{i}+1line")


class MarkdownHighlighter:
    """Lightweight regex-based syntax highlighting for the editor pane."""

    PATTERNS = [
        ("heading", r"^#{1,6}\s.*$", {"foreground": "#1a73e8", "font": ("Menlo", 12, "bold")}),
        ("bold", r"(\*\*|__)(?!\s).+?(?<!\s)\1", {"font": ("Menlo", 12, "bold")}),
        ("italic", r"(?<!\*)\*(?!\*)(?!\s).+?(?<!\s)\*(?!\*)|(?<!_)_(?!_)(?!\s).+?(?<!\s)_(?!_)",
         {"font": ("Menlo", 12, "italic")}),
        ("inline_code", r"`[^`\n]+`", {"foreground": "#c2185b", "background": "#f5f5f5"}),
        ("code_fence", r"^```.*$", {"foreground": "#c2185b"}),
        ("link", r"\[[^\]]*\]\([^)]*\)", {"foreground": "#0b8043"}),
        ("blockquote", r"^>.*$", {"foreground": "#5f6368", "font": ("Menlo", 12, "italic")}),
        ("list_item", r"^\s*([-*+]|\d+\.)\s", {"foreground": "#e8710a"}),
        ("hr", r"^\s*(-{3,}|\*{3,}|_{3,})\s*$", {"foreground": "#9e9e9e"}),
    ]

    def __init__(self, text_widget: tk.Text):
        self.text_widget = text_widget
        for name, _pattern, opts in self.PATTERNS:
            self.text_widget.tag_configure(name, **opts)

    def highlight(self) -> None:
        content = self.text_widget.get("1.0", "end-1c")
        for name, _pattern, _opts in self.PATTERNS:
            self.text_widget.tag_remove(name, "1.0", "end")
        for name, pattern, _opts in self.PATTERNS:
            flags = re.MULTILINE if pattern.startswith("^") else 0
            for match in re.finditer(pattern, content, flags):
                start = f"1.0+{match.start()}c"
                end = f"1.0+{match.end()}c"
                self.text_widget.tag_add(name, start, end)


class MarkdownEditorApp(tk.Tk):
    def __init__(self, initial_file: str | None = None):
        super().__init__()
        self.title(APP_NAME)
        self.geometry("1300x820")
        self.minsize(800, 500)

        self.current_path: str | None = None
        self.modified = False
        self._render_job = None
        self._highlight_job = None

        self.md = markdown.Markdown(extensions=MD_EXTENSIONS, output_format="html5")

        self._build_menu()
        self._build_toolbar()
        self._build_body()
        self._build_statusbar()
        self._bind_shortcuts()

        if initial_file and os.path.isfile(initial_file):
            self._load_file(initial_file)
        else:
            self._update_title()
            self._schedule_render()

    def report_callback_exception(self, exc, val, tb) -> None:
        import traceback
        traceback.print_exception(exc, val, tb)
        messagebox.showerror(APP_NAME, f"An unexpected error occurred:\n{val}")

    # ---------------------------------------------------------------- UI --
    def _build_menu(self) -> None:
        menubar = tk.Menu(self)

        file_menu = tk.Menu(menubar, tearoff=False)
        file_menu.add_command(label="New", accelerator="Cmd/Ctrl+N", command=self.new_file)
        file_menu.add_command(label="Open...", accelerator="Cmd/Ctrl+O", command=self.open_file)
        file_menu.add_separator()
        file_menu.add_command(label="Save", accelerator="Cmd/Ctrl+S", command=self.save_file)
        file_menu.add_command(label="Save As...", accelerator="Shift+Cmd/Ctrl+S", command=self.save_file_as)
        file_menu.add_separator()
        file_menu.add_command(label="Export Rendered HTML...", command=self.export_html)
        file_menu.add_command(label="Export to Word (.docx)...", command=self.export_word)
        file_menu.add_command(label="Export to PDF...", command=self.export_pdf)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.on_close)
        menubar.add_cascade(label="File", menu=file_menu)

        edit_menu = tk.Menu(menubar, tearoff=False)
        edit_menu.add_command(label="Undo", accelerator="Cmd/Ctrl+Z", command=lambda: self.editor.edit_undo())
        edit_menu.add_command(label="Redo", accelerator="Shift+Cmd/Ctrl+Z", command=lambda: self.editor.edit_redo())
        edit_menu.add_separator()
        edit_menu.add_command(label="Cut", command=lambda: self.editor.event_generate("<<Cut>>"))
        edit_menu.add_command(label="Copy", command=lambda: self.editor.event_generate("<<Copy>>"))
        edit_menu.add_command(label="Paste", command=lambda: self.editor.event_generate("<<Paste>>"))
        menubar.add_cascade(label="Edit", menu=edit_menu)

        fmt_menu = tk.Menu(menubar, tearoff=False)
        fmt_menu.add_command(label="Bold", accelerator="Cmd/Ctrl+B", command=self.make_bold)
        fmt_menu.add_command(label="Italic", accelerator="Cmd/Ctrl+I", command=self.make_italic)
        fmt_menu.add_command(label="Inline Code", command=self.make_inline_code)
        fmt_menu.add_separator()
        for level in (1, 2, 3):
            fmt_menu.add_command(label=f"Heading {level}", command=lambda l=level: self.make_heading(l))
        fmt_menu.add_separator()
        fmt_menu.add_command(label="Bulleted List", command=self.make_bullet_list)
        fmt_menu.add_command(label="Numbered List", command=self.make_numbered_list)
        fmt_menu.add_command(label="Blockquote", command=self.make_blockquote)
        fmt_menu.add_command(label="Code Block", command=self.make_code_block)
        fmt_menu.add_command(label="Link", command=self.make_link)
        fmt_menu.add_command(label="Image", command=self.make_image)
        fmt_menu.add_command(label="Table", command=self.make_table)
        fmt_menu.add_command(label="Horizontal Rule", command=self.make_hr)
        menubar.add_cascade(label="Format", menu=fmt_menu)

        view_menu = tk.Menu(menubar, tearoff=False)
        view_menu.add_command(label="Refresh Preview", accelerator="Cmd/Ctrl+R", command=self.render_preview)
        menubar.add_cascade(label="View", menu=view_menu)

        help_menu = tk.Menu(menubar, tearoff=False)
        help_menu.add_command(label="About", command=self.show_about)
        menubar.add_cascade(label="Help", menu=help_menu)

        self.config(menu=menubar)

    def _build_toolbar(self) -> None:
        bar = ttk.Frame(self, padding=(6, 4))
        bar.pack(side="top", fill="x")

        buttons = [
            ("B", self.make_bold, "Bold (Cmd/Ctrl+B)"),
            ("i", self.make_italic, "Italic (Cmd/Ctrl+I)"),
            ("H1", lambda: self.make_heading(1), "Heading 1"),
            ("H2", lambda: self.make_heading(2), "Heading 2"),
            ("H3", lambda: self.make_heading(3), "Heading 3"),
            ("Code", self.make_inline_code, "Inline code"),
            ("Block", self.make_code_block, "Code block"),
            ("•List", self.make_bullet_list, "Bulleted list"),
            ("1.List", self.make_numbered_list, "Numbered list"),
            ("Quote", self.make_blockquote, "Blockquote"),
            ("Link", self.make_link, "Insert link"),
            ("Image", self.make_image, "Insert image"),
            ("Table", self.make_table, "Insert table"),
            ("HR", self.make_hr, "Horizontal rule"),
        ]
        for label, cmd, tip in buttons:
            b = ttk.Button(bar, text=label, width=6, command=cmd)
            b.pack(side="left", padx=2)

        ttk.Separator(bar, orient="vertical").pack(side="left", fill="y", padx=6)
        ttk.Button(bar, text="Refresh Preview", command=self.render_preview).pack(side="left", padx=2)

    def _build_body(self) -> None:
        paned = ttk.Panedwindow(self, orient="horizontal")
        paned.pack(side="top", fill="both", expand=True)

        # --- Editor pane ---
        editor_frame = ttk.Frame(paned)
        editor_header = ttk.Label(editor_frame, text="Markdown Source", padding=(6, 3), anchor="w")
        editor_header.pack(side="top", fill="x")

        text_container = ttk.Frame(editor_frame)
        text_container.pack(side="top", fill="both", expand=True)

        self.editor = tk.Text(
            text_container, wrap="word", undo=True, font=("Menlo", 12),
            padx=8, pady=8, borderwidth=0, highlightthickness=0,
        )
        self.linenumbers = LineNumbers(text_container, self.editor, background="#fafafa")
        v_scroll = ttk.Scrollbar(text_container, orient="vertical", command=self._on_editor_scroll)
        self.editor.configure(yscrollcommand=self._on_editor_yscroll)

        self.linenumbers.pack(side="left", fill="y")
        self.editor.pack(side="left", fill="both", expand=True)
        v_scroll.pack(side="right", fill="y")

        self.highlighter = MarkdownHighlighter(self.editor)

        paned.add(editor_frame, weight=1)

        # --- Preview pane ---
        preview_frame = ttk.Frame(paned)
        preview_header = ttk.Label(preview_frame, text="Preview", padding=(6, 3), anchor="w")
        preview_header.pack(side="top", fill="x")

        self.preview = HTMLScrolledText(preview_frame, html="", padx=10, pady=10)
        self.preview.pack(side="top", fill="both", expand=True)

        paned.add(preview_frame, weight=1)

        self.editor.bind("<<Modified>>", self._on_text_modified)
        self.editor.bind("<Configure>", self.linenumbers.redraw)
        self.editor.bind("<Tab>", self._on_tab)

    def _build_statusbar(self) -> None:
        bar = ttk.Frame(self, padding=(6, 2))
        bar.pack(side="bottom", fill="x")
        self.status_path = ttk.Label(bar, text="Untitled", anchor="w")
        self.status_path.pack(side="left")
        self.status_words = ttk.Label(bar, text="0 words · 0 chars", anchor="e")
        self.status_words.pack(side="right")

    def _bind_shortcuts(self) -> None:
        for mod in ("Command", "Control"):
            self.bind_all(f"<{mod}-n>", lambda e: self.new_file())
            self.bind_all(f"<{mod}-o>", lambda e: self.open_file())
            self.bind_all(f"<{mod}-s>", lambda e: self.save_file())
            self.bind_all(f"<{mod}-Shift-S>", lambda e: self.save_file_as())
            self.bind_all(f"<{mod}-b>", lambda e: self.make_bold())
            self.bind_all(f"<{mod}-i>", lambda e: self.make_italic())
            self.bind_all(f"<{mod}-r>", lambda e: self.render_preview())
            self.bind_all(f"<{mod}-q>", lambda e: self.on_close())
        self.protocol("WM_DELETE_WINDOW", self.on_close)

    # ------------------------------------------------------------ scroll --
    def _on_editor_scroll(self, *args) -> None:
        self.editor.yview(*args)
        self.linenumbers.redraw()

    def _on_editor_yscroll(self, first, last) -> None:
        self.linenumbers.redraw()

    def _on_tab(self, _event):
        self.editor.insert("insert", "    ")
        return "break"

    # ------------------------------------------------------- text events --
    def _on_text_modified(self, _event=None) -> None:
        if not self.editor.edit_modified():
            return
        self.modified = True
        self._update_title()
        self._update_wordcount()
        self._schedule_render()
        self._schedule_highlight()
        self.editor.edit_modified(False)

    def _schedule_render(self) -> None:
        if self._render_job is not None:
            self.after_cancel(self._render_job)
        self._render_job = self.after(AUTOSAVE_DELAY_MS, self.render_preview)

    def _schedule_highlight(self) -> None:
        if self._highlight_job is not None:
            self.after_cancel(self._highlight_job)
        self._highlight_job = self.after(150, self.highlighter.highlight)

    def render_preview(self) -> None:
        self._render_job = None
        source = self.editor.get("1.0", "end-1c")
        self.md.reset()
        try:
            html_body = self.md.convert(source)
            html_body = self._resolve_preview_image_sources(html_body)
        except Exception as exc:  # malformed input shouldn't crash the app
            html_body = f"<p><b>Render error:</b> {exc}</p>"
        if not html_body.strip():
            html_body = "<p style='color:#999'>(empty document)</p>"
        self.preview.set_html(html_body)

    def _update_wordcount(self) -> None:
        content = self.editor.get("1.0", "end-1c")
        words = len(content.split())
        chars = len(content)
        self.status_words.configure(text=f"{words} words · {chars} chars")

    def _update_title(self) -> None:
        name = os.path.basename(self.current_path) if self.current_path else "Untitled"
        star = "*" if self.modified else ""
        self.title(f"{star}{name} — {APP_NAME}")
        self.status_path.configure(text=self.current_path or "Untitled")

    # ----------------------------------------------------------- file io --
    def _confirm_discard_if_needed(self) -> bool:
        if not self.modified:
            return True
        choice = messagebox.askyesnocancel(
            APP_NAME, "You have unsaved changes. Save before continuing?"
        )
        if choice is None:
            return False
        if choice:
            return self.save_file()
        return True

    def new_file(self) -> None:
        if not self._confirm_discard_if_needed():
            return
        self.editor.delete("1.0", "end")
        self.current_path = None
        self.modified = False
        self._update_title()
        self._update_wordcount()
        self.render_preview()
        self.highlighter.highlight()

    def open_file(self) -> None:
        if not self._confirm_discard_if_needed():
            return
        path = filedialog.askopenfilename(title="Open Markdown file", filetypes=FILE_TYPES)
        if path:
            self._load_file(path)

    def _load_file(self, path: str) -> None:
        try:
            with open(path, "r", encoding="utf-8-sig") as fh:
                content = fh.read()
        except UnicodeDecodeError:
            try:
                with open(path, "r", encoding="cp1252", errors="replace") as fh:
                    content = fh.read()
            except OSError as exc:
                messagebox.showerror(APP_NAME, f"Could not open file:\n{exc}")
                return
            messagebox.showwarning(
                APP_NAME,
                "This file isn't UTF-8 encoded. It was opened as Windows-1252 "
                "and some characters may not display correctly.",
            )
        except OSError as exc:
            messagebox.showerror(APP_NAME, f"Could not open file:\n{exc}")
            return
        self.editor.delete("1.0", "end")
        self.editor.insert("1.0", content)
        self.editor.edit_reset()
        self.editor.edit_modified(False)
        self.current_path = path
        self.modified = False
        self._update_title()
        self._update_wordcount()
        self.render_preview()
        self.highlighter.highlight()

    def save_file(self) -> bool:
        if self.current_path is None:
            return self.save_file_as()
        return self._write_to(self.current_path)

    def save_file_as(self) -> bool:
        path = filedialog.asksaveasfilename(
            title="Save Markdown file", defaultextension=".md", filetypes=FILE_TYPES
        )
        if not path:
            return False
        return self._write_to(path)

    def _write_to(self, path: str) -> bool:
        try:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(self.editor.get("1.0", "end-1c"))
        except OSError as exc:
            messagebox.showerror(APP_NAME, f"Could not save file:\n{exc}")
            return False
        self.current_path = path
        self.modified = False
        self._update_title()
        return True

    def _document_base_dir(self) -> str:
        if self.current_path:
            return os.path.dirname(os.path.abspath(self.current_path))
        return os.getcwd()

    def _resolve_local_image_path(self, src: str) -> str:
        if src.startswith("//"):
            return src
        parsed = urlparse(src)
        if parsed.scheme in REMOTE_IMAGE_SCHEMES:
            return src
        if parsed.scheme == "file":
            return unquote(parsed.path)
        local_path = unquote(src)
        if os.path.isabs(local_path):
            return local_path
        return os.path.abspath(os.path.join(self._document_base_dir(), local_path))

    def _resolve_preview_image_sources(self, html_body: str) -> str:
        resolver = ImageSourceResolver(self._resolve_local_image_path)
        resolver.feed(html_body)
        resolver.close()
        return resolver.html() if resolver.changed else html_body

    def _render_html_document(self, body: str) -> str:
        doc_title = os.path.splitext(os.path.basename(self.current_path or "document"))[0]
        return (
            "<!DOCTYPE html>\n<html><head><meta charset='utf-8'>"
            f"<title>{doc_title}</title>\n"
            "<style>body{max-width:840px;margin:40px auto;font-family:-apple-system,"
            "Segoe UI,Helvetica,Arial,sans-serif;line-height:1.6;padding:0 16px;color:#1a1a1a}"
            "pre{background:#f5f5f5;padding:10px;overflow:auto;border-radius:4px}"
            "code{background:#f5f5f5;padding:2px 4px;border-radius:3px}"
            "blockquote{border-left:4px solid #ccc;margin:0;padding-left:16px;color:#555}"
            "table{border-collapse:collapse}"
            "th,td{border:1px solid #ccc;padding:4px 8px}"
            "img{max-width:100%}</style></head><body>\n" + body + "\n</body></html>"
        )

    def _convert_source_to_html(self) -> str | None:
        source = self.editor.get("1.0", "end-1c")
        self.md.reset()
        try:
            return self.md.convert(source)
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Could not render markdown:\n{exc}")
            return None

    def export_html(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Export rendered HTML", defaultextension=".html",
            filetypes=[("HTML files", "*.html"), ("All files", "*.*")],
        )
        if not path:
            return
        body = self._convert_source_to_html()
        if body is None:
            return
        html_doc = self._render_html_document(body)
        try:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(html_doc)
        except OSError as exc:
            messagebox.showerror(APP_NAME, f"Could not export HTML:\n{exc}")
            return
        messagebox.showinfo(APP_NAME, f"Exported rendered HTML to:\n{path}")

    def export_word(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Export to Word", defaultextension=".docx",
            filetypes=[("Word document", "*.docx"), ("All files", "*.*")],
        )
        if not path:
            return
        try:
            import docx
            from htmldocx import HtmlToDocx
        except ImportError:
            messagebox.showerror(
                APP_NAME,
                "Word export requires the 'python-docx' and 'htmldocx' packages.\n\n"
                "Install with:\n    pip install python-docx htmldocx",
            )
            return
        body = self._convert_source_to_html()
        if body is None:
            return
        try:
            document = docx.Document()
            HtmlToDocx().add_html_to_document(body, document)
            document.save(path)
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Could not export Word document:\n{exc}")
            return
        messagebox.showinfo(APP_NAME, f"Exported Word document to:\n{path}")

    def export_pdf(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Export to PDF", defaultextension=".pdf",
            filetypes=[("PDF files", "*.pdf"), ("All files", "*.*")],
        )
        if not path:
            return
        try:
            from xhtml2pdf import pisa
        except ImportError:
            messagebox.showerror(
                APP_NAME,
                "PDF export requires the 'xhtml2pdf' package.\n\n"
                "Install with:\n    pip install xhtml2pdf",
            )
            return
        body = self._convert_source_to_html()
        if body is None:
            return
        html_doc = self._render_html_document(body)
        try:
            with open(path, "wb") as fh:
                result = pisa.CreatePDF(html_doc, dest=fh)
        except Exception as exc:
            messagebox.showerror(APP_NAME, f"Could not export PDF:\n{exc}")
            return
        if result.err:
            messagebox.showwarning(
                APP_NAME, f"PDF exported with warnings ({result.err}) to:\n{path}"
            )
        else:
            messagebox.showinfo(APP_NAME, f"Exported PDF to:\n{path}")

    def on_close(self) -> None:
        if self._confirm_discard_if_needed():
            self.destroy()

    # --------------------------------------------------- format helpers --
    def _wrap_selection(self, prefix: str, suffix: str | None = None, placeholder: str = "text") -> None:
        suffix = prefix if suffix is None else suffix
        try:
            start = self.editor.index("sel.first")
            end = self.editor.index("sel.last")
            selected = self.editor.get(start, end)
            self.editor.delete(start, end)
            self.editor.insert(start, f"{prefix}{selected}{suffix}")
        except tk.TclError:
            pos = self.editor.index("insert")
            self.editor.insert(pos, f"{prefix}{placeholder}{suffix}")
        self.editor.focus_set()

    def _prefix_lines(self, prefix_fn) -> None:
        try:
            start_line = int(self.editor.index("sel.first").split(".")[0])
            end_line = int(self.editor.index("sel.last").split(".")[0])
        except tk.TclError:
            start_line = end_line = int(self.editor.index("insert").split(".")[0])
        for i, line_no in enumerate(range(start_line, end_line + 1)):
            idx = f"{line_no}.0"
            self.editor.insert(idx, prefix_fn(i))
        self.editor.focus_set()

    def make_bold(self) -> None:
        self._wrap_selection("**", placeholder="bold text")

    def make_italic(self) -> None:
        self._wrap_selection("*", placeholder="italic text")

    def make_inline_code(self) -> None:
        self._wrap_selection("`", placeholder="code")

    def make_heading(self, level: int) -> None:
        self._prefix_lines(lambda i: "#" * level + " ")

    def make_bullet_list(self) -> None:
        self._prefix_lines(lambda i: "- ")

    def make_numbered_list(self) -> None:
        self._prefix_lines(lambda i: f"{i + 1}. ")

    def make_blockquote(self) -> None:
        self._prefix_lines(lambda i: "> ")

    def make_code_block(self) -> None:
        try:
            start = self.editor.index("sel.first")
            end = self.editor.index("sel.last")
            selected = self.editor.get(start, end)
            self.editor.delete(start, end)
            self.editor.insert(start, f"```\n{selected}\n```")
        except tk.TclError:
            pos = self.editor.index("insert")
            self.editor.insert(pos, "```\ncode here\n```")
        self.editor.focus_set()

    def make_link(self) -> None:
        try:
            start = self.editor.index("sel.first")
            end = self.editor.index("sel.last")
            selected = self.editor.get(start, end)
            self.editor.delete(start, end)
            self.editor.insert(start, f"[{selected}](https://)")
        except tk.TclError:
            self.editor.insert("insert", "[link text](https://)")
        self.editor.focus_set()

    def make_image(self) -> None:
        path = filedialog.askopenfilename(title="Insert image", filetypes=IMAGE_FILE_TYPES)
        if not path:
            return
        if self.current_path:
            image_path = os.path.relpath(path, self._document_base_dir())
        else:
            image_path = path
        image_path = image_path.replace(os.sep, "/")
        alt_text = os.path.splitext(os.path.basename(path))[0] or "image"
        self.editor.insert("insert", f"![{alt_text}]({image_path})")
        self.editor.focus_set()

    def make_table(self) -> None:
        pos = self.editor.index("insert")
        line_start = f"{pos.split('.')[0]}.0"
        table = (
            "\n| Header 1 | Header 2 | Header 3 |\n"
            "| --- | --- | --- |\n"
            "| Cell 1 | Cell 2 | Cell 3 |\n"
            "| Cell 1 | Cell 2 | Cell 3 |\n\n"
        )
        self.editor.insert(line_start, table)
        self.editor.focus_set()

    def make_hr(self) -> None:
        pos = self.editor.index("insert")
        line_start = f"{pos.split('.')[0]}.0"
        self.editor.insert(line_start, "\n---\n")
        self.editor.focus_set()

    def show_about(self) -> None:
        messagebox.showinfo(
            APP_NAME,
            f"{APP_NAME}\n\nA lightweight Python/Tkinter Markdown editor "
            "with live preview.\nBuilt with python-markdown and tkhtmlview.",
        )


def main() -> None:
    initial_file = sys.argv[1] if len(sys.argv) > 1 else None
    app = MarkdownEditorApp(initial_file)
    app.mainloop()


if __name__ == "__main__":
    main()
