# PART 1: Download SQPs from cbseacademic.nic.in
$ErrorActionPreference = "Continue"
$baseDir = "D:\Study Papers\Jeffrey"

Write-Host "=== CBSE SQP & Additional Papers Downloader ===" -ForegroundColor Cyan

$sqpYears = @(
    @{ Year = "2025-26"; DirYear = "2025_26"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2025-26.html" },
    @{ Year = "2024-25"; DirYear = "2024_25"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2024-25.html" },
    @{ Year = "2023-24"; DirYear = "2023_24"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2023-24.html" },
    @{ Year = "2022-23"; DirYear = "2022_23"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2022-23.html" },
    @{ Year = "2021-22"; DirYear = "2021_22"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2021-22.html" },
    @{ Year = "2020-21"; DirYear = "2020_21"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2020-21.html" },
    @{ Year = "2019-20"; DirYear = "2019_20"; PageUrl = "https://cbseacademic.nic.in/SQP_CLASSXII_2019-20.html" }
)

$sqpBaseUrl = "https://cbseacademic.nic.in/"

foreach ($sqpYear in $sqpYears) {
    Write-Host ""
    Write-Host "--- SQP $($sqpYear.Year) ---" -ForegroundColor Yellow
    
    try {
        $response = Invoke-WebRequest -Uri $sqpYear.PageUrl -UseBasicParsing -TimeoutSec 30
        $html = $response.Content
        Write-Host "  Page fetched OK" -ForegroundColor Gray
    } catch {
        Write-Host "  SKIP - page not found" -ForegroundColor DarkGray
        continue
    }
    
    $pdfPattern = 'href="(web_material/SQP/[^"]+\.pdf)"'
    $pdfMatches = [regex]::Matches($html, $pdfPattern)
    Write-Host "  Found $($pdfMatches.Count) PDF links" -ForegroundColor Gray
    
    $dlCount = 0; $skipCount = 0; $failCount = 0
    
    foreach ($pdfMatch in $pdfMatches) {
        $relPath = $pdfMatch.Groups[1].Value
        $fullUrl = $sqpBaseUrl + $relPath
        $fileName = [System.IO.Path]::GetFileName($relPath)
        
        $subjectName = $fileName -replace '-SQP\.pdf$','' -replace '-MS\.pdf$','' -replace '\.pdf$',''
        $subjectName = $subjectName -replace '_',' ' -replace '-',' '
        $subjectName = (Get-Culture).TextInfo.ToTitleCase($subjectName.ToLower().Trim())
        
        $nameMap = @{
            "Maths"="Mathematics";"Phy"="Physics";"Chem"="Chemistry";"Bio"="Biology";
            "Eng Core"="English Core";"Eng Elective"="English Elective";
            "Comp Science"="Computer Science";"Ip"="Informatics Practices";
            "Bst"="Business Studies";"Pol Science"="Political Science";
            "Phy Edu"="Physical Education";"Eco"="Economics";"Acc"="Accountancy";
            "Cs"="Computer Science";"Hist"="History";"Geo"="Geography"
        }
        if ($nameMap.ContainsKey($subjectName)) { $subjectName = $nameMap[$subjectName] }
        
        $targetDir = Join-Path $baseDir $subjectName
        $sqpDir = Join-Path $targetDir "SQP $($sqpYear.Year)"
        if (-not (Test-Path $sqpDir)) { New-Item -ItemType Directory -Path $sqpDir -Force | Out-Null }
        
        $targetFile = Join-Path $sqpDir $fileName
        if (Test-Path $targetFile) { $skipCount++; continue }
        
        Write-Host "  $subjectName - $fileName..." -ForegroundColor White -NoNewline
        try {
            Invoke-WebRequest -Uri $fullUrl -OutFile $targetFile -UseBasicParsing -TimeoutSec 60
            Write-Host " OK" -ForegroundColor Green
            $dlCount++
        } catch {
            Write-Host " FAIL" -ForegroundColor Red
            $failCount++
            Remove-Item $targetFile -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Milliseconds 150
    }
    Write-Host "  Result: $dlCount downloaded, $skipCount skipped, $failCount failed" -ForegroundColor $(if ($failCount -gt 0){"Yellow"}else{"Green"})
}

# PART 2: Check CBSE main page for compartment + marking scheme sections
Write-Host ""
Write-Host "--- Scanning cbse.gov.in for Compartment + Marking Scheme papers ---" -ForegroundColor Yellow

$mainPageUrl = "https://www.cbse.gov.in/cbsenew/question-paper.html"
try {
    $response = Invoke-WebRequest -Uri $mainPageUrl -UseBasicParsing -TimeoutSec 60
    $html = $response.Content
    
    # Find ALL ZIP links (not just XII from question-paper path - also compartment)
    $allZipPattern = 'href="([^"]+XII[^"]*\.zip)"'
    $allZipMatches = [regex]::Matches($html, $allZipPattern)
    Write-Host "  Total XII ZIP links on page: $($allZipMatches.Count)" -ForegroundColor Gray
    
    # Find compartment-specific links
    $compZipPattern = 'href="([^"]*[Cc]ompartment[^"]*XII[^"]*\.zip)"'
    $compMatches = [regex]::Matches($html, $compZipPattern)
    
    # Also check for compartment in different URL patterns
    $compZipPattern2 = 'href="([^"]*XII[^"]*[Cc]ompartment[^"]*\.zip)"'
    $compMatches2 = [regex]::Matches($html, $compZipPattern2)
    
    $compZipPattern3 = 'href="(compartment[^"]*XII[^"]*\.zip)"'
    $compMatches3 = [regex]::Matches($html, $compZipPattern3)
    
    Write-Host "  Compartment links found: $($compMatches.Count + $compMatches2.Count + $compMatches3.Count)" -ForegroundColor Gray
    
    # Check for "Compartment" text in the page
    $compTextMatches = [regex]::Matches($html, '[Cc]ompartment')
    Write-Host "  'Compartment' mentions on page: $($compTextMatches.Count)" -ForegroundColor Gray
    
    # Extract surrounding context for compartment mentions
    foreach ($ctm in $compTextMatches) {
        $start = [Math]::Max(0, $ctm.Index - 100)
        $len = [Math]::Min(250, $html.Length - $start)
        $context = $html.Substring($start, $len) -replace '<[^>]+>','' -replace '\s+',' '
        Write-Host "    Context: ...${context}..." -ForegroundColor DarkGray
    }
    
    # Look for links NOT matching the standard question-paper/YEAR/XII pattern
    $nonStdLinks = @()
    foreach ($m in $allZipMatches) {
        $link = $m.Groups[1].Value
        if ($link -notmatch 'question-paper/\d{4}/XII/') {
            $nonStdLinks += $link
        }
    }
    if ($nonStdLinks.Count -gt 0) {
        Write-Host "  Non-standard XII ZIP links found:" -ForegroundColor Green
        foreach ($l in $nonStdLinks) {
            Write-Host "    $l" -ForegroundColor White
        }
    }
} catch {
    Write-Host "  ERROR fetching page" -ForegroundColor Red
}

# PART 3: Try marking scheme page
Write-Host ""
Write-Host "--- Checking Marking Scheme page ---" -ForegroundColor Yellow

$msUrls = @(
    "https://www.cbse.gov.in/cbsenew/marking-scheme.html",
    "https://www.cbse.gov.in/cbsenew/ms.html",
    "https://www.cbse.gov.in/cbsenew/Marking-Scheme.html"
)

foreach ($msUrl in $msUrls) {
    try {
        $response = Invoke-WebRequest -Uri $msUrl -UseBasicParsing -TimeoutSec 20
        Write-Host "  FOUND: $msUrl" -ForegroundColor Green
        $html = $response.Content
        
        $msZipPattern = 'href="([^"]*\.zip)"'
        $msMatches = [regex]::Matches($html, $msZipPattern)
        Write-Host "  ZIP links: $($msMatches.Count)" -ForegroundColor White
        
        # Download XII marking schemes
        $msBase = "https://www.cbse.gov.in/cbsenew/"
        foreach ($msMatch in $msMatches) {
            $link = $msMatch.Groups[1].Value
            if ($link -match 'XII') {
                $fullUrl = $msBase + $link
                if ($link -match '(\d{4})/XII/(.+)\.zip') {
                    $year = $Matches[1]
                    $subj = $Matches[2] -replace '^\d+_','' -replace '_',' '
                    $subj = (Get-Culture).TextInfo.ToTitleCase($subj.ToLower().Trim())
                    
                    $msDir = Join-Path (Join-Path $baseDir $subj) "Marking Scheme $year"
                    if (-not (Test-Path $msDir)) { New-Item -ItemType Directory -Path $msDir -Force | Out-Null }
                    
                    $existing = Get-ChildItem -Path $msDir -File -ErrorAction SilentlyContinue
                    if ($existing.Count -gt 0) { continue }
                    
                    $zipFile = Join-Path $msDir ([System.IO.Path]::GetFileName($link))
                    Write-Host "  Downloading MS: $subj ($year)..." -ForegroundColor White -NoNewline
                    try {
                        Invoke-WebRequest -Uri $fullUrl -OutFile $zipFile -UseBasicParsing -TimeoutSec 120
                        try { Expand-Archive -Path $zipFile -DestinationPath $msDir -Force; Remove-Item $zipFile -Force -EA SilentlyContinue } catch {}
                        Write-Host " OK" -ForegroundColor Green
                    } catch {
                        Write-Host " FAIL" -ForegroundColor Red
                        Remove-Item $zipFile -Force -EA SilentlyContinue
                    }
                    Start-Sleep -Milliseconds 200
                }
            }
        }
        break
    } catch {
        Write-Host "  Not found: $msUrl" -ForegroundColor DarkGray
    }
}

# PART 4: Probe older years via direct URL
Write-Host ""
Write-Host "--- Probing older years (2021-2015) ---" -ForegroundColor Yellow

$probeSubjects = @("Physics","Chemistry","Mathematics","Biology","ENGLISH_CORE","Accountancy","Economics")
$years = @(2021,2020,2019,2018,2017,2016,2015)
$cbseBase = "https://www.cbse.gov.in/cbsenew/"

foreach ($yr in $years) {
    $foundAny = $false
    foreach ($subj in $probeSubjects) {
        $testUrl = "${cbseBase}question-paper/${yr}/XII/${subj}.zip"
        try {
            $resp = Invoke-WebRequest -Uri $testUrl -Method Head -UseBasicParsing -TimeoutSec 8 -EA Stop
            Write-Host "  Year ${yr}: FOUND ($subj)" -ForegroundColor Green
            $foundAny = $true
            break
        } catch {}
    }
    if (-not $foundAny) {
        Write-Host "  Year ${yr}: Not available" -ForegroundColor DarkGray
    }
}

# PART 5: Check model answers
Write-Host ""
Write-Host "--- Checking Model Answer pages ---" -ForegroundColor Yellow
$modelUrls = @(
    "https://www.cbse.gov.in/cbsenew/model-answer.html",
    "https://cbseacademic.nic.in/modelAnswer.html",
    "https://cbseacademic.nic.in/model-answer.html"
)
foreach ($mu in $modelUrls) {
    try {
        $resp = Invoke-WebRequest -Uri $mu -UseBasicParsing -TimeoutSec 20
        Write-Host "  FOUND: $mu ($(($resp.Content.Length/1024).ToString('N0')) KB)" -ForegroundColor Green
    } catch {
        Write-Host "  Not found: $mu" -ForegroundColor DarkGray
    }
}

# FINAL SUMMARY
Write-Host ""
Write-Host "=== Current Archive Status ===" -ForegroundColor Cyan
$folders = Get-ChildItem -Path $baseDir -Directory -EA SilentlyContinue | Sort-Object Name
$totalFiles = 0; $totalSize = 0
foreach ($f in $folders) {
    $files = Get-ChildItem -Path $f.FullName -Recurse -File -EA SilentlyContinue
    $totalFiles += $files.Count
    $sz = ($files | Measure-Object -Property Length -Sum -EA SilentlyContinue).Sum
    if ($sz) { $totalSize += $sz }
}
Write-Host "  Subjects: $($folders.Count)" -ForegroundColor White
Write-Host "  Files: $totalFiles" -ForegroundColor White
Write-Host "  Size: $(($totalSize / 1MB).ToString('N1')) MB" -ForegroundColor White
Write-Host "=== Done ===" -ForegroundColor Cyan
