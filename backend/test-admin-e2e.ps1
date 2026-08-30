# TEST E2E ADMIN: Catalog CRUD flow
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

Write-Host "=== Admin E2E: Create Product -> Appears Everywhere ==="

# 1. Get initial categories and ingredients
$categories = Invoke-WebRequest -Uri "$base/api/catalog/categories" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
$ingredients = Invoke-WebRequest -Uri "$base/api/catalog/ingredients" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "Categories: $($categories.Count), Ingredients: $($ingredients.Count)"

# 2. Create a new category
$catBody = @{name="E2E Test Category";description="Auto-generated test";displayOrder=999} | ConvertTo-Json
$cat = Invoke-WebRequest -Uri "$base/api/catalog/categories" -Method POST -UseBasicParsing -ContentType "application/json" -Body $catBody | Select-Object -ExpandProperty Content | ConvertFrom-Json
$catId = $cat.id
Write-Host "Created category: $($cat.name) (ID=$catId)"

# 3. Create a new ingredient
$ingBody = @{name="E2E Test Ingredient";unit="gramos";allergens=@()} | ConvertTo-Json
$ing = Invoke-WebRequest -Uri "$base/api/catalog/ingredients" -Method POST -UseBasicParsing -ContentType "application/json" -Body $ingBody | Select-Object -ExpandProperty Content | ConvertFrom-Json
$ingId = $ing.id
Write-Host "Created ingredient: $($ing.name) (ID=$ingId)"

# 4. Create a new product in the new category
$prodBody = @{
    name="E2E Test Product"
    description="Producto de prueba end-to-end"
    price=99999
    categoryId=$catId
    allergens=@()
    ingredients=@(@{ingredientId=$ingId;type="BASE";quantityRequired=100;extraPrice=0})
} | ConvertTo-Json
$prod = Invoke-WebRequest -Uri "$base/api/catalog/products" -Method POST -UseBasicParsing -ContentType "application/json" -Body $prodBody | Select-Object -ExpandProperty Content | ConvertFrom-Json
$prodId = $prod.id
Write-Host "Created product: $($prod.name) (ID=$prodId, Active=$($prod.active))"

# 5. Verify product appears in /menu
$menu = Invoke-WebRequest -Uri "$base/api/catalog/menu" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
$inMenu = $menu | Where-Object { $_.products | Where-Object { $_.id -eq $prodId } }
$menuOk = $inMenu -ne $null
Write-Host "In /menu: $menuOk"

# 6. Verify product appears in /menu-with-stock
$menuWs = Invoke-WebRequest -Uri "$base/api/catalog/menu-with-stock" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
$inMenuWs = $menuWs | Where-Object { $_.products | Where-Object { $_.id -eq $prodId } }
$menuWsOk = $inMenuWs -ne $null
Write-Host "In /menu-with-stock: $menuWsOk"

# 7. Verify product appears in /products
$products = Invoke-WebRequest -Uri "$base/api/catalog/products" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
$inProducts = $products | Where-Object { $_.id -eq $prodId }
$productsOk = $inProducts -ne $null
Write-Host "In /products: $productsOk"

# 8. Toggle product active=false
$toggle = Invoke-WebRequest -Uri "$base/api/catalog/products/$prodId" -Method PUT -UseBasicParsing -ContentType "application/json" -Body '{"active":false}' | Select-Object -ExpandProperty Content | ConvertFrom-Json
$toggleOk = $toggle.active -eq $false
Write-Host "Toggle active=false: $toggleOk"

# 9. Verify product no longer in /menu (only active products)
$menuAfter = Invoke-WebRequest -Uri "$base/api/catalog/menu" -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
$inMenuAfter = $menuAfter | Where-Object { $_.products | Where-Object { $_.id -eq $prodId } }
$menuAfterOk = $inMenuAfter -eq $null
Write-Host "Not in /menu after deactivate: $menuAfterOk"

# 10. Cleanup: hard-delete would require DB access; soft-delete already done
Write-Host ""
Write-Host "=== RESULTADOS ==="
Write-Host "Create category: OK (ID=$catId)"
Write-Host "Create ingredient: OK (ID=$ingId)"
Write-Host "Create product: OK (ID=$prodId)"
Write-Host "Appears in /menu: $menuOk"
Write-Host "Appears in /menu-with-stock: $menuWsOk"
Write-Host "Appears in /products: $productsOk"
Write-Host "Toggle active: $toggleOk"
Write-Host "Disappears from /menu: $menuAfterOk"
Write-Host "ADMIN E2E OK: $($menuOk -and $menuWsOk -and $productsOk -and $toggleOk -and $menuAfterOk)"
