# Generates a client-demo deck for the ReStat SRI support agent as a real .pptx
# using PowerPoint COM automation (no external libraries). Output: output\ReStat_Agent_Demo.pptx

$ErrorActionPreference = 'Stop'

function RGB([int]$r, [int]$g, [int]$b) { return [int]($r + ($g * 256) + ($b * 65536)) }

$NAVY   = RGB 18 38 82
$ACCENT = RGB 0 158 170
$INK    = RGB 45 45 45
$MUTED  = RGB 110 120 135
$WHITE  = RGB 255 255 255
$LIGHT  = RGB 244 246 249

$W = 960.0
$H = 540.0

# Layout / shape enum constants (avoid loading interop types)
$ppLayoutBlank = 12
$msoShapeRectangle = 1
$msoTextHorizontal = 1
$msoTrue = -1
$msoFalse = 0
$ppSaveOpenXML = 24
$ppAlignLeft = 1
$ppAlignCenter = 2

$slides = @(
  @{ Type='title'; Title='ReStat';
     Subtitle='AI Support Agent for the StatistiX Realtime Interface (SRI)';
     Tagline='Evidence-based root-cause analysis for SRI incidents';
     Footer='Deutsche Boerse  |  StatistiX  |  Client Demo' },

  @{ Type='content'; Title='What is ReStat?'; Bullets=@(
     'A senior SRI support engineer, delivered as an AI agent',
     'Analyzes SRI logs to identify the issue, root cause, impact and resolution',
     'Grounded in a curated SRI knowledge base and your issue history',
     'Guides the engineer step-by-step with exact, read-only commands') },

  @{ Type='content'; Title='Why it matters'; Bullets=@(
     'SRI incidents span the full chain: client -> AMQP -> SRI engine -> Oracle',
     'Root-cause analysis is log-heavy, multi-system and time-pressured',
     'Under pressure it is easy to jump to the wrong conclusion',
     'ReStat keeps the investigation evidence-first and repeatable') },

  @{ Type='content'; Title='How it works - the flow'; Bullets=@(
     '1. Intake: environment, timing, server, client error',
     '2. Confirm the affected server and engine role',
     '3. Check the issue tracker for prior matches',
     '4. Evidence-first investigation: read-only logs, DB, AWR',
     '5. Root cause: confirmed facts vs hypotheses',
     '6. Record: tracker row + detailed analysis report') },

  @{ Type='content'; Title='Operating principles'; Bullets=@(
     'Evidence-first: separates confirmed facts from hypotheses',
     'Read-only and safe: no restarts, no SQL execution, no secrets',
     'Self-correcting: withdraws a conclusion when new evidence arrives',
     'Change-safety gate: any fix is reviewed, backed up and approved') },

  @{ Type='content'; Title='What ReStat knows'; Bullets=@(
     'Environments, hosts and engine topology (DEV / SIT / ACC / DR / PROD)',
     'Components and the end-to-end message flow',
     'Error patterns and a library of known issues',
     'Oracle metadata, database catalog, runbooks and Control-M jobs') },

  @{ Type='content'; Title='Case study - PROD QUERY_TIMEOUT'; Bullets=@(
     'Symptom: liqao la_calibration fails with QUERY_TIMEOUT (LaoUnderlyingPrices)',
     'Traced: client -> PRISMA_PROD Main engine -> Oracle (EUREX_PRISMA)',
     'Correlated three failures on 15-Oct via their transaction IDs',
     'Reframed from a "heavy query" to a genuine performance regression') },

  @{ Type='content'; Title='The evidence that nailed it'; Bullets=@(
     'Same query: ~10-16s on 07-08 Oct, timing out at 35-60 min from 14-Oct',
     'AWR plan flip: 774885401 (good, ~200K buffer gets) -> 531885362 (bad, 20-34M)',
     'Trigger: 11-Oct statistics re-gather on a 955M-row partitioned table',
     'Fix: refresh statistics / no_index hotfix / pin the good plan') },

  @{ Type='content'; Title='Outputs it produces'; Bullets=@(
     'Issue tracker row - appended, then updated in place through its lifecycle',
     'Elaborated analysis report per issue (output / <issue>.txt)',
     'Client-ready summaries for stakeholders',
     'Read-only PuTTY and SQL command packs for the engineer') },

  @{ Type='content'; Title='Safety and guardrails'; Bullets=@(
     'Read-only diagnostics scoped to the confirmed engine and time window',
     'Never executes SQL, never restarts engines, never exposes secrets',
     'Explicit approval required before any state-changing fix',
     'Historical tracker used as investigative leads, not as proof') },

  @{ Type='content'; Title='Value to the team'; Bullets=@(
     'Faster and more consistent root-cause analysis',
     'Knowledge retention across incidents and engineers',
     'Fewer wrong turns, with a clear audit trail',
     'Repeatable, well-documented output for every issue') },

  @{ Type='closing'; Title='Thank you';
     Subtitle='ReStat - SRI Support Agent';
     Tagline='Questions and live demo' }
)

