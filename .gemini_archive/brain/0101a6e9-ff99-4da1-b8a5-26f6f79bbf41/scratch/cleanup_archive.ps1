# Comprehensive Cleanup and Consolidation of CBSE PYQ Archive
# Merges ALL variant SQP folder names into canonical subject names

$ErrorActionPreference = "Continue"
$baseDir = "D:\Study Papers\Jeffrey"

Write-Host "=== CBSE Archive Cleanup & Consolidation ===" -ForegroundColor Cyan

# Complete map of ALL variant names to canonical names (from batch outputs)
$folderMergeMap = @{
    # SQP naming variants -> Canonical
    "Acc" = "Accountancy"
    "Bharatnatyam" = "Bharatanatyam Dance"
    "Bharatnatyam Ms Hi" = "Bharatanatyam Dance"
    "Bharatnatyam Sqp Hi" = "Bharatanatyam Dance"
    "Biology Ms Hi" = "Biology"
    "Biology Sqp Hi" = "Biology"
    "Biotechnology Ms Hi" = "Biotechnology"
    "Biotechnology Sqp Hi" = "Biotechnology"
    "Businessstudies" = "Business Studies"
    "Carnaticmusicvocal" = "Carnatic Music (Vocal)"
    "Carnaticmusicmelodicinstrument" = "Carnatic Music (Melodic Instruments)"
    "Carnaticmusicpercussion" = "Carnatic Music (Percussion Instruments)"
    "Chemistry Ms Hi" = "Chemistry"
    "Chemistry Sqp Hi" = "Chemistry"
    "Computerscience" = "Computer Science"
    "Engggraphics" = "Engineering Graphics"
    "Englishcore" = "English Core"
    "Englishelective" = "English Elective"
    "Geography Ms Hi" = "Geography"
    "Geography Sqp Hi" = "Geography"
    "Graphic" = "Graphics (Fine Art)"
    "Graphic Ms Hi" = "Graphics (Fine Art)"
    "Graphic Sqp Hi" = "Graphics (Fine Art)"
    "Hindicore" = "Hindi Core"
    "Hindielective" = "Hindi Elective"
    "Hindustanivocal" = "Hindustani Music (Vocal)"
    "Hindustanimelodic" = "Hindustani Music (Melodic Instruments)"
    "Hindustanipercussion" = "Hindustani Music (Percussion Instruments)"
    "History Ms Hi" = "History"
    "History Sqp Hi" = "History"
    "Homescience" = "Home Science"
    "Homescience Ms Hi" = "Home Science"
    "Homescience Sqp Hi" = "Home Science"
    "Informaticspractices" = "Informatics Practices"
    "Kathak" = "Kathak Dance"
    "Kathak Ms Hi" = "Kathak Dance"
    "Kathak Sqp Hi" = "Kathak Dance"
    "Kathakali" = "Kathakali Dance"
    "Kathakali Ms Hi" = "Kathakali Dance"
    "Kathakali Sqp Hi" = "Kathakali Dance"
    "Kokborok Ms" = "Kokborok"
    "Kokborok Sqp" = "Kokborok"
    "Ktpi" = "Knowledge Traditions and Practices of India"
    "Kuchipudi" = "Kuchipudi Dance"
    "Kuchipudi Ms Hi" = "Kuchipudi Dance"
    "Kuchipudi Sqp Hi" = "Kuchipudi Dance"
    "Legalstudies" = "Legal Studies"
    "Legalstudies Ms Hi" = "Legal Studies"
    "Legalstudies Sqp Hi" = "Legal Studies"
    "Library" = "Library and Information Science"
    "Manipuridance" = "Manipuri Dance"
    "Manipuridance Ms Hi" = "Manipuri Dance"
    "Manipuridance Sqp Hi" = "Manipuri Dance"
    "Maths Ms Hi" = "Mathematics"
    "Maths Sqp Hi" = "Mathematics"
    "Ncc" = "National Cadet Corps (NCC)"
    "Odissi" = "Odissi Dance"
    "Odissi Ms Hi" = "Odissi Dance"
    "Odissi Sqp Hi" = "Odissi Dance"
    "Painting Ms Hi" = "Painting"
    "Painting Sqp Hi" = "Painting"
    "Physcaleducation" = "Physical Education"
    "Physicaleducation" = "Physical Education"
    "Physics Ms Hi" = "Physics"
    "Physics Sqp Hi" = "Physics"
    "Polsci" = "Political Science"
    "Polsci Ms Hi" = "Political Science"
    "Polsci Sqp Hi" = "Political Science"
    "Sanskritcore" = "Sanskrit Core"
    "Sanskrit Core Ms Core" = "Sanskrit Core"
    "Sanskrit Core Sqp Core" = "Sanskrit Core"
    "Sanskritelective" = "Sanskrit Elective"
    "Sculpture Ms Hi" = "Sculpture"
    "Sculpture Sqp Hi" = "Sculpture"
    "Sociology Ms Hi" = "Sociology"
    "Sociology Sqp Hi" = "Sociology"
    "Tangkhulmil" = "Tangkhul"
    "Teluguap" = "Telugu"
    "Teluguandhra" = "Telugu"
    "Telugutl" = "Telugu (Telangana)"
    "Telugutelangana" = "Telugu (Telangana)"
    "Urducore" = "Urdu Core"
    "Urduelective" = "Urdu Elective"
    "Historyhindi" = "History"
    "Nepalese" = "Nepali"
    "Scluputre" = "Sculpture"
    "Ktpi Hi" = "Knowledge Traditions and Practices of India"
    "Polscihi" = "Political Science"
    "Khuchipudi" = "Kuchipudi Dance"
    "Manipuridance Ms Term2" = "Manipuri Dance"
    "Manipuridance Sqp Term2" = "Manipuri Dance"
    "Odissi Ms Term2" = "Odissi Dance"
    "Odissi Sqp Term2" = "Odissi Dance"
    
    # Empty/broken folders to delete
    "Hrgs" = "__DELETE__"
    "Multimedia Webtech Ms 2018 19" = "__DELETE__"
    "Multimedia Webtech Sqp 2018 19" = "__DELETE__"
}

Write-Host "[1/3] Merging variant folders..." -ForegroundColor Green

$mergedCount = 0; $deletedCount = 0

# Process folders - need to re-read each time since we're modifying
$processedAny = $true
while ($processedAny) {
    $processedAny = $false
    $folders = Get-ChildItem -Path $baseDir -Directory -EA SilentlyContinue | Sort-Object Name
    
    foreach ($folder in $folders) {
        $folderName = $folder.Name
        
        if (-not $folderMergeMap.ContainsKey($folderName)) { continue }
        
        $canonicalName = $folderMergeMap[$folderName]
        
        if ($canonicalName -eq "__DELETE__") {
            $fileCount = (Get-ChildItem -Path $folder.FullName -Recurse -File -EA SilentlyContinue).Count
            if ($fileCount -eq 0) {
                Remove-Item $folder.FullName -Recurse -Force -EA SilentlyContinue
                Write-Host "  DELETE: $folderName (empty)" -ForegroundColor DarkGray
                $deletedCount++
                $processedAny = $true
            }
            continue
        }
        
        if ($folderName -eq $canonicalName) { continue }
        
        $targetDir = Join-Path $baseDir $canonicalName
        
        if (-not (Test-Path $targetDir)) {
            Rename-Item -Path $folder.FullName -NewName $canonicalName -EA SilentlyContinue
            Write-Host "  RENAME: $folderName -> $canonicalName" -ForegroundColor Yellow
        } else {
            # Merge contents
            $subDirs = Get-ChildItem -Path $folder.FullName -Directory -EA SilentlyContinue
            foreach ($subDir in $subDirs) {
                $destSubDir = Join-Path $targetDir $subDir.Name
                if (-not (Test-Path $destSubDir)) {
                    Move-Item -Path $subDir.FullName -Destination $destSubDir -Force -EA SilentlyContinue
                } else {
                    Get-ChildItem -Path $subDir.FullName -File -EA SilentlyContinue | ForEach-Object {
                        $destFile = Join-Path $destSubDir $_.Name
                        if (-not (Test-Path $destFile)) {
                            Move-Item -Path $_.FullName -Destination $destFile -Force -EA SilentlyContinue
                        }
                    }
                }
            }
            # Move root files too
            Get-ChildItem -Path $folder.FullName -File -EA SilentlyContinue | ForEach-Object {
                $destFile = Join-Path $targetDir $_.Name
                if (-not (Test-Path $destFile)) { Move-Item -Path $_.FullName -Destination $destFile -Force -EA SilentlyContinue }
            }
            # Remove if empty
            $remaining = (Get-ChildItem -Path $folder.FullName -Recurse -File -EA SilentlyContinue).Count
            if ($remaining -eq 0) {
                Remove-Item $folder.FullName -Recurse -Force -EA SilentlyContinue
                Write-Host "  MERGE: $folderName -> $canonicalName" -ForegroundColor Yellow
            } else {
                Write-Host "  PARTIAL MERGE: $folderName -> $canonicalName ($remaining files remain)" -ForegroundColor Yellow
            }
        }
        $mergedCount++
        $processedAny = $true
        break  # Restart loop since folder list changed
    }
}

Write-Host "  Merged: $mergedCount, Deleted: $deletedCount" -ForegroundColor Gray

# Step 2: Remove empty directories (bottom-up)
Write-Host "[2/3] Cleaning empty directories..." -ForegroundColor Green
$emptyCount = 0
do {
    $found = $false
    Get-ChildItem -Path $baseDir -Directory -Recurse -EA SilentlyContinue | Sort-Object { $_.FullName.Length } -Descending | ForEach-Object {
        if ((Get-ChildItem -Path $_.FullName -EA SilentlyContinue).Count -eq 0) {
            Remove-Item $_.FullName -Force -EA SilentlyContinue
            $emptyCount++
            $found = $true
        }
    }
} while ($found)
Write-Host "  Removed $emptyCount empty directories" -ForegroundColor Gray

# Step 3: Final report
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  CBSE Class XII PYQ Complete Archive" -ForegroundColor Cyan
Write-Host "  Location: $baseDir" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$folders = Get-ChildItem -Path $baseDir -Directory -EA SilentlyContinue | Sort-Object Name
$totalFiles = 0; $totalSize = 0

foreach ($folder in $folders) {
    $files = Get-ChildItem -Path $folder.FullName -Recurse -File -EA SilentlyContinue
    $fileCount = $files.Count
    $sizeBytes = ($files | Measure-Object -Property Length -Sum -EA SilentlyContinue).Sum
    if (-not $sizeBytes) { $sizeBytes = 0 }
    $sizeMB = ($sizeBytes / 1MB).ToString("N1")
    $years = (Get-ChildItem -Path $folder.FullName -Directory -EA SilentlyContinue | Select-Object -ExpandProperty Name | Sort-Object) -join ", "
    
    Write-Host "  $($folder.Name)" -ForegroundColor White -NoNewline
    Write-Host " | $fileCount files | $sizeMB MB | [$years]" -ForegroundColor Gray
    
    $totalFiles += $fileCount
    $totalSize += $sizeBytes
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  TOTAL: $($folders.Count) subjects" -ForegroundColor White
Write-Host "  FILES: $totalFiles" -ForegroundColor White
Write-Host "  SIZE:  $(($totalSize / 1MB).ToString('N1')) MB ($(($totalSize / 1GB).ToString('N2')) GB)" -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Cyan
