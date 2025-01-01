package smartpot.com.api.Crops.Model.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Status {
    Dead, Extreme_decomposition, Severe_deterioration, Moderate_deterioration, Healthy_state, intermittent,
    Moderate_health, Good_health, Very_healthy, Excellent, Perfect_plant, Unknown;

    /**
     * Obtiene la lista de nombres de estados de cultivos definidos en el sistema.
     *
     * @return Una lista con los nombres de los estados.
     */
    public static List<String> getStatusNames() {
        return Arrays.stream(Status.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