$outDir = Join-Path (Get-Location) 'output'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$outPath = Join-Path $outDir 'ReStat_Agent_Demo.pptx'
if (Test-Path $outPath) { Remove-Item $outPath -Force }

$pp = New-Object -ComObject PowerPoint.Application
$pp.Visible = $msoTrue
$pres = $pp.Presentations.Add()
$pres.PageSetup.SlideWidth = $W
$pres.PageSetup.SlideHeight = $H

function Add-Rect($slide, $l, $t, $w, $h, $color) {
  $s = $slide.Shapes.AddShape($msoShapeRectangle, $l, $t, $w, $h)
  $s.Fill.ForeColor.RGB = [int]$color
  $s.Line.Visible = $msoFalse
  $s.Shadow.Visible = $msoFalse
  return $s
}

function Add-Text($slide, $l, $t, $w, $h, $text, $size, $color, $bold, $align, $font) {
  $tb = $slide.Shapes.AddTextbox($msoTextHorizontal, $l, $t, $w, $h)
  $tf = $tb.TextFrame
  $tf.WordWrap = $msoTrue
  $tf.TextRange.Text = $text
  $tf.TextRange.Font.Size = $size
  $tf.TextRange.Font.Name = $font
  $tf.TextRange.Font.Color.RGB = [int]$color
  $tf.TextRange.Font.Bold = $bold
  $tf.TextRange.ParagraphFormat.Alignment = $align
  return $tb
}

$idx = 0
foreach ($sl in $slides) {
  $idx++
  $slide = $pres.Slides.Add($idx, $ppLayoutBlank)

  if ($sl.Type -eq 'title' -or $sl.Type -eq 'closing') {
    Add-Rect $slide 0 0 $W $H $NAVY | Out-Null
    Add-Rect $slide 0 ($H/2 + 70) $W 5 $ACCENT | Out-Null
    Add-Text $slide 80 170 ($W-160) 90 $sl.Title 54 $WHITE $msoTrue $ppAlignLeft 'Segoe UI Semibold' | Out-Null
    if ($sl.Subtitle) { Add-Text $slide 82 275 ($W-160) 50 $sl.Subtitle 24 $ACCENT $msoFalse $ppAlignLeft 'Segoe UI' | Out-Null }
    if ($sl.Tagline)  { Add-Text $slide 82 325 ($W-160) 40 $sl.Tagline 18 (RGB 200 210 225) $msoFalse $ppAlignLeft 'Segoe UI' | Out-Null }
    if ($sl.Footer)   { Add-Text $slide 80 470 ($W-160) 30 $sl.Footer 14 (RGB 170 182 200) $msoFalse $ppAlignLeft 'Segoe UI' | Out-Null }
  }
  else {
    Add-Rect $slide 0 0 $W 92 $NAVY | Out-Null
    Add-Rect $slide 0 92 $W 6 $ACCENT | Out-Null
    Add-Text $slide 48 24 ($W-96) 48 $sl.Title 30 $WHITE $msoTrue $ppAlignLeft 'Segoe UI Semibold' | Out-Null

    $text = ($sl.Bullets -join "`r")
    $box = Add-Text $slide 56 128 ($W-112) 360 $text 18 $INK $msoFalse $ppAlignLeft 'Segoe UI'
    $tr = $box.TextFrame.TextRange
    $n = $tr.Paragraphs().Count
    for ($i = 1; $i -le $n; $i++) {
      $p = $tr.Paragraphs($i)
      $p.ParagraphFormat.SpaceAfter = 10
      $p.ParagraphFormat.SpaceBefore = 0
      $p.ParagraphFormat.Bullet.Visible = $msoTrue
      $p.ParagraphFormat.Bullet.Character = 8226
      $p.ParagraphFormat.Bullet.Font.Color.RGB = [int]$ACCENT
    }
    # footer
    Add-Text $slide 48 508 ($W-200) 24 'ReStat - SRI Support Agent' 11 $MUTED $msoFalse $ppAlignLeft 'Segoe UI' | Out-Null
    Add-Text $slide ($W-120) 508 72 24 ([string]$idx) 11 $MUTED $msoFalse $ppAlignCenter 'Segoe UI' | Out-Null
  }
}

$pres.SaveAs($outPath, $ppSaveOpenXML)
$pres.Close()
$pp.Quit()
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($pres) | Out-Null
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($pp) | Out-Null
[GC]::Collect(); [GC]::WaitForPendingFinalizers()

Write-Output "Created: $outPath"
Write-Output ("Slides: {0}" -f $slides.Count)
