"""简历附件文本抽取脚本。

用法:
    python tools/parse_resume.py <input_path>

输入: PDF 或 .docx 文件绝对路径
输出: stdout JSON, 形如 {"text": "...", "pages": N, "format": "pdf|docx"}

依赖:
    pdfplumber  (PDF 文本抽取)
    python-docx (Word 段落与表格)

失败: 进程退出码非 0, stderr 输出原因。Java 端会自动回退到 Apache PDFBox / POI。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def extract_pdf(path: Path) -> tuple[str, int]:
    import pdfplumber  # type: ignore

    chunks: list[str] = []
    with pdfplumber.open(str(path)) as pdf:
        for page in pdf.pages:
            text = page.extract_text() or ""
            if text:
                chunks.append(text)
    return "\n".join(chunks), len(pdf.pages)


def extract_docx(path: Path) -> tuple[str, int]:
    from docx import Document  # type: ignore

    doc = Document(str(path))
    parts: list[str] = []
    for para in doc.paragraphs:
        if para.text:
            parts.append(para.text)
    for table in doc.tables:
        for row in table.rows:
            for cell in row.cells:
                if cell.text:
                    parts.append(cell.text)
    return "\n".join(parts), len(doc.paragraphs)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        sys.stderr.write("usage: parse_resume.py <file>\n")
        return 2

    path = Path(argv[1])
    if not path.exists() or not path.is_file():
        sys.stderr.write(f"file not found: {path}\n")
        return 3

    suffix = path.suffix.lower()
    try:
        if suffix == ".pdf":
            text, pages = extract_pdf(path)
            fmt = "pdf"
        elif suffix == ".docx":
            text, pages = extract_docx(path)
            fmt = "docx"
        else:
            sys.stderr.write(f"unsupported extension: {suffix}\n")
            return 4
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"extract failed: {type(e).__name__}: {e}\n")
        return 5

    payload = {"text": text, "pages": pages, "format": fmt}
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
