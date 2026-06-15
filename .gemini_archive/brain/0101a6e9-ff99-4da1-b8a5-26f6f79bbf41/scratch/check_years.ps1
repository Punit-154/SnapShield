$pageUrl = 'https://www.cbse.gov.in/cbsenew/question-paper.html'
$response = Invoke-WebRequest -Uri $pageUrl -UseBasicParsing -TimeoutSec 60
$html = $response.Content

# Find all year accordions
$yearPattern = 'Question Papers for Examination (\d{4})'
$yearMatches = [regex]::Matches($html, $yearPattern)
Write-Host 'Available years:'
foreach ($m in $yearMatches) { Write-Host "  $($m.Groups[1].Value)" }

# Find ALL XII ZIP links
$zipPattern = 'href="(question-paper/\d{4}/XII/[^"]+\.zip)"'
$zipMatches = [regex]::Matches($html, $zipPattern)
Write-Host ""
Write-Host "Total Class XII ZIP links: $($zipMatches.Count)"

# Group by year
$byYear = @{}
foreach ($m in $zipMatches) {
    if ($m.Groups[1].Value -match 'question-paper/(\d{4})/') {
        $year = $Matches[1]
        if (-not $byYear.ContainsKey($year)) { $byYear[$year] = 0 }
        $byYear[$year]++
    }
}
foreach ($key in ($byYear.Keys | Sort-Object)) {
    Write-Host "  Year ${key}: $($byYear[$key]) subjects"
}

# Also list unique subjects across all years
Write-Host ""
Write-Host "Unique subjects found:"
$subjects = @{}
foreach ($m in $zipMatches) {
    if ($m.Groups[1].Value -match 'question-paper/\d{4}/XII/(.+)\.zip') {
        $subj = $Matches[1]
        if (-not $subjects.ContainsKey($subj)) { $subjects[$subj] = 0 }
        $subjects[$subj]++
    }
}
foreach ($key in ($subjects.Keys | Sort-Object)) {
    Write-Host "  $key (${$subjects[$key]} years)"
}
