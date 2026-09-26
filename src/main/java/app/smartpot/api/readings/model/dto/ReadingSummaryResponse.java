package app.smartpot.api.readings.model.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Mínimo, promedio y máximo por variable en la ventana pedida.
 */
public record ReadingSummaryResponse(Instant from, Instant to, long count, Map<String, MetricSummary> metrics) {

    public record MetricSummary(Double min, Double avg, Double max) {
    }
}
