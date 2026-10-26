# â”€â”€â”€â”€â”€ START ALL MICROSERVICES SIMULTANEOUSLY â”€â”€â”€â”€â”€
# Run this inside the 'integrated' folder

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
    "api-gateway"
)

foreach ($svc in $services) {
    if (Test-Path ".\$svc") {
        Write-Host "Starting $svc..."
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd `".\$svc`"; mvn spring-boot:run"
    } else {
        Write-Warning "Folder .\$svc not found!"
    }
}

Write-Host "All services are launching at the same time. Check each PowerShell window for logs."