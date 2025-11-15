package app.smartpot.api.commands.repository;

import app.smartpot.api.commands.model.entity.Command;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
public interface CommandRepository extends MongoRepository<Command, ObjectId> {

    // Búsqueda por tipo de comando
    @Query("{ 'commandType' : { $regex: ?0, $options: 'i' } }")
    List<Command> findByCommandType(String commandType);

    // Búsqueda por estado
    @Query("{ 'status' : ?0 }")
    List<Command> findByStatus(String status);

    // Búsqueda por cultivo
    @Query("{ 'crop' : ?0 }")
    List<Command> findByCropId(ObjectId cropId);

    // Búsqueda por cultivo y estado
    @Query("{ 'crop' : ?0, 'status' : ?1 }")
    List<Command> findByCropIdAndStatus(ObjectId cropId, String status);

    // Búsqueda por rango de fechas de creación
    @Query("{ 'dateCreated' : { $gte: ?0, $lte: ?1 } }")
    List<Command> findByDateCreatedBetween(Date startDate, Date endDate);

    // Búsqueda por rango de fechas de ejecución
    @Query("{ 'dateExecuted' : { $gte: ?0, $lte: ?1 } }")
    List<Command> findByDateExecutedBetween(Date startDate, Date endDate);

    // Búsqueda de comandos pendientes por cultivo
    @Query("{ 'crop' : ?0, 'status' : 'PENDING' }")
    List<Command> findPendingCommandsByCropId(ObjectId cropId);

    // Búsqueda de comandos ejecutados por cultivo
    @Query("{ 'crop' : ?0, 'status' : 'EXECUTED' }")
    List<Command> findExecutedCommandsByCropId(ObjectId cropId);

    // Búsqueda de comandos fallidos por cultivo
    @Query("{ 'crop' : ?0, 'status' : 'FAILED' }")
    List<Command> findFailedCommandsByCropId(ObjectId cropId);

    // Búsqueda por tipo de comando y estado
    @Query("{ 'commandType' : ?0, 'status' : ?1 }")
    List<Command> findByCommandTypeAndStatus(String commandType, String status);

    // Contar comandos por estado y cultivo
    @Query(value = "{ 'crop' : ?0, 'status' : ?1 }", count = true)
    long countByCropIdAndStatus(ObjectId cropId, String status);

    // Eliminar comandos por cultivo
    @Transactional
    @Query(value = "{ 'crop' : ?0 }")
    void deleteCommandsByCropId(ObjectId cropId);

    // Búsqueda de últimos comandos por cultivo
    @Query(value = "{ 'crop' : ?0 }", sort = "{ 'dateCreated' : -1 }")
    List<Command> findLastCommandsByCropId(ObjectId cropId);

    @Transactional
    @Query("{ '_id' : ?0 }")
    Command updateComand(ObjectId id, Command command);
}