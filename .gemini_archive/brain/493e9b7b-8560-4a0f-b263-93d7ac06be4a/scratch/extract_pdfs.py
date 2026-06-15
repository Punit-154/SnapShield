from pypdf import PdfReader
import os

files = [
    (r"D:\Study Papers\Jeffrey\Chemistry\SQP 2025-26\Chemistry-SQP.pdf", "sqp.txt"),
    (r"D:\Study Papers\Jeffrey\Chemistry\SQP 2025-26\Chemistry-MS.pdf", "ms.txt"),
    (r"D:\Study Papers\Jeffrey\Chemistry\2026\Chemistry\56-1-1_Chemistry.pdf", "paper_2026.txt"),
    (r"D:\Study Papers\Jeffrey\Chemistry\2025\CHEMISTRY\56-1-1_Chemistry.pdf", "paper_2025.txt"),
    (r"D:\Study Papers\Jeffrey\Chemistry\2024\56-1-1 Chemistry.pdf" if os.path.exists(r"D:\Study Papers\Jeffrey\Chemistry\2024\56-1-1 Chemistry.pdf") else None, "paper_2024.txt"),
]

out_dir = r"C:\Users\joel0\.gemini\antigravity\brain\493e9b7b-8560-4a0f-b263-93d7ac06be4a\scratch"

for pdf_path, out_name in files:
    if pdf_path is None or not os.path.exists(pdf_path):
        # Try to find a file in the 2024 directory
        if "2024" in (out_name or ""):
            d = r"D:\Study Papers\Jeffrey\Chemistry\2024"
            if os.path.isdir(d):
                for f in os.listdir(d):
                    print(f"2024 dir content: {f}")
        print(f"Skipping {out_name} - file not found")
        continue
    try:
        reader = PdfReader(pdf_path)
        text = '\n'.join([page.extract_text() or '' for page in reader.pages])
        with open(os.path.join(out_dir, out_name), 'w', encoding='utf-8') as f:
            f.write(text)
        print(f"Extracted {out_name}: {len(text)} chars, {len(reader.pages)} pages")
    except Exception as e:
        print(f"Error with {pdf_path}: {e}")
