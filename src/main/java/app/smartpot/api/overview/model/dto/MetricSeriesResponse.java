package app.smartpot.api.overview.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Promedios de una variable por intervalo para cada cultivo, listos para compararlos en un gráfico.
 */
public record MetricSeriesResponse(String metric, int hours, int bucketMinutes, List<CropSeries> series) {

    public record CropSeries(String cropId, String name, String type, List<Point> points) {
    }

    public record Point(Instant time, double value) {
    }
}
