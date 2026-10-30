<#
.SYNOPSIS
    Read-only search of the SRI issue tracker workbook.

.DESCRIPTION
    Opens the .xlsx (a ZIP container) read-only, extracts xl/sharedStrings.xml,
    and returns the shared-string cells matching a regex pattern. It never writes
    to or modifies the workbook. Use it to find historical incidents by error
    signature (e.g. ORA code, tablespace, model/business object).

.PARAMETER Pattern
    Regex to match against each shared-string cell (case-insensitive).
    Default matches everything.

.PARAMETER Path
    Path to the tracker workbook. Defaults to issue_tracker\SRI_Issue_Tracker.xlsx
    resolved from the repo root (one level above this script's folder).

.PARAMETER MaxLength
    Max characters shown per matching cell (default 200). Use 0 for full text.

.EXAMPLE
    .\scripts\Search-IssueTracker.ps1 -Pattern 'ORA-01692|PRISMA_LOB_8K'

.EXAMPLE
    .\scripts\Search-IssueTracker.ps1 -Pattern 'timeout' -MaxLength 0
#>
[CmdletBinding()]
param(
    [string]$Pattern = '.',
    [string]$Path,
    [int]$MaxLength = 200
)

$ErrorActionPreference = 'Stop'

if (-not $Path) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $Path = Join-Path $repoRoot 'issue_tracker\SRI_Issue_Tracker.xlsx'
}

if (-not (Test-Path -LiteralPath $Path)) {
    throw "Tracker workbook not found: $Path"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
try {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq 'xl/sharedStrings.xml' }
    if (-not $entry) {
        throw "xl/sharedStrings.xml not found in workbook (no shared strings)."
    }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { $xml = $reader.ReadToEnd() } finally { $reader.Dispose() }
}
finally {
    $zip.Dispose()
}

$opt = [System.Text.RegularExpressions.RegexOptions]::Singleline
$cells = [regex]::Matches($xml, '<t[^>]*>(.*?)</t>', $opt) | ForEach-Object {
    # Decode the five standard XML entities so matches work on real text.
    $_.Groups[1].Value `
        -replace '&lt;', '<' -replace '&gt;', '>' `
        -replace '&quot;', '"' -replace '&apos;', "'" -replace '&amp;', '&'
}

$matches = $cells | Where-Object { $_ -match $Pattern }

Write-Host "Tracker: $Path"
Write-Host "Total shared strings: $($cells.Count)"
Write-Host "Pattern: $Pattern"
Write-Host "Matches: $($matches.Count)"
Write-Host ('-' * 60)

foreach ($cell in $matches) {
    $flat = ($cell -replace '\s+', ' ').Trim()
    if ($MaxLength -gt 0 -and $flat.Length -gt $MaxLength) {
        $flat = $flat.Substring(0, $MaxLength) + ' ...'
    }
    Write-Output $flat
    Write-Output ''
}
