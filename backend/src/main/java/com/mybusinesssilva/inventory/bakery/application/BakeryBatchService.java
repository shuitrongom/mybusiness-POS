package com.mybusinesssilva.inventory.bakery.application;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryBatch;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import com.mybusinesssilva.inventory.bakery.domain.model.YieldVerdict;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryBatchUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryBatchRepository;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BatchProductQueryPort;
import com.mybusinesssilva.inventory.bakery.domain.port.out.YieldAnalyzerPort;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.InsufficientRawMaterialException;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestación de la TANDA. Vive en un bean DISTINTO de {@code BakeryService} (bean
 * B): la llamada {@code bakeryUseCase.registerProduction(...)} es cross-bean a través
 * del puerto {@link BakeryUseCase}, por lo que pasa por el proxy transaccional de
 * Spring y su propagación REQUIRED se une a la transacción abierta aquí. Las tres
 * patas (MP, pan, tanda) comparten UN solo tx. Queda PROHIBIDO por diseño mover esta
 * orquestación dentro de {@code BakeryService} (self-invocation omite el proxy).
 */
@Service
public class BakeryBatchService implements BakeryBatchUseCase {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    private final BakeryBatchRepository batchRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final BatchProductQueryPort productQuery;
    private final BakeryUseCase bakeryUseCase;
    private final YieldAnalyzerPort yieldAnalyzer;

    public BakeryBatchService(BakeryBatchRepository batchRepository,
                              RawMaterialRepository rawMaterialRepository,
                              BatchProductQueryPort productQuery,
                              BakeryUseCase bakeryUseCase,
                              YieldAnalyzerPort yieldAnalyzer) {
        this.batchRepository = batchRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.productQuery = productQuery;
        this.bakeryUseCase = bakeryUseCase;
        this.yieldAnalyzer = yieldAnalyzer;
    }

