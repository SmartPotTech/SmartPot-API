package app.smartpot.api.exception;

import org.bson.types.ObjectId;

public final class ObjectIds {

    private ObjectIds() {
    }

    public static String require(String id, String notFoundMessage) {
        if (id == null || !ObjectId.isValid(id)) {
            throw ApiException.notFound(notFoundMessage);
        }
        return id;
    }

    public static boolean isValid(String id) {
        return id != null && ObjectId.isValid(id);
    }
}
