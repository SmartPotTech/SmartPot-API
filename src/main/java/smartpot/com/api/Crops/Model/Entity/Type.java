package smartpot.com.api.Crops.Model.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Type {
    TOMATO, LETTUCE;

    public static List<String> getTypeNames() {
        return Arrays.stream(Type.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
