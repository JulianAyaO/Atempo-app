# TEST E2E COMPLETO EN MESA 8
$ErrorActionPreference = "Stop"

# 1. Crear sesión
$session = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/sessions/table/8" -Method POST -UseBasicParsing -ContentType "application/json" | Select-Object -ExpandProperty Content | ConvertFrom-Json
$sid = $session.sessionId
Write-Host "Session: $sid"

# 2. Agregar producto por chat
$body = @{sessionId=$sid; tableId=8; message="quiero 1 guacamole"; idempotencyKey="e2e-1-$(Get-Random)"} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/chat/turn" -Method POST -UseBasicParsing -ContentType "application/json" -Body $body
$order = ($r.Content | ConvertFrom-Json).orderSnapshot
Write-Host "After ADD: Items=$($order.items.Count), Status=$($order.status)"

# 3. Eliminar producto por chat
$body = @{sessionId=$sid; tableId=8; message="ya no quiero el guacamole"; idempotencyKey="e2e-2-$(Get-Random)"} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/chat/turn" -Method POST -UseBasicParsing -ContentType "application/json" -Body $body
$order = ($r.Content | ConvertFrom-Json).orderSnapshot
Write-Host "After REMOVE: Items=$($order.items.Count), Status=$($order.status)"
$removeOk = $order.items.Count -eq 0

# 4. Agregar de nuevo y confirmar
$body = @{sessionId=$sid; tableId=8; message="quiero 2 nachos supremos"; idempotencyKey="e2e-3-$(Get-Random)"} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/chat/turn" -Method POST -UseBasicParsing -ContentType "application/json" -Body $body
$order = ($r.Content | ConvertFrom-Json).orderSnapshot
$oid = $order.orderId
Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid/confirm" -Method POST -UseBasicParsing | Out-Null
Write-Host "After CONFIRM: OrderId=$oid"

# 5. Persistencia (simular salir/volver)
$current = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/table/8/current" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "Persistencia: Status=$($current.status), Items=$($current.items.Count)"
$persistOk = $current.status -eq 'PENDING' -and $current.items.Count -eq 1 -and $current.items[0].quantity -eq 2

# 6. Mesero
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/sessions/$sid/call-waiter" -Method POST -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "Mesero: $($r.message)"

# 7. Estados: IN_PREPARATION → READY → DELIVERED
Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid/status" -Method PATCH -UseBasicParsing -ContentType "application/json" -Body '{"status":"IN_PREPARATION"}' | Out-Null
Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid/status" -Method PATCH -UseBasicParsing -ContentType "application/json" -Body '{"status":"READY"}' | Out-Null
Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid/status" -Method PATCH -UseBasicParsing -ContentType "application/json" -Body '{"status":"DELIVERED"}' | Out-Null
$current = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "After DELIVERED: Status=$($current.status)"

# 8. Solicitar cuenta
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/sessions/$sid/request-payment" -Method POST -UseBasicParsing -ContentType "application/json" -Body '{"tableId":8}' | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "PaymentRequest: Status=$($r.status)"
$paymentOk = $r.status -eq 'PAYMENT_REQUESTED'

# 9. Verificar en payment-requested
$payments = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/payment-requested" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "PaymentRequested list: $($payments.Count) items"

# 10. Pagar
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/$oid/status" -Method PATCH -UseBasicParsing -ContentType "application/json" -Body '{"status":"PAID"}' | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "After PAID: Status=$($r.status)"

# 11. Mesa liberada
$liberated = 0
try { $null = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/table/8/current" -UseBasicParsing -ErrorAction Stop } catch { $liberated = $_.Exception.Response.StatusCode.value__ }
Write-Host "Mesa liberada: $liberated (expected 404)"
$freeOk = $liberated -eq 404

Write-Host ""
Write-Host "=== RESULTADOS ==="
Write-Host "REMOVE_ITEM sincronizado: $removeOk"
Write-Host "Persistencia PEDIDO: $persistOk"
Write-Host "Solicitud CUENTA: $paymentOk"
Write-Host "Liberacion MESA: $freeOk"
Write-Host "FLUJO COMPLETO OK: $($removeOk -and $persistOk -and $paymentOk -and $freeOk)"
