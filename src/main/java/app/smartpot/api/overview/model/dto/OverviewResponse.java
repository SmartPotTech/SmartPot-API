package app.smartpot.api.overview.model.dto;

import app.smartpot.api.crops.model.dto.CropResponse;

import java.util.List;

/**
 * Panel general: totales de la cuenta y cada cultivo con su última lectura.
 */
public record OverviewResponse(Totals totals, List<CropResponse> crops) {

    /**
     * needsAttention: cultivos desconectados o con salud por debajo de 70.
     */
    public record Totals(int crops, int online, int automated, Double averageHealth, int needsAttention,
                         long unreadAlerts, long commandsLast24h) {
    }
}
