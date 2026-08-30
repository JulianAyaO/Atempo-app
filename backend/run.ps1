# No se necesita API key con Ollama local
$env:OPENAI_API_KEY = "ollama"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$MvnLocal = Join-Path $ScriptDir "tools\apache-maven-3.9.6\bin\mvn.cmd"
$Args = @("spring-boot:run", "-DskipTests")

if (Test-Path $MvnLocal) {
    & $MvnLocal @Args
} elseif (Test-Path ".\mvnw.cmd") {
    & .\mvnw.cmd @Args
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn @Args
} else {
    throw "Maven not found. Install Maven or ensure backend/tools/apache-maven-3.9.6 exists."
}
