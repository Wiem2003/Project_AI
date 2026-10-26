# Script de démarrage du preevaluation-service en mode local
# Usage: .\start-local.ps1

Write-Host "🚀 Démarrage du preevaluation-service avec RAG" -ForegroundColor Green
Write-Host ""

# Vérifier que le fichier .env existe
if (-Not (Test-Path ".env")) {
    Write-Host "❌ Fichier .env non trouvé!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Créez d'abord le fichier .env à partir de .env.example:" -ForegroundColor Yellow
    Write-Host "  1. Copier .env.example en .env" -ForegroundColor Yellow
    Write-Host "  2. Éditer .env et renseigner votre GROQ_API_KEY" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Pour obtenir une clé API Groq gratuite:" -ForegroundColor Cyan
    Write-Host "  https://console.groq.com/keys" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

# Charger les variables d'environnement depuis .env
Write-Host "📋 Chargement de la configuration depuis .env..." -ForegroundColor Cyan
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        
        # Masquer la clé API dans l'affichage
        if ($name -eq "GROQ_API_KEY" -and $value) {
            $masked = $value.Substring(0, [Math]::Min(8, $value.Length)) + "..."
            Write-Host "  ✓ $name = $masked" -ForegroundColor Gray
        } elseif ($value) {
            Write-Host "  ✓ $name = $value" -ForegroundColor Gray
        }
    }
}

Write-Host ""

# Vérifier que GROQ_API_KEY est définie
$apiKey = [Environment]::GetEnvironmentVariable("GROQ_API_KEY", "Process")
if (-Not $apiKey -or $apiKey.Length -eq 0) {
    Write-Host "❌ GROQ_API_KEY non définie dans .env!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Ajoutez votre clé API Groq dans le fichier .env:" -ForegroundColor Yellow
    Write-Host "  GROQ_API_KEY=gsk_votre_cle_ici" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Pour obtenir une clé gratuite: https://console.groq.com/keys" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

Write-Host "✅ Configuration valide" -ForegroundColor Green
Write-Host ""

# Afficher les informations de démarrage
Write-Host "📦 Service: preevaluation-service" -ForegroundColor Cyan
Write-Host "🌐 Port: 8089" -ForegroundColor Cyan
Write-Host "🤖 Modèle Groq: $([Environment]::GetEnvironmentVariable('GROQ_MODEL', 'Process'))" -ForegroundColor Cyan
Write-Host "🧠 RAG: Activé avec base documentaire CECRL" -ForegroundColor Cyan
Write-Host ""

Write-Host "⏳ Démarrage en cours..." -ForegroundColor Yellow
Write-Host ""

# Démarrer le service
mvn spring-boot:run -Dspring-boot.run.profiles=local
