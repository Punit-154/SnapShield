# CBSE Class XII PYQ Complete Downloader
# Downloads ALL question papers from cbse.gov.in (2022-2026) and organizes by subject
# Subject names are normalized across years for consistency

param(
    [string]$BatchId = "all"  # "1", "2", "3", "4", or "all"
)

$ErrorActionPreference = "Continue"
$baseDir = "D:\Study Papers\Jeffrey"
$baseUrl = "https://www.cbse.gov.in/cbsenew/"
$pageUrl = "https://www.cbse.gov.in/cbsenew/question-paper.html"

# Subject name normalization map - maps various filenames to canonical folder names
$subjectNormMap = @{
    # Core Academic Subjects
    "Accountancy" = "Accountancy"
    "Biology" = "Biology"
    "Chemistry" = "Chemistry"
    "Physics" = "Physics"
    "Math" = "Mathematics"
    "Mathematics" = "Mathematics"
    "Applied_Math" = "Applied Mathematics"
    "Applied_Mathematics" = "Applied Mathematics"
    "465_APPLIED_MATHEMATICS" = "Applied Mathematics"
    "Economics" = "Economics"
    "Geography" = "Geography"
    "History" = "History"
    "Political_Science" = "Political Science"
    "Poltical_Science" = "Political Science"
    "Sociology" = "Sociology"
    "62_Sociology" = "Sociology"
    "Psychology" = "Psychology"
    "63_Psychology" = "Psychology"
    "Philosophy" = "Philosophy"
    
    # English
    "ENGLISH_CORE" = "English Core"
    "Eng_Core" = "English Core"
    "English_Elective" = "English Elective"
    "28_ENGLISH_Elective" = "English Elective"
    
    # Hindi
    "Hindi_Core" = "Hindi Core"
    "Hindi_Elective" = "Hindi Elective"
    
    # Other Languages
    "Arabic" = "Arabic"
    "16_ARABIC" = "Arabic"
    "Assamese" = "Assamese"
    "14_ASSAMESE" = "Assamese"
    "Bengali" = "Bengali"
    "5_Bengali" = "Bengali"
    "BODO" = "Bodo"
    "38_BODO" = "Bodo"
    "Bhoti" = "Bhoti"
    "Bhutia" = "Bhutia"
    "27_BHUTIA" = "Bhutia"
    "French" = "French"
    "18_FRENCH" = "French"
    "German" = "German"
    "20_GERMAN" = "German"
    "Gujarati" = "Gujarati"
    "10_GUJARATI" = "Gujarati"
    "Japanese" = "Japanese"
    "37_JAPANESE" = "Japanese"
    "Kannada" = "Kannada"
    "15_KANNADA" = "Kannada"
    "Kashmiri" = "Kashmiri"
    "96_Kashmiri" = "Kashmiri"
    "Kokborok" = "Kokborok"
    "Lepcha" = "Lepcha"
    "26_LEPCHA" = "Lepcha"
    "Limboo" = "Limboo"
    "25_LIMBOO" = "Limboo"
    "Malayalam" = "Malayalam"
    "malayalam_XII" = "Malayalam"
    "12_MALAYALAM" = "Malayalam"
    "Manipuri" = "Manipuri"
    "11_MANIPURI" = "Manipuri"
    "Marathi" = "Marathi"
    "9_MARATHI" = "Marathi"
    "Mizo" = "Mizo"
    "97_MIZO" = "Mizo"
    "Nepali" = "Nepali"
    "24_NEPALI" = "Nepali"
    "Odia" = "Odia"
    "13_ODIA" = "Odia"
    "Persian" = "Persian"
    "23_PERSIAN" = "Persian"
    "Punjabi" = "Punjabi"
    "4_PUNJABI" = "Punjabi"
    "Russian" = "Russian"
    "21_RUSSIAN" = "Russian"
    "Sanskrit_Core" = "Sanskrit Core"
    "22_SANSKRIT_Core" = "Sanskrit Core"
    "Sanskrit_Elective" = "Sanskrit Elective"
    "49_Sanskrit_Elective" = "Sanskrit Elective"
    "SINDHI" = "Sindhi"
    "8_SINDHI" = "Sindhi"
    "Spanish" = "Spanish"
    "95_Spanish" = "Spanish"
    "Tamil" = "Tamil"
    "6_TAMIL" = "Tamil"
    "Telugu" = "Telugu"
    "7_TELUGU" = "Telugu"
    "Telugu_Telangana" = "Telugu (Telangana)"
    "50_TELUGU_TELANGANA" = "Telugu (Telangana)"
    "Tibetan" = "Tibetan"
    "Tibetan_cl.12" = "Tibetan"
    "17_TIBETAN" = "Tibetan"
    "tangkhul" = "Tangkhul"
    "tangkhul-QP" = "Tangkhul"
    "35_TANGKHUL" = "Tangkhul"
    "Urdu_Core" = "Urdu Core"
    "3_ Urdu_Core" = "Urdu Core"
    "Urdu_Elective" = "Urdu Elective"
    "30_Urdu_Elective" = "Urdu Elective"
    
    # Commerce Subjects
    "Business_Studies" = "Business Studies"
    "BS" = "Business Studies"
    "Business_Administration" = "Business Administration"
    "357_Business_Administration" = "Business Administration"
    "Cost_Accounting" = "Cost Accounting"
    "347_COST_ACCOUNTING" = "Cost Accounting"
    "Entrepreneurship" = "Entrepreneurship"
    "98_Entrepreneurship" = "Entrepreneurship"
    "Financial_Markets_Management" = "Financial Markets Management"
    "329_FMM" = "Financial Markets Management"
    "Taxation" = "Taxation"
    "346_Taxation" = "Taxation"
    "Salesmanship" = "Salesmanship"
    "355_Salesmanship" = "Salesmanship"
    "Insurance" = "Insurance"
    "338_Insurance" = "Insurance"
    "Banking" = "Banking"
    "335_Banking" = "Banking"
    "marketing" = "Marketing"
    "336_MARKETING" = "Marketing"
    "OFFICE_PROCEDURES_PRACTICES" = "Office Procedures and Practices"
    "348_OFFICE_PROCEDURES_PRACTICES" = "Office Procedures and Practices"
    "Retail" = "Retail"
    "325_RETAIL" = "Retail"
    
    # Science/Tech Subjects
    "Computer_Science" = "Computer Science"
    "91_Computer_Science" = "Computer Science"
    "Informatics_Practices" = "Informatics Practices"
    "90_Informatics_Practices" = "Informatics Practices"
    "Information_Technology" = "Information Technology"
    "326_Information_Technology" = "Information Technology"
    "Biotechnology" = "Biotechnology"
    "99_Biotechnology" = "Biotechnology"
    "Data_Science" = "Data Science"
    "Data _Science" = "Data Science"
    "368_DATA_SCIENCE" = "Data Science"
    "Artificial_Intelligence" = "Artificial Intelligence"
    "Artificial _Intelligence" = "Artificial Intelligence"
    "367_ARTIFICIAL_INTELLIGENCE" = "Artificial Intelligence"
    "Design_Thinking_Innovation" = "Design Thinking and Innovation"
    "Geospatial_technology" = "Geospatial Technology"
    "342_Geospatial_Technology" = "Geospatial Technology"
    "Electronics_Hardware" = "Electronics and Hardware"
    "Electronics_Technology" = "Electronics Technology"
    "344_Electronics_Technology" = "Electronics Technology"
    "Electrical_Technology" = "Electrical Technology"
    "Electical_Technology" = "Electrical Technology"
    "343_Electrical_Technology" = "Electrical Technology"
    "Medical_Diagnostics" = "Medical Diagnostics"
    "352_Medical_Diagnostics" = "Medical Diagnostics"
    "Multimedia" = "Multimedia"
    "345_MULTIMEDIA" = "Multimedia"
    "WEB_APPLICATIONS" = "Web Applications"
    "WEB_APPLICATIONS " = "Web Applications"
    "327_Web_Applications" = "Web Applications"
    
    # Engineering & Technical
    "Engg_Graphics" = "Engineering Graphics"
    "Engineering_Graphics" = "Engineering Graphics"
    "68_Engg_Graphics" = "Engineering Graphics"
    "Air_Conditioning_and_Refrigeration" = "Air Conditioning and Refrigeration"
    "Air_Conditionng_Refrigeration" = "Air Conditioning and Refrigeration"
    "351_Air_Conditioning_Refrigeration" = "Air Conditioning and Refrigeration"
    "Automotive" = "Automotive"
    "328_AUTOMOTIVE" = "Automotive"
    "Typography_and_Computer_Applications" = "Typography and Computer Applications"
    "Typography_Comp_Applications" = "Typography and Computer Applications"
    "Typography_Computer" = "Typography and Computer Applications"
    "341_Typography_Computer_Applications" = "Typography and Computer Applications"
    "Shorthand_English" = "Shorthand English"
    "349_Shorthand_English" = "Shorthand English"
    "Shorthand_Hindi" = "Shorthand Hindi"
    "350_Shorthand_Hindi" = "Shorthand Hindi"
    
    # Arts/Music/Dance
    "Painting" = "Painting"
    "71_Painting" = "Painting"
    "Commercial_Art" = "Commercial Art"
    "Commercial_Art_Theory" = "Commercial Art"
    "72_Commercial_Art" = "Commercial Art"
    "Sculpture" = "Sculpture"
    "73_Sculpture" = "Sculpture"
    "Graphics" = "Graphics (Fine Art)"
    "74_Graphics" = "Graphics (Fine Art)"
    "Carnatic_Music_Vocal" = "Carnatic Music (Vocal)"
    "Carnatic Music Vocal_76" = "Carnatic Music (Vocal)"
    "Carnatic_Music" = "Carnatic Music (Vocal)"
    "76_Carnatic_Music_Vocal" = "Carnatic Music (Vocal)"
    "Carnatic_Music_Melodic_Instru" = "Carnatic Music (Melodic Instruments)"
    "Carnatic_Music_Inst_Mel" = "Carnatic Music (Melodic Instruments)"
    "77_Carnatic_Music_Inst_Melodic" = "Carnatic Music (Melodic Instruments)"
    "Carnatic_Music_Instl" = "Carnatic Music (Percussion Instruments)"
    "Carnatic_Music_Inst_Per" = "Carnatic Music (Percussion Instruments)"
    "78_Carnatic_Music_Ins_Perc" = "Carnatic Music (Percussion Instruments)"
    "Music_Hindustani_Vocal" = "Hindustani Music (Vocal)"
    "Music_Hindustani_vocal_Theory" = "Hindustani Music (Vocal)"
    "79_Music_Hindustani_Vocal" = "Hindustani Music (Vocal)"
    "Hindustani_Music_Melodic_Insmts" = "Hindustani Music (Melodic Instruments)"
    "Hindustani_Music_ Mel_Inst" = "Hindustani Music (Melodic Instruments)"
    "Music_Hindustani_MI" = "Hindustani Music (Melodic Instruments)"
    "80_HINDUSTANI_MUSIC_Melodic_Instuments" = "Hindustani Music (Melodic Instruments)"
    "Hindustani_Music_Percussion_Inst" = "Hindustani Music (Percussion Instruments)"
    "Hindustani_Music_ Perc_Inst" = "Hindustani Music (Percussion Instruments)"
    "Music_Hindustani_PI" = "Hindustani Music (Percussion Instruments)"
    "81_HINDUSTANI_MUSIC_Percussion_Instruments" = "Hindustani Music (Percussion Instruments)"
    "Kathak_dance" = "Kathak Dance"
    "82_KATHAK_DANCE" = "Kathak Dance"
    "Manipuri_Dance" = "Manipuri Dance"
    "83_Manipuri_Dance" = "Manipuri Dance"
    "Bharatanatyam_Dance" = "Bharatanatyam Dance"
    "Bharatnatayam_Dance" = "Bharatanatyam Dance"
    "Bharatnatyam_Dance" = "Bharatanatyam Dance"
    "84_BHARATANATYAM_DANCE" = "Bharatanatyam Dance"
    "Kathakali_Dance" = "Kathakali Dance"
    "86_KATHAKALI_DANCE" = "Kathakali Dance"
    "Odissi_Dance" = "Odissi Dance"
    "87_ODISSI_DANCE" = "Odissi Dance"
    "Kuchipudi_Dance" = "Kuchipudi Dance"
    "Kuchipudii_Dance" = "Kuchipudi Dance"
    "Dance_Kuchipudi" = "Kuchipudi Dance"
    "88_Kuchipudii_Dance" = "Kuchipudi Dance"
    
    # Other Subjects
    "Physical_Education" = "Physical Education"
    "Phy_Edu" = "Physical Education"
    "75_Physical_Education" = "Physical Education"
    "Physical_Activity_Trainer" = "Physical Activity Trainer"
    "Home_Science" = "Home Science"
    "Home Science" = "Home Science"
    "Home_Scince" = "Home Science"
    "69_Home_Science" = "Home Science"
    "Legal_Studies" = "Legal Studies"
    "40_LEGAL_STUDIES" = "Legal Studies"
    "Fashion_Studies" = "Fashion Studies"
    "361_Fashion_Studies" = "Fashion Studies"
    "Tourism" = "Tourism"
    "330_Tourism" = "Tourism"
    "Agriculture" = "Agriculture"
    "332_AGRICULTURE" = "Agriculture"
    "Horticulture" = "Horticulture"
    "340_Horticulture" = "Horticulture"
    "Food_Production" = "Food Production"
    "333_Food_Production" = "Food Production"
    "Food_Nutrition" = "Food Nutrition and Dietetics"
    "Food_Nutrition_&_dietetics" = "Food Nutrition and Dietetics"
    "Food_Nutrition_Dietetics" = "Food Nutrition and Dietetics"
    "358_Food_Nutrition_Dietetics" = "Food Nutrition and Dietetics"
    "Front_Office_Operations" = "Front Office Operations"
    "334_Front_Office_Operations" = "Front Office Operations"
    "Health_Care" = "Health Care"
    "337_HEALTH_CARE" = "Health Care"
    "Beauty_Wellness" = "Beauty and Wellness"
    "Beauty _and_Wellness" = "Beauty and Wellness"
    "331_BEAUTY_WELLNESS" = "Beauty and Wellness"
    "Yoga" = "Yoga"
    "365_Yoga" = "Yoga"
    "Early_Childhood_Care_Educaiton" = "Early Childhood Care and Education"
    "Early_Childhood_Care_&_Education" = "Early Childhood Care and Education"
    "Early_Childhood_Care_Edn" = "Early Childhood Care and Education"
    "366_EARLY_CHILDHOOD_CARE_EDUCATION" = "Early Childhood Care and Education"
    "National_Cadet_Corps_NCC" = "National Cadet Corps (NCC)"
    "NCC" = "National Cadet Corps (NCC)"
    "42_National_Cadet_Corps_NCC" = "National Cadet Corps (NCC)"
    "Knowledge_Tradubg" = "Knowledge Traditions and Practices of India"
    "KTPI" = "Knowledge Traditions and Practices of India"
    "39_KTPI" = "Knowledge Traditions and Practices of India"
    "Library_and_Information_Science" = "Library and Information Science"
    "Library_Info_Science" = "Library and Information Science"
    "Library_Information_Science" = "Library and Information Science"
    "360_Library_Information_Science" = "Library and Information Science"
    "Mass_Media_Studies" = "Mass Media Studies"
    "359_Mass_Media_Studies" = "Mass Media Studies"
    "Textile_Design" = "Textile Design"
    "353_Textile_Design" = "Textile Design"
    "Design" = "Design"
    "354_Design" = "Design"
}

# Create base directory
if (-not (Test-Path $baseDir)) {
    New-Item -ItemType Directory -Path $baseDir -Force | Out-Null
}

Write-Host "=== CBSE Class XII PYQ Complete Downloader ===" -ForegroundColor Cyan
Write-Host "Target: $baseDir" -ForegroundColor Yellow
Write-Host "Batch: $BatchId" -ForegroundColor Yellow
Write-Host ""

# Step 1: Fetch the question paper page
Write-Host "[1/4] Fetching CBSE question paper page..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $pageUrl -UseBasicParsing -TimeoutSec 60
    $html = $response.Content
    Write-Host "  Page fetched successfully" -ForegroundColor Gray
} catch {
    Write-Host "  ERROR: Failed to fetch page: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Parse all ZIP download links for Class XII
Write-Host "[2/4] Parsing download links..." -ForegroundColor Green

$zipPattern = 'href="(question-paper/\d{4}/XII/[^"]+\.zip)"'
$zipMatches = [regex]::Matches($html, $zipPattern)

$downloads = @()
foreach ($match in $zipMatches) {
    $relPath = $match.Groups[1].Value
    $fullUrl = $baseUrl + $relPath
    
    if ($relPath -match 'question-paper/(\d{4})/XII/(.+)\.zip') {
        $year = $Matches[1]
        $subjectFile = $Matches[2]
        
        # Normalize subject name
        $subjectName = $subjectFile.Trim()
        if ($subjectNormMap.ContainsKey($subjectName)) {
            $subjectName = $subjectNormMap[$subjectName]
        } else {
            # Fallback: clean up the name
            $subjectName = $subjectName -replace '^\d+_', ''
            $subjectName = $subjectName -replace '_', ' '
            $subjectName = $subjectName -replace '  +', ' '
            $subjectName = (Get-Culture).TextInfo.ToTitleCase($subjectName.ToLower().Trim())
        }
        
        $downloads += [PSCustomObject]@{
            Url = $fullUrl
            Year = $year
            Subject = $subjectName
            FileName = "$subjectFile.zip"
            RelPath = $relPath
        }
    }
}

Write-Host "  Found $($downloads.Count) total download links" -ForegroundColor Gray

# Split into batches if needed
$totalBatches = 4
$batchSize = [Math]::Ceiling($downloads.Count / $totalBatches)

if ($BatchId -ne "all") {
    $batchNum = [int]$BatchId
    $startIdx = ($batchNum - 1) * $batchSize
    $endIdx = [Math]::Min($startIdx + $batchSize, $downloads.Count)
    $downloads = $downloads[$startIdx..($endIdx - 1)]
    Write-Host "  Processing batch $BatchId ($($downloads.Count) items, index $startIdx to $($endIdx - 1))" -ForegroundColor Yellow
}

# Group by year for display
$byYear = $downloads | Group-Object -Property Year
foreach ($yearGroup in $byYear) {
    Write-Host "    Year $($yearGroup.Name): $($yearGroup.Count) subjects" -ForegroundColor Gray
}

# Step 3: Download and extract
Write-Host "[3/4] Downloading and extracting papers..." -ForegroundColor Green

$totalCount = $downloads.Count
$currentCount = 0
$successCount = 0
$failCount = 0
$skipCount = 0
$failedItems = @()

foreach ($dl in $downloads) {
    $currentCount++
    $subjectDir = Join-Path $baseDir $dl.Subject
    $yearDir = Join-Path $subjectDir $dl.Year
    $zipPath = Join-Path $yearDir $dl.FileName
    
    # Create directory
    if (-not (Test-Path $yearDir)) {
        New-Item -ItemType Directory -Path $yearDir -Force | Out-Null
    }
    
    # Check if already downloaded and extracted
    $existingFiles = Get-ChildItem -Path $yearDir -File -ErrorAction SilentlyContinue
    if ($existingFiles.Count -gt 0) {
        Write-Host "  [$currentCount/$totalCount] SKIP $($dl.Subject) ($($dl.Year)) - already has $($existingFiles.Count) files" -ForegroundColor DarkGray
        $skipCount++
        continue
    }
    
    Write-Host "  [$currentCount/$totalCount] $($dl.Subject) ($($dl.Year))..." -ForegroundColor White -NoNewline
    
    try {
        # Download with retry
        $retries = 3
        $downloaded = $false
        for ($r = 0; $r -lt $retries; $r++) {
            try {
                Invoke-WebRequest -Uri $dl.Url -OutFile $zipPath -UseBasicParsing -TimeoutSec 180
                $downloaded = $true
                break
            } catch {
                if ($r -lt $retries - 1) {
                    Start-Sleep -Seconds 2
                } else {
                    throw $_
                }
            }
        }
        
        if ($downloaded) {
            # Extract
            try {
                Expand-Archive -Path $zipPath -DestinationPath $yearDir -Force
                Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
                $extractedCount = (Get-ChildItem -Path $yearDir -Recurse -File).Count
                Write-Host " OK ($extractedCount files)" -ForegroundColor Green
                $successCount++
            } catch {
                Write-Host " EXTRACT WARN (keeping ZIP)" -ForegroundColor Yellow
                $successCount++
            }
        }
    } catch {
        Write-Host " FAILED" -ForegroundColor Red
        $failCount++
        $failedItems += "$($dl.Subject) ($($dl.Year)): $($dl.Url)"
        # Clean up empty directories
        if ((Get-ChildItem -Path $yearDir -ErrorAction SilentlyContinue).Count -eq 0) {
            Remove-Item $yearDir -Force -ErrorAction SilentlyContinue
        }
        if ((Test-Path $subjectDir) -and (Get-ChildItem -Path $subjectDir -ErrorAction SilentlyContinue).Count -eq 0) {
            Remove-Item $subjectDir -Force -ErrorAction SilentlyContinue
        }
    }
    
    # Small delay to be polite to the server
    Start-Sleep -Milliseconds 300
}

# Step 4: Summary
Write-Host ""
Write-Host "[4/4] Download Summary (Batch: $BatchId)" -ForegroundColor Green
Write-Host "  Total links processed: $totalCount" -ForegroundColor White
Write-Host "  Successfully downloaded: $successCount" -ForegroundColor Green
Write-Host "  Skipped (already exist): $skipCount" -ForegroundColor DarkGray
if ($failCount -gt 0) {
    Write-Host "  Failed: $failCount" -ForegroundColor Red
    Write-Host "  Failed items:" -ForegroundColor Red
    foreach ($fi in $failedItems) {
        Write-Host "    - $fi" -ForegroundColor Red
    }
} else {
    Write-Host "  Failed: 0" -ForegroundColor Green
}

# List all subject folders
Write-Host ""
Write-Host "=== Current Archive Contents ===" -ForegroundColor Cyan
$folders = Get-ChildItem -Path $baseDir -Directory -ErrorAction SilentlyContinue | Sort-Object Name
$totalFiles = 0
$totalSize = 0
foreach ($folder in $folders) {
    $files = Get-ChildItem -Path $folder.FullName -Recurse -File -ErrorAction SilentlyContinue
    $fileCount = $files.Count
    $sizeBytes = ($files | Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
    if (-not $sizeBytes) { $sizeBytes = 0 }
    $sizeMB = ($sizeBytes / 1MB).ToString("N1")
    $years = (Get-ChildItem -Path $folder.FullName -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name) -join ", "
    Write-Host "  $($folder.Name): $fileCount files ($sizeMB MB) [$years]" -ForegroundColor White
    $totalFiles += $fileCount
    $totalSize += $sizeBytes
}
Write-Host ""
Write-Host "  TOTAL: $($folders.Count) subjects, $totalFiles files, $(($totalSize / 1MB).ToString('N1')) MB" -ForegroundColor Cyan
Write-Host "=== Done! ===" -ForegroundColor Cyan