    @Override
    @Transactional
    public long registerBatch(BakeryBatch batch) {
        // --- Paso 1: validación de entrada ---
        if (batch.inputs().isEmpty()) {
            throw new IllegalArgumentException("La tanda debe tener al menos un insumo.");
        }
        if (batch.outputs().isEmpty()) {
            throw new IllegalArgumentException("La tanda debe tener al menos un pan producido.");
        }
        long branchId = batch.branchId();

        // --- Paso 2: resolver y congelar precios de ficha (ANTES de tocar inventario) ---
        // fichaPrice se usa como ÚNICA fuente para el entryPrice de la producción y para
        // bakery_batch_output.entry_price, de modo que output_value nunca diverge del pan valorado.
        List<ResolvedOutput> resolvedOutputs = new ArrayList<>();
        for (BatchOutput out : batch.outputs()) {
            if (out.quantity() == null || out.quantity().signum() <= 0) {
                throw new IllegalArgumentException("La cantidad de cada pan debe ser positiva.");
            }
            BigDecimal fichaPrice = productQuery.productPrice(out.productId());
            if (fichaPrice == null || fichaPrice.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Captura primero el precio del artículo antes de registrar su producción.");
            }
            resolvedOutputs.add(new ResolvedOutput(out.productId(), out.quantity(), out.unit(), fichaPrice));
        }

        // Resolver insumos (costo congelado del catálogo) y validar existencia/activo.
        List<ResolvedInput> resolvedInputs = new ArrayList<>();
        for (BatchInput in : batch.inputs()) {
            if (in.quantity() == null || in.quantity().signum() <= 0) {
                throw new IllegalArgumentException("La cantidad de cada insumo debe ser positiva.");
            }
            RawMaterial material = rawMaterialRepository.find(in.rawMaterialId())
                    .orElseThrow(() -> new IllegalArgumentException("Un insumo de la tanda no existe."));
            if (!material.active()) {
                throw new IllegalArgumentException("Un insumo de la tanda está inactivo: " + material.name() + ".");
            }
            BigDecimal unitCost = material.unitCost() == null ? BigDecimal.ZERO : material.unitCost();
            resolvedInputs.add(new ResolvedInput(
                    material.id(), material.name(), in.quantity(), material.unit(), unitCost));
        }

        // --- Paso 3: insertar cabecera para obtener batchId/folio ---
        LocalDate batchDate = LocalDate.now(MX);
        long batchId = batchRepository.insertHeader(
                branchId, batchDate, batch.createdBy(), batch.createdRole(), batch.notes());

        // --- Paso 4: consumo de MP (barrera dura de existencia) + kardex ---
        for (ResolvedInput in : resolvedInputs) {
            boolean ok = rawMaterialRepository.deductIfEnough(in.rawMaterialId(), branchId, in.quantity());
            if (!ok) {
                BigDecimal available = rawMaterialRepository.stock(in.rawMaterialId(), branchId);
                throw new InsufficientRawMaterialException(
                        in.rawMaterialId(), in.name(), available, in.quantity());
            }
            BigDecimal balance = rawMaterialRepository.stock(in.rawMaterialId(), branchId);
            rawMaterialRepository.recordMovement(in.rawMaterialId(), branchId,
                    RawMaterialMovementType.CONSUME, in.quantity().negate(), balance,
                    in.unitCost(), "Consumo de tanda", "BATCH:" + batchId, batch.createdBy());
        }

        // --- Paso 5: pata pan (cross-bean, se une al mismo tx vía proxy) ---
        List<BakeryProductionLine> lines = new ArrayList<>();
        for (ResolvedOutput out : resolvedOutputs) {
            lines.add(new BakeryProductionLine(null, out.productId(), null,
                    out.quantity(), out.unit(), out.fichaPrice(), BigDecimal.ZERO,
                    out.fichaPrice().multiply(out.quantity())));
        }
        BigDecimal totalQty = lines.stream().map(BakeryProductionLine::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = lines.stream().map(BakeryProductionLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BakeryProduction production = new BakeryProduction(
                null, null, branchId, "PRODUCED", batchDate, totalQty, totalValue,
                batch.notes(), batch.createdBy(), batch.createdRole(), null, lines);
        long productionId = bakeryUseCase.registerProduction(production);

        // --- Paso 6: cerrar la tanda (totales + líneas) ---
        BigDecimal inputCost = BigDecimal.ZERO;
        for (ResolvedInput in : resolvedInputs) {
            inputCost = inputCost.add(in.lineCost());
        }
        BigDecimal outputValue = BigDecimal.ZERO;
        for (ResolvedOutput out : resolvedOutputs) {
            outputValue = outputValue.add(out.lineValue());
        }
        BigDecimal valueOverInputs = outputValue.subtract(inputCost);
        batchRepository.closeBatch(batchId, productionId, inputCost, outputValue, valueOverInputs);

        for (ResolvedInput in : resolvedInputs) {
            batchRepository.insertInput(batchId, new BatchInput(
                    in.rawMaterialId(), in.quantity(), in.unit(), in.unitCost(), in.lineCost()));
        }
        for (ResolvedOutput out : resolvedOutputs) {
            batchRepository.insertOutput(batchId, new BatchOutput(
                    out.productId(), out.quantity(), out.unit(), out.fichaPrice(), out.lineValue()));
        }

        // --- Paso 7 ---
        return batchId;
    }

    @Override
    public List<Map<String, Object>> myBatchesToday(long branchId, String actor) {
        return batchRepository.myBatchesToday(branchId, actor, LocalDate.now(MX));
    }

    @Override
    public List<Map<String, Object>> listBatches(Long branchId, LocalDate from, LocalDate to, int limit) {
        List<Map<String, Object>> batches = batchRepository.listBatches(branchId, from, to, limit);
        for (Map<String, Object> b : batches) {
            long batchId = ((Number) b.get("batchId")).longValue();
            YieldVerdict verdict = yieldAnalyzer.analyze(batchId);
            b.put("yieldTotal", verdict.yieldTotal());
            b.put("movingAvg", verdict.movingAvg());
            b.put("deviation", verdict.deviation().name());
        }
        return batches;
    }

    @Override
    public Map<String, Object> batchDetail(long batchId) {
        Map<String, Object> detail = batchRepository.batchDetail(batchId);
        if (detail == null) {
            throw new IllegalArgumentException("La tanda indicada no existe.");
        }
        YieldVerdict verdict = yieldAnalyzer.analyze(batchId);
        detail.put("yieldTotal", verdict.yieldTotal());
        detail.put("movingAvg", verdict.movingAvg());
        detail.put("deviation", verdict.deviation().name());
        return detail;
    }

    @Override
    public List<Map<String, Object>> yieldReport(Long branchId, LocalDate from, LocalDate to) {
        return batchRepository.yieldReport(branchId, from, to);
    }

    @Override
    public List<Map<String, Object>> anomalies(Long branchId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(batchRepository.backupProductions(branchId, from, to));
        result.addAll(batchRepository.manualAdjustments(branchId, from, to));
        return result;
    }

    // ---- Tipos internos de trabajo (congelan los valores resueltos) ----

    private record ResolvedInput(long rawMaterialId, String name, BigDecimal quantity,
                                 String unit, BigDecimal unitCost) {
        BigDecimal lineCost() {
            return unitCost.multiply(quantity);
        }
    }

    private record ResolvedOutput(long productId, BigDecimal quantity, String unit, BigDecimal fichaPrice) {
        BigDecimal lineValue() {
            return fichaPrice.multiply(quantity);
        }
    }
}
