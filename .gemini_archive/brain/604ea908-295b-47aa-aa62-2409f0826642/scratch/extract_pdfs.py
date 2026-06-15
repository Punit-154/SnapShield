import PyPDF2
import sys
import os

files = [
    r"D:\Study Papers\Jeffrey\Mathematics\SQP 2025-26\Maths-SQP.pdf",
    r"D:\Study Papers\Jeffrey\Mathematics\SQP 2025-26\Maths-MS.pdf",
    r"D:\Study Papers\Jeffrey\Mathematics\2026\2413-1_65-1-1_Mathematics.pdf",
    r"D:\Study Papers\Jeffrey\Mathematics\2025\65-1-1_Mathematics.pdf",
]

outdir = r"C:\Users\joel0\.gemini\antigravity\brain\604ea908-295b-47aa-aa62-2409f0826642\scratch"

for fpath in files:
    if not os.path.exists(fpath):
        print(f"MISSING: {fpath}")
        continue
    basename = os.path.splitext(os.path.basename(fpath))[0] + ".txt"
    outpath = os.path.join(outdir, basename)
    try:
        reader = PyPDF2.PdfReader(fpath)
        text = ""
        for page in reader.pages:
            text += page.extract_text() + "\n\n---PAGE BREAK---\n\n"
        with open(outpath, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"OK: {fpath} -> {outpath} ({len(reader.pages)} pages)")
    except Exception as e:
        print(f"ERROR: {fpath} -> {e}")
