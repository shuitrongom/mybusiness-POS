package com.mybusinesssilva.inventory.bakery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryBatch;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryBatchRepository;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BatchProductQueryPort;
import com.mybusinesssilva.inventory.bakery.domain.port.out.YieldAnalyzerPort;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.InsufficientRawMaterialException;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pruebas de servicio de la TANDA con todos los puertos mockeados. Verifican el
 * orden MP→pan→persistencia, el rollback (excepción propagada) ante MP insuficiente
 * y ante precio 0, el cálculo de totales y la invariante
 * {@code valueOverInputs == outputValue − inputCost}, y la persistencia del rol del
 * actor como {@code created_role}.
 */
class BakeryBatchServiceTest {

    private static final long BRANCH = 1L;
    private static final long HARINA = 3L;
    private static final long AZUCAR = 7L;
    private static final long CONCHA = 101L;
    private static final long BOLILLO = 102L;

    private BakeryBatchRepository batchRepository;
    private RawMaterialRepository rawMaterialRepository;
    private BatchProductQueryPort productQuery;
    private BakeryUseCase bakeryUseCase;
    private YieldAnalyzerPort yieldAnalyzer;
    private BakeryBatchService service;

    @BeforeEach
    void setUp() {
        batchRepository = mock(BakeryBatchRepository.class);
        rawMaterialRepository = mock(RawMaterialRepository.class);
        productQuery = mock(BatchProductQueryPort.class);
        bakeryUseCase = mock(BakeryUseCase.class);
        yieldAnalyzer = mock(YieldAnalyzerPort.class);
        service = new BakeryBatchService(batchRepository, rawMaterialRepository,
                productQuery, bakeryUseCase, yieldAnalyzer);
    }

    private BakeryBatch batch(String role) {
        return new BakeryBatch(null, null, BRANCH, null, null,
                List.of(new BatchInput(HARINA, new BigDecimal("10"), null, null, null),
                        new BatchInput(AZUCAR, new BigDecimal("3"), null, null, null)),
                List.of(new BatchOutput(CONCHA, new BigDecimal("10"), null, null, null),
                        new BatchOutput(BOLILLO, new BigDecimal("30"), null, null, null)),
                "Hornada matutina", "panadero@x.com", role);
    }

    private void stockDefaults() {
        when(rawMaterialRepository.find(HARINA)).thenReturn(Optional.of(
                new RawMaterial(HARINA, null, "Harina", "kg", new BigDecimal("18"), BigDecimal.ZERO, true)));
        when(rawMaterialRepository.find(AZUCAR)).thenReturn(Optional.of(
                new RawMaterial(AZUCAR, null, "Azúcar", "kg", new BigDecimal("22"), BigDecimal.ZERO, true)));
        when(productQuery.productPrice(CONCHA)).thenReturn(new BigDecimal("15"));
        when(productQuery.productPrice(BOLILLO)).thenReturn(new BigDecimal("10"));
        when(rawMaterialRepository.deductIfEnough(anyLong(), eq(BRANCH), any())).thenReturn(true);
        when(rawMaterialRepository.stock(anyLong(), eq(BRANCH))).thenReturn(new BigDecimal("100"));
        when(batchRepository.insertHeader(eq(BRANCH), any(), any(), any(), any())).thenReturn(57L);
        when(bakeryUseCase.registerProduction(any())).thenReturn(300L);
    }

