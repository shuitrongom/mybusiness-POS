package com.mybusinesssilva.inventory.bakery.application;

import com.mybusinesssilva.inventory.bakery.domain.model.YieldVerdict;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryBatchRepository;
import com.mybusinesssilva.inventory.bakery.domain.port.out.YieldAnalyzerPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Regla simple de detección de desvío: compara el rendimiento total de la tanda
 * contra el promedio móvil de las últimas {@value #N} tandas del mismo tipo y marca
 * {@code HIGH}/{@code LOW} si se aparta más de ±{@value #DEVIATION_THRESHOLD_PCT}.
 *
 * <p>Encapsulada tras {@link YieldAnalyzerPort} para sustituirla por IA sin tocar el
 * resto del módulo.
 */
@Component
public class SimpleMovingAverageYieldAnalyzer implements YieldAnalyzerPort {

    /** Número de tandas del mismo tipo que forman el promedio móvil. */
    static final int N = 10;

    /** Umbral de desvío (±15%). */
    static final BigDecimal DEVIATION_THRESHOLD = new BigDecimal("0.15");

    private final BakeryBatchRepository repository;

    public SimpleMovingAverageYieldAnalyzer(BakeryBatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public YieldVerdict analyze(long batchId) {
        BigDecimal yieldTotal = repository.yieldTotal(batchId);
        List<BigDecimal> history = repository.recentYieldsForSameType(batchId, N);
        return evaluate(yieldTotal, history);
    }

    /**
     * Evalúa el veredicto a partir del rendimiento de la tanda y su histórico. Público
     * en el paquete para poder probar la regla con series fijas sin base de datos.
     */
    YieldVerdict evaluate(BigDecimal yieldTotal, List<BigDecimal> history) {
        if (yieldTotal == null) {
            return new YieldVerdict(null, null, YieldVerdict.Deviation.OK);
        }
        if (history == null || history.isEmpty()) {
            // Sin histórico de referencia no se puede juzgar el desvío.
            return new YieldVerdict(yieldTotal, null, YieldVerdict.Deviation.OK);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal y : history) {
            if (y != null) {
                sum = sum.add(y);
                count++;
            }
        }
        if (count == 0) {
            return new YieldVerdict(yieldTotal, null, YieldVerdict.Deviation.OK);
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        if (avg.signum() == 0) {
            return new YieldVerdict(yieldTotal, avg, YieldVerdict.Deviation.OK);
        }
        BigDecimal upper = avg.add(avg.multiply(DEVIATION_THRESHOLD));
        BigDecimal lower = avg.subtract(avg.multiply(DEVIATION_THRESHOLD));
        YieldVerdict.Deviation deviation;
        if (yieldTotal.compareTo(upper) > 0) {
            deviation = YieldVerdict.Deviation.HIGH;
        } else if (yieldTotal.compareTo(lower) < 0) {
            deviation = YieldVerdict.Deviation.LOW;
        } else {
            deviation = YieldVerdict.Deviation.OK;
        }
        return new YieldVerdict(yieldTotal, avg, deviation);
    }
}
