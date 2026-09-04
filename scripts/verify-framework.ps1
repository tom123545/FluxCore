Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Host 'Checking Java...' -ForegroundColor Cyan
java -version

Write-Host 'Checking Maven...' -ForegroundColor Cyan
mvn -version

Write-Host 'Validating Maven project...' -ForegroundColor Cyan
mvn -q validate

Write-Host 'Framework validation passed.' -ForegroundColor Green
