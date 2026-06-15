$baseDir = 'D:\Study Papers\Jeffrey'
$subjects = @('Physics', 'Chemistry', 'Mathematics', 'English Core')
foreach ($subj in $subjects) {
    $dir = Join-Path $baseDir $subj
    Write-Host "=== $subj ===" -ForegroundColor Cyan
    if (Test-Path $dir) {
        $years = Get-ChildItem $dir -Directory | Sort-Object Name
        foreach ($yr in $years) {
            $files = Get-ChildItem $yr.FullName -Recurse -File
            $pdfs = $files | Where-Object { $_.Extension -eq '.pdf' }
            Write-Host "  $($yr.Name): $($pdfs.Count) PDFs"
            foreach ($pdf in ($pdfs | Select-Object -First 5)) {
                Write-Host "    - $($pdf.Name)" -ForegroundColor Gray
            }
            if ($pdfs.Count -gt 5) { Write-Host "    ... and $($pdfs.Count - 5) more" -ForegroundColor DarkGray }
        }
    } else {
        Write-Host '  NOT FOUND' -ForegroundColor Red
    }
    Write-Host ''
}
