$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$sources = @(
    "src/storage/AppPaths.java",
    "src/auth/AuthService.java",
    "src/security/CryptoService.java",
    "src/AuctionServer.java",
    "src/AuctionClient.java"
)

Write-Host "[1/2] Compilando arquivos Java..."
javac @sources

if ($LASTEXITCODE -ne 0) {
    throw "Falha na compilacao."
}

$powerShellExe = (Get-Command powershell).Source

function Start-LeilaoWindow {
    param(
        [string]$Title,
        [string]$Command
    )

    $windowCommand = @"
Set-Location '$repoRoot'
`$host.UI.RawUI.WindowTitle = '$Title'
$Command
"@

    Start-Process -FilePath $powerShellExe `
        -ArgumentList "-NoExit", "-Command", $windowCommand `
        -WorkingDirectory $repoRoot
}

Write-Host "[2/2] Abrindo janelas do teste local..."

Start-LeilaoWindow -Title "Leilao Replica 1" -Command "java -cp src AuctionServer replica 1"
Start-Sleep -Milliseconds 400

Start-LeilaoWindow -Title "Leilao Replica 2" -Command "java -cp src AuctionServer replica 2"
Start-Sleep -Milliseconds 400

Start-LeilaoWindow -Title "Leilao Primario" -Command "java -cp src AuctionServer"

Write-Host "Janelas abertas:"
Write-Host "- Replica 1"
Write-Host "- Replica 2"
Write-Host "- Primario"
Write-Host ""
Write-Host "Cliente manual:"
Write-Host "  java -cp src AuctionClient"
Write-Host ""
Write-Host "Execute com:"
Write-Host "  powershell -ExecutionPolicy Bypass -File .\start-local-test.ps1"
