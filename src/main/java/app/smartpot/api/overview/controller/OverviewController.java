package app.smartpot.api.overview.controller;

import app.smartpot.api.ai.model.dto.FleetResponse;
import app.smartpot.api.overview.model.dto.MetricSeriesResponse;
import app.smartpot.api.overview.model.dto.OverviewResponse;
import app.smartpot.api.overview.service.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/overview")
@Tag(name = "Panel general", description = "Todos los cultivos de la cuenta: totales, comparativas y análisis de la IA")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    @Operation(summary = "Resumen de la cuenta", description = "Totales y cada cultivo con su última lectura")
    public OverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return overviewService.overview(jwt.getSubject());
    }

    @GetMapping("/series")
    @Operation(summary = "Comparar una variable entre cultivos",
            description = "Promedios por intervalo de temperature, humidity, brightness, ph, tds, soilMoisture o "
                    + "atmosphere en las últimas horas (1 a 168)")
    public MetricSeriesResponse series(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "temperature") String metric,
                                       @RequestParam(defaultValue = "24") int hours) {
        return overviewService.series(jwt.getSubject(), metric, hours);
    }

    @GetMapping("/fleet")
    @Operation(summary = "Análisis de todos los cultivos",
            description = "Ranking por salud, problemas compartidos del entorno, grupos por condiciones similares y "
                    + "acciones sugeridas en bloque")
    public FleetResponse fleet(@AuthenticationPrincipal Jwt jwt) {
        return overviewService.fleet(jwt.getSubject());
    }
}
