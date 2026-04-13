# ============================================================================
# Performance Testing Script (PowerShell)
# ============================================================================
# Usage: .\test-performance.ps1 [-TestType light|medium|heavy|all]
# Default: all
# ============================================================================

param(
    [ValidateSet("light", "medium", "heavy", "all")]
    [string]$TestType = "all",
    [string]$BaseUrl = "http://localhost:18045"
)

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "Performance Testing Script" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""

# Function to run a pressure test
function Invoke-PressureTest {
    param(
        [string]$Name,
        [int]$Concurrency,
        [int]$Requests,
        [int]$Timeout
    )
    
    Write-Host "Running: $Name" -ForegroundColor Yellow
    Write-Host "  Concurrency: $Concurrency"
    Write-Host "  Total Requests: $Requests"
    Write-Host "  Timeout: ${Timeout}ms"
    Write-Host ""
    
    $body = @{
        name = $Name
        targetUrl = "$BaseUrl/api/v1/system/info"
        method = "GET"
        concurrency = $Concurrency
        totalRequests = $Requests
        timeoutMillis = $Timeout
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod `
            -Uri "$BaseUrl/api/v1/monitoring/pressure-tests/run" `
            -Method Post `
            -Body $body `
            -ContentType "application/json; charset=utf-8"
        
        # Display key metrics
        $metrics = [PSCustomObject]@{
            Name = $response.name
            SuccessRate = "$($response.successRate)%"
            AvgLatencyMs = "$($response.avgLatencyMs) ms"
            P95LatencyMs = "$($response.p95LatencyMs) ms"
            RequestsPerSecond = $response.requestsPerSecond
            TotalRequests = $response.totalRequests
            SuccessCount = $response.successCount
            FailureCount = $response.failureCount
        }
        
        $metrics | Format-List
        Write-Host ""
        
        return $response
    }
    catch {
        Write-Host "Error running test: $_" -ForegroundColor Red
        return $null
    }
}

# Check if application is running
Write-Host "Checking application health..." -NoNewline
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -ErrorAction Stop
    if ($health.status -eq "UP") {
        Write-Host " ✓ Application is running" -ForegroundColor Green
    }
    else {
        throw "Application status is not UP"
    }
}
catch {
    Write-Host " ✗ Application is not responding at $BaseUrl" -ForegroundColor Red
    Write-Host "Please start the application first:"
    Write-Host "  mvn spring-boot:run"
    exit 1
}

Write-Host ""

# Run tests based on type
switch ($TestType) {
    "light" {
        Invoke-PressureTest -Name "light-load-test" -Concurrency 10 -Requests 50 -Timeout 5000
    }
    "medium" {
        Invoke-PressureTest -Name "medium-load-test" -Concurrency 20 -Requests 100 -Timeout 5000
    }
    "heavy" {
        Invoke-PressureTest -Name "heavy-load-test" -Concurrency 50 -Requests 200 -Timeout 10000
    }
    "all" {
        Invoke-PressureTest -Name "light-load-test" -Concurrency 10 -Requests 50 -Timeout 5000
        Write-Host "----------------------------------------" -ForegroundColor Yellow
        Write-Host ""
        Invoke-PressureTest -Name "medium-load-test" -Concurrency 20 -Requests 100 -Timeout 5000
        Write-Host "----------------------------------------" -ForegroundColor Yellow
        Write-Host ""
        Invoke-PressureTest -Name "heavy-load-test" -Concurrency 50 -Requests 200 -Timeout 10000
    }
}

Write-Host "========================================" -ForegroundColor Green
Write-Host "All tests completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "View detailed results at:"
Write-Host "  $BaseUrl/api/v1/monitoring/pressure-tests/history"
