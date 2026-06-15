import fitz
import os

scratch = r'C:\Users\joel0\.gemini\antigravity\brain\e2fcc499-cd3a-4c35-8173-0bab0c842bdd\scratch'

files = {
    'sqp.txt': r'D:\Study Papers\Jeffrey\Mathematics\SQP 2025-26\Maths-SQP.pdf',
    'ms.txt': r'D:\Study Papers\Jeffrey\Mathematics\SQP 2025-26\Maths-MS.pdf',
    'paper_2026.txt': r'D:\Study Papers\Jeffrey\Mathematics\2026\2413-1_65-1-1_Mathematics.pdf',
    'paper_2025.txt': r'D:\Study Papers\Jeffrey\Mathematics\2025\65-1-1_Mathematics.pdf',
}

for outname, pdf_path in files.items():
    try:
        doc = fitz.open(pdf_path)
        text = ''
        for page in doc:
            text += page.get_text()
        outpath = os.path.join(scratch, outname)
        with open(outpath, 'w', encoding='utf-8') as f:
            f.write(text)
        print(f'OK: {outname} - {len(text)} chars, {len(doc)} pages')
    except Exception as e:
        print(f'ERROR: {outname} - {e}')