    @Test
    void registerBatchConsumesRawMaterialThenProducesThenPersists() {
        stockDefaults();

        long id = service.registerBatch(batch("PANADERO"));

        assertThat(id).isEqualTo(57L);
        // MP: dos CONSUME negativos con costo congelado del catálogo y reference BATCH:57.
        verify(rawMaterialRepository).recordMovement(eq(HARINA), eq(BRANCH),
                eq(RawMaterialMovementType.CONSUME), eq(new BigDecimal("-10")), any(),
                eq(new BigDecimal("18")), any(), eq("BATCH:57"), eq("panadero@x.com"));
        verify(rawMaterialRepository).recordMovement(eq(AZUCAR), eq(BRANCH),
                eq(RawMaterialMovementType.CONSUME), eq(new BigDecimal("-3")), any(),
                eq(new BigDecimal("22")), any(), eq("BATCH:57"), eq("panadero@x.com"));
        // Pan: se delega a registerProduction con entryPrice de la ficha.
        ArgumentCaptor<BakeryProduction> prod = ArgumentCaptor.forClass(BakeryProduction.class);
        verify(bakeryUseCase).registerProduction(prod.capture());
        assertThat(prod.getValue().lines()).anyMatch(
                l -> l.productId() == CONCHA && l.entryPrice().compareTo(new BigDecimal("15")) == 0);

        // Cierre: input_cost = 10·18 + 3·22 = 246; output_value = 10·15 + 30·10 = 450;
        // valueOverInputs = 450 − 246 = 204 (invariante).
        ArgumentCaptor<BigDecimal> inputCost = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> outputValue = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> voi = ArgumentCaptor.forClass(BigDecimal.class);
        verify(batchRepository).closeBatch(eq(57L), eq(300L),
                inputCost.capture(), outputValue.capture(), voi.capture());
        assertThat(inputCost.getValue()).isEqualByComparingTo("246");
        assertThat(outputValue.getValue()).isEqualByComparingTo("450");
        assertThat(voi.getValue()).isEqualByComparingTo("204");
        assertThat(voi.getValue()).isEqualByComparingTo(
                outputValue.getValue().subtract(inputCost.getValue()));
    }

    @Test
    void rollbackWhenRawMaterialInsufficient() {
        stockDefaults();
        // La harina no alcanza.
        when(rawMaterialRepository.deductIfEnough(eq(HARINA), eq(BRANCH), any())).thenReturn(false);
        when(rawMaterialRepository.stock(eq(HARINA), eq(BRANCH))).thenReturn(new BigDecimal("4"));

        assertThatThrownBy(() -> service.registerBatch(batch("PANADERO")))
                .isInstanceOf(InsufficientRawMaterialException.class);

        // El pan NUNCA se produce si falla el consumo de MP (orden MP→pan).
        verify(bakeryUseCase, never()).registerProduction(any());
        verify(batchRepository, never()).closeBatch(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void rollbackWhenProductPriceZero() {
        when(productQuery.productPrice(CONCHA)).thenReturn(BigDecimal.ZERO);
        when(productQuery.productPrice(BOLILLO)).thenReturn(new BigDecimal("10"));

        assertThatThrownBy(() -> service.registerBatch(batch("PANADERO")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Captura primero el precio");

        // El precio se valida ANTES de tocar inventario: no se consume MP ni se crea cabecera.
        verify(rawMaterialRepository, never()).deductIfEnough(anyLong(), anyLong(), any());
        verify(batchRepository, never()).insertHeader(anyLong(), any(), any(), any(), any());
    }

    @Test
    void emptyInputsOrOutputsRejected() {
        BakeryBatch noInputs = new BakeryBatch(null, null, BRANCH, null, null,
                List.of(), List.of(new BatchOutput(CONCHA, BigDecimal.ONE, null, null, null)),
                null, "p", "PANADERO");
        assertThatThrownBy(() -> service.registerBatch(noInputs))
                .isInstanceOf(IllegalArgumentException.class);

        BakeryBatch noOutputs = new BakeryBatch(null, null, BRANCH, null, null,
                List.of(new BatchInput(HARINA, BigDecimal.ONE, null, null, null)), List.of(),
                null, "p", "PANADERO");
        assertThatThrownBy(() -> service.registerBatch(noOutputs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createdRoleFlowsAsPanadero() {
        stockDefaults();
        service.registerBatch(batch("PANADERO"));
        // created_role = 'PANADERO' llega al insert de la cabecera.
        verify(batchRepository).insertHeader(eq(BRANCH), any(), eq("panadero@x.com"),
                eq("PANADERO"), eq("Hornada matutina"));
    }
}
