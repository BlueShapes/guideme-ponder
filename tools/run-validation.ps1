param(
    [ValidateSet('neoforge', 'forge')][string]$Platform = 'neoforge',
    [ValidatePattern('^[a-z0-9_]+(,[a-z0-9_]+)*$')][string]$Lessons = 'first_network',
    [switch]$Focused,
    [switch]$Languages,
    [switch]$Captions,
    [switch]$Narration,
    [switch]$Coverage,
    [switch]$StaffOnly,
    [switch]$TextOnly,
    [string]$AdditionalItems = '',
    [string]$TextKeys = '',
    [string]$TextSelection = '',
    [switch]$WithoutAddons
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$validationLock = [System.Threading.Mutex]::new($false, 'Local\PonderSeriesValidation')
$lockAcquired = $false
$validationExit = 1
if ($StaffOnly) {
    if ($TextOnly) { throw 'TextOnly and StaffOnly are mutually exclusive validation modes.' }
    $Coverage = $true
    Write-Output 'StaffOnly enables Coverage validation for charged staff and explicitly selected additional items.'
}
if ($TextOnly) {
    if ([string]::IsNullOrWhiteSpace($TextKeys) -and [string]::IsNullOrWhiteSpace($TextSelection)) {
        throw 'TextOnly requires either a non-empty TextKeys selection or a TextSelection resource.'
    }
    if (![string]::IsNullOrWhiteSpace($TextKeys) -and ![string]::IsNullOrWhiteSpace($TextSelection)) {
        throw 'TextOnly cannot combine TextKeys and TextSelection.'
    }
    if (![string]::IsNullOrWhiteSpace($AdditionalItems)) { throw 'TextOnly cannot be combined with AdditionalItems.' }
    $Coverage = $true
    Write-Output 'TextOnly enables Coverage validation for the explicitly selected localized text.'
}
if (![string]::IsNullOrWhiteSpace($TextSelection)) {
    if (!$TextOnly) { throw 'TextSelection requires TextOnly.' }
    if ($TextSelection -notmatch '^ae2ponder:validation/[a-z0-9/._-]+\.json$') {
        throw 'TextSelection must be an ae2ponder validation JSON resource ID.'
    }
}
if (![string]::IsNullOrWhiteSpace($AdditionalItems)) {
    if (!$StaffOnly) { throw 'AdditionalItems requires StaffOnly so the selected native W checks remain a targeted validation.' }
    if ($AdditionalItems -notmatch '^[a-z0-9_.-]+:[a-z0-9/._-]+(,[a-z0-9_.-]+:[a-z0-9/._-]+)*$') {
        throw 'AdditionalItems must be a comma-separated list of fully qualified item IDs.'
    }
}
if (![string]::IsNullOrWhiteSpace($TextKeys)) {
    if (!$StaffOnly -and !$TextOnly) { throw 'TextKeys requires StaffOnly or TextOnly so only the explicitly selected text is rechecked.' }
    if ($TextKeys -notmatch '^[a-z0-9_.-]+(,[a-z0-9_.-]+)*$') {
        throw 'TextKeys must be a comma-separated list of translation keys.'
    }
}
if ($Coverage -and ($Focused -or $Languages -or $Captions -or $Narration)) {
    throw 'Coverage validation has its own targeted item, navigation and language checks.'
}
if ($Focused -and ($Languages -or $Captions)) {
    throw 'Focused navigation validation cannot be combined with Languages or Captions.'
}
if ($Narration -and ($Focused -or $Languages -or $Captions -or $WithoutAddons)) {
    throw 'Narration combines focused navigation and caption validation with all supported add-ons enabled.'
}
try {
    Write-Output 'Waiting for Local\PonderSeriesValidation...'
    while (!$lockAcquired) {
        try { $lockAcquired = $validationLock.WaitOne(10000) }
        catch [System.Threading.AbandonedMutexException] { $lockAcquired = $true }
        if (!$lockAcquired) { Write-Output 'Another Ponder series validation is still running.' }
    }
    Write-Output 'Acquired Local\PonderSeriesValidation'

    # Check only this checkout's Minecraft client; never stop another project's process.
    $activeClients = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object {
            $_.CommandLine -and $_.CommandLine.Contains($projectRoot) -and
            $_.CommandLine -match 'devlaunch|--launchTarget|net\.minecraft\.client\.main'
        })
    if ($activeClients.Count -gt 0) {
        throw "This project already has a Minecraft client (PID $($activeClients.ProcessId -join ', ')); reuse or finish it before validation."
    }

    $validationArgs = @("-Pplatform=$Platform", '-PvalidateGuide', "-PvalidationLessons=$Lessons",
        'runValidationClient', '--console=plain')
    if ($Languages) { $validationArgs += '-PvalidateLanguages' }
    if ($Captions) { $validationArgs += '-PvalidateCaptions' }
    if ($Focused) { $validationArgs += '-PvalidateFocused' }
    if ($Narration) { $validationArgs += '-PvalidateNarration' }
    if ($Coverage) { $validationArgs += '-PvalidateCoverage' }
    if ($StaffOnly) { $validationArgs += '-PvalidateStaffOnly' }
    if ($TextOnly) { $validationArgs += '-PvalidateTextOnly' }
    if (![string]::IsNullOrWhiteSpace($AdditionalItems)) { $validationArgs += "-PvalidationAdditionalItems=$AdditionalItems" }
    if (![string]::IsNullOrWhiteSpace($TextKeys)) { $validationArgs += "-PvalidationTextKeys=$TextKeys" }
    if (![string]::IsNullOrWhiteSpace($TextSelection)) { $validationArgs += "-PvalidationTextSelection=$TextSelection" }
    if ($WithoutAddons) { $validationArgs += '-PwithoutAddons' }
    Push-Location -LiteralPath $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') @validationArgs
        $validationExit = $LASTEXITCODE
    }
    finally { Pop-Location }
}
finally {
    if ($lockAcquired) { $validationLock.ReleaseMutex() }
    $validationLock.Dispose()
}
exit $validationExit
