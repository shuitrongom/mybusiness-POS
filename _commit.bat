@echo off
cd /d "c:\Users\Serch\Documents\My Bussines Pro\mybusiness-POS\.worktrees\producto-rapido"
echo === BRANCH ===
git rev-parse --abbrev-ref HEAD
echo === STATUS BEFORE ===
git status -s
echo === ADD ===
git add "backend/src/main/java/com/mybusinesssilva/sales/domain/model/SaleLine.java" "backend/src/main/resources/db/migration/tenant/V31__sale_line_free_item.sql" "backend/src/main/java/com/mybusinesssilva/sales/application/SaleService.java" "backend/src/main/java/com/mybusinesssilva/sales/domain/port/out/SaleRepository.java" "backend/src/main/java/com/mybusinesssilva/sales/adapters/out/persistence/JdbcSaleRepository.java" "backend/src/main/java/com/mybusinesssilva/sales/adapters/in/rest/SaleController.java" "backend/src/main/java/com/mybusinesssilva/bi/application/BiService.java" "backend/src/main/java/com/mybusinesssilva/sales/application/SalesReturnService.java" "backend/src/test/java/com/mybusinesssilva/sales/domain/SaleTest.java" ".agents/tasks/producto-rapido/features/FEAT-001.json"
echo ADD_EXIT=%errorlevel%
echo === STAGED ===
git diff --cached --name-only
echo === COMMIT ===
git commit -m "feat(sales): allow free product-less sale lines (backend)"
echo COMMIT_EXIT=%errorlevel%
echo === LOG ===
git log --oneline -1
