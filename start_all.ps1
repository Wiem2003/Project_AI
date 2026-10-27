# ----- START ALL MICROSERVICES -----
# Run this from the repository root (the folder containing this script).
# API keys are read from .env (copy .env.example to .env first).

# Load .env into this session so every child window inherits the variables
if (Test-Path ".\.env") {
    Get-Content ".\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $name, $value = $line.Split("=", 2)
            if ($value) { [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process") }
        }
    }
    Write-Host "Loaded environment variables from .env"
} else {
    Write-Warning "No .env file found: copy .env.example to .env and add your API keys (AI features will not work without them)."
}

$services = @(
    "eureka-server",
    "ai-service",
    "event-service",
    "payment-service",
    "certificate-service",
    "quiz-feedback-service",
    "course-service",
    "user-service",
    "job-service",
    "preevaluation-service",
    "api-gateway"
)

foreach ($svc in $services) {
    if (Test-Path ".\$svc") {
        Write-Host "Starting $svc..."
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd `".\$svc`"; mvn spring-boot:run"
        if ($svc -eq "eureka-server") { Start-Sleep -Seconds 15 }  # let the registry come up first
    } else {
        Write-Warning "Folder .\$svc not found!"
    }
}

Write-Host "All services are launching. Check each PowerShell window for logs."
Write-Host "Frontend: cd frontend; npm install; npm start  ->  http://localhost:4200"
