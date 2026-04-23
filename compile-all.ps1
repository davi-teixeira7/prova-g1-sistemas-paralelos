$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$javaFiles = Get-ChildItem -Path "src" -Recurse -Filter "*.java" | ForEach-Object {
    $_.FullName
}

if (-not $javaFiles) {
    throw "Nenhum arquivo .java foi encontrado em src."
}

Write-Host "Compilando arquivos Java..."
javac $javaFiles

if ($LASTEXITCODE -ne 0) {
    throw "Falha na compilacao."
}

Write-Host "Compilacao concluida com sucesso."
