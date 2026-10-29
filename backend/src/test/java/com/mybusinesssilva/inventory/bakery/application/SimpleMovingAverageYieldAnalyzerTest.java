package com.mybusinesssilva.inventory.bakery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mybusinesssilva.inventory.bakery.domain.model.YieldVerdict;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryBatchRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la regla de desvío con series fijas (umbral ±15%).
 */
class SimpleMovingAverageYieldAnalyzerTest {

    private SimpleMovingAverageYieldAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SimpleMovingAverageYieldAnalyzer(mock(BakeryBatchRepository.class));
    }

    private List<BigDecimal> history(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void withinThresholdIsOk() {
        // Promedio = 3.0; 3.2 cae dentro de ±15% (2.55–3.45).
        YieldVerdict v = analyzer.evaluate(new BigDecimal("3.2"), history("3.0", "3.0", "3.0"));
        assertThat(v.deviation()).isEqualTo(YieldVerdict.Deviation.OK);
        assertThat(v.movingAvg()).isEqualByComparingTo("3.0");
    }

    @Test
    void aboveThresholdIsHigh() {
        // Promedio = 3.0; 3.6 supera el +15% (3.45).
        YieldVerdict v = analyzer.evaluate(new BigDecimal("3.6"), history("3.0", "3.0", "3.0"));
        assertThat(v.deviation()).isEqualTo(YieldVerdict.Deviation.HIGH);
    }

    @Test
    void belowThresholdIsLow() {
        // Promedio = 3.0; 2.4 cae por debajo del −15% (2.55).
        YieldVerdict v = analyzer.evaluate(new BigDecimal("2.4"), history("3.0", "3.0", "3.0"));
        assertThat(v.deviation()).isEqualTo(YieldVerdict.Deviation.LOW);
    }

    @Test
    void noHistoryIsOk() {
        YieldVerdict v = analyzer.evaluate(new BigDecimal("3.0"), List.of());
        assertThat(v.deviation()).isEqualTo(YieldVerdict.Deviation.OK);
        assertThat(v.movingAvg()).isNull();
    }

    @Test
    void nullYieldIsOkWithoutNpe() {
        YieldVerdict v = analyzer.evaluate(null, history("3.0"));
        assertThat(v.deviation()).isEqualTo(YieldVerdict.Deviation.OK);
        assertThat(v.yieldTotal()).isNull();
    }
}
