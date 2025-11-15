package app.smartpot.api.crops.service;

import app.smartpot.api.crops.mapper.CropMapper;
import app.smartpot.api.crops.model.dto.CropDTO;
import app.smartpot.api.crops.model.entity.CropStatus;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.repository.CropRepository;
import app.smartpot.api.crops.validator.CropValidator;
import app.smartpot.api.users.service.UserService;
import jakarta.validation.ValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Servicio que gestiona las operaciones relacionadas con los cultivos.
 * Proporciona métodos para crear, leer, actualizar y eliminar cultivos,
 * así como búsquedas específicas por diferentes criterios.
 */
@Slf4j
@Data
@Builder
@Service
public class CropServiceImpl implements CropService {

    private final CropRepository repositoryCrop;
    private final UserService serviceUser;
    private final CropMapper mapperCrop;
    private final CropValidator validatorCrop;

    /**
     * Constructor del servicio de cultivos.
     *
     * <p>Inyecta las dependencias necesarias para realizar las operaciones relacionadas con los cultivos,
     * incluyendo el repositorio de cultivos {@link CropRepository}, el servicio de usuarios {@link UserService},
     * el convertidor de cultivos {@link CropMapper}, y el validador de cultivos {@link CropValidator}.</p>
     *
     * @param repositoryCrop El repositorio que maneja las operaciones de base de datos para cultivos.
     * @param serviceUser    El servicio de usuarios, utilizado para interactuar con los detalles de los usuarios.
     * @param mapperCrop     El convertidor que convierte las entidades de cultivos a objetos DTO correspondientes.
     * @param validatorCrop  El validador que valida los datos relacionados con los cultivos.
     * @see CropRepository
     * @see UserService
     * @see CropMapper
     * @see CropValidator
     */
    @Autowired
    public CropServiceImpl(CropRepository repositoryCrop, UserService serviceUser, CropMapper mapperCrop, CropValidator validatorCrop) {
        this.repositoryCrop = repositoryCrop;
        this.serviceUser = serviceUser;
        this.mapperCrop = mapperCrop;
        this.validatorCrop = validatorCrop;
    }

    /**
     * Crea un nuevo cultivo en la base de datos a partir de un objeto {@link CropDTO}.
     *
     * <p>Este método recibe un objeto {@link CropDTO}, valida el tipo del cultivo y el ID del usuario
     * asociado utilizando el validador {@link CropValidator}. Si las validaciones son correctas, se asigna el estado
     * por defecto "Unknown" al cultivo y se persiste en la base de datos utilizando el repositorio {@link CropRepository}.
     * Finalmente, el método mapea la entidad guardada nuevamente a un {@link CropDTO} para devolverlo como respuesta.</p>
     *
     * <p>Si las validaciones fallan, se lanza una {@link ValidationException} con los detalles del error.
     * Si el cultivo ya existe, se lanza una {@link Exception} indicando que el cultivo no puede ser creado.</p>
     *
     * @param cropDTO El objeto {@link CropDTO} que contiene los datos del cultivo a crear. Este parámetro es obligatorio.
     * @return El objeto {@link CropDTO} que representa el cultivo creado, con el estado actualizado a "Unknown".
     * @throws Exception           Si el cultivo ya existe o si ocurre algún otro error durante la creación.
     * @throws ValidationException Si las validaciones del tipo o ID del usuario fallan según el validador {@link CropValidator}.
     * @see CropDTO
     * @see CropValidator
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Transactional
    @CachePut(value = "crops", key = "#cropDTO.id")
    public CropDTO createCrop(CropDTO cropDTO) throws Exception {
        return Optional.of(cropDTO)
                .map(ValidCropDTO -> {
                    validatorCrop.validateType(ValidCropDTO.getType());
                    try {
                        serviceUser.getUserById(ValidCropDTO.getUser());
                    } catch (Exception e) {
                        throw new ValidationException(e.getMessage() + ", asocia el cultivo a un usuario existente");
                    }

                    if (!validatorCrop.isValid()) {
                        throw new ValidationException(validatorCrop.getErrors().toString());
                    }
                    validatorCrop.Reset();
                    return ValidCropDTO;
                })
                .map(dto -> {
                    dto.setStatus("Unknown");
                    return dto;
                })
                .map(mapperCrop::toEntity)
                .map(repositoryCrop::save)
                .map(mapperCrop::toDTO)
                .orElseThrow(() -> new Exception("El cultivo ya existe"));
    }

    /**
     * Obtiene todos los cultivos registrados en la base de datos y los convierte en objetos DTO.
     *
     * <p>Este método consulta todos los cultivos almacenados en la base de datos mediante el repositorio {@link CropRepository}.
     * Si la lista de cultivos está vacía, se lanza una excepción. Los cultivos obtenidos se mapean a objetos
     * {@link CropDTO} utilizando el convertidor {@link CropMapper}.</p>
     *
     * @return Una lista de objetos {@link CropDTO} que representan todos los cultivos en la base de datos.
     * @throws Exception Si no existen cultivos registrados en la base de datos.
     * @see CropDTO
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Cacheable(value = "crops", key = "'all_crops'")
    public List<CropDTO> getAllCrops() throws Exception {
        return Optional.of(repositoryCrop.findAll())
                .filter(crops -> !crops.isEmpty())
                .map(crops -> crops.stream()
                        .map(mapperCrop::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún cultivo"));
    }

    /**
     * Obtiene un cultivo de la base de datos a partir de su identificador.
     *
     * <p>Este método busca un cultivo en la base de datos utilizando el ID proporcionado.
     * Primero, valida que el ID sea válido utilizando el validador {@link CropValidator}.
     * Si el ID es válido, realiza una búsqueda en la base de datos usando el repositorio {@link CropRepository}.
     * Si el cultivo existe, se convierte a un objeto {@link CropDTO} utilizando el convertidor {@link CropMapper}.
     * Si el cultivo no existe o el ID no es válido, se lanza una excepción correspondiente.</p>
     *
     * @param id El identificador del cultivo que se desea obtener. El ID debe ser una cadena que representa un {@link ObjectId}.
     * @return Un objeto {@link CropDTO} que representa el cultivo encontrado.
     * @throws Exception           Si el cultivo no existe en la base de datos o si el ID no es válido.
     * @throws ValidationException Si el ID proporcionado no es válido según las reglas de validación del validador {@link CropValidator}.
     * @see CropDTO
     * @see CropValidator
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Cacheable(value = "crops", key = "'id_'+#id")
    public CropDTO getCropById(String id) throws Exception {
        return Optional.of(id)
                .map(ValidCropId -> {
                    validatorCrop.validateId(ValidCropId);
                    if (!validatorCrop.isValid()) {
                        throw new ValidationException(validatorCrop.getErrors().toString());
                    }
                    validatorCrop.Reset();
                    return new ObjectId(ValidCropId);
                })
                .map(repositoryCrop::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(mapperCrop::toDTO)
                .orElseThrow(() -> new Exception("El cultivo no existe"));
    }

    /**
     * Obtiene los cultivos asociados a un usuario específico mediante su ID.
     *
     * <p>Este método busca todos los cultivos relacionados con un usuario en la base de datos utilizando el
     * ID del usuario. Primero, valida que el ID del usuario sea válido llamando al servicio de usuarios {@link UserService}.
     * Si el usuario existe, se realiza una búsqueda de los cultivos asociados a ese usuario a través del repositorio {@link CropRepository}.
     * Si se encuentran cultivos, se mapean a objetos {@link CropDTO} mediante el convertidor {@link CropMapper}.
     * Si el usuario no tiene cultivos o el ID del usuario es inválido, se lanza una excepción correspondiente.</p>
     *
     * @param id El identificador del usuario cuyos cultivos se desean obtener. El ID debe ser una cadena que representa un {@link ObjectId}.
     * @return Una lista de objetos {@link CropDTO} que representan los cultivos asociados al usuario.
     * @throws Exception Si el usuario no tiene cultivos o si el ID del usuario es inválido o no existe.
     * @see CropDTO
     * @see UserService
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Cacheable(value = "crops", key = "'user_'+#id")
    public List<CropDTO> getCropsByUser(String id) throws Exception {
        return Optional.of(serviceUser.getUserById(id))
                .map(validUser -> new ObjectId(validUser.getId()))
                .map(repositoryCrop::findByUser)
                .filter(crops -> !crops.isEmpty())
                .map(crops -> crops.stream()
                        .map(mapperCrop::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No tiene ningún cultivo"));
    }

    /**
     * Cuenta la cantidad de cultivos asociados a un usuario específico.
     *
     * <p>Este método obtiene la lista de cultivos asociados a un usuario mediante su ID utilizando el método
     * {@link #getCropsByUser(String)}, y luego cuenta la cantidad de cultivos encontrados. Si el usuario no tiene cultivos,
     * el método devolverá un valor de 0.</p>
     *
     * @param id El identificador del usuario cuyos cultivos se desean contar. El ID debe ser una cadena que representa un {@link ObjectId}.
     * @return El número de cultivos asociados al usuario especificado.
     * @throws Exception Sí ocurre algún error al obtener los cultivos del usuario.
     * @see #getCropsByUser(String)
     */
    @Override
    @Cacheable(value = "crops", key = "'count_user_'+#id")
    public long countCropsByUser(String id) throws Exception {
        return getCropsByUser(id).size();
    }

    /**
     * Obtiene una lista de cultivos de la base de datos según el tipo proporcionado.
     *
     * <p>Este método busca cultivos en la base de datos utilizando el tipo de cultivo proporcionado como parámetro.
     * Primero, valida que el tipo de cultivo sea válido mediante el validador {@link CropValidator}.
     * Si el tipo es válido, realiza una búsqueda en la base de datos usando el repositorio {@link CropRepository}.
     * Si se encuentran cultivos con el tipo proporcionado, se mapean a objetos {@link CropDTO} usando el convertidor {@link CropMapper}.
     * Si no se encuentran cultivos o si el tipo no es válido, se lanza una excepción correspondiente.</p>
     *
     * @param type El tipo de cultivo que se desea obtener.
     * @return Una lista de objetos {@link CropDTO} que representan los cultivos encontrados con el tipo proporcionado.
     * @throws Exception           Si no se encuentran cultivos con el tipo proporcionado o si ocurre algún otro error.
     * @throws ValidationException Si el tipo de cultivo proporcionado no es válido según las reglas de validación del validador {@link CropValidator}.
     * @see CropDTO
     * @see CropValidator
     * @see CropRepository
     * @see CropMapper
     * @see CropServiceImpl
     */
    @Override
    @Cacheable(value = "crops", key = "'type_'+#type")
    public List<CropDTO> getCropsByType(String type) throws Exception {
        return Optional.of(type)
                .map(ValidType -> {
                    validatorCrop.validateType(ValidType);
                    if (!validatorCrop.isValid()) {
                        throw new ValidationException(validatorCrop.getErrors().toString());
                    }
                    validatorCrop.Reset();
                    return ValidType;
                })
                .map(repositoryCrop::findByType)
                .filter(crops -> !crops.isEmpty())
                .map(crops -> crops.stream()
                        .map(mapperCrop::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existen cultivos"));
    }

    /**
     * Obtiene una lista de todos los tipos de cultivo registrados en el sistema.
     *
     * <p>Este método consulta todos los tipos de cultivo disponibles. Si no se encuentran tipos de cultivo,
     * se lanza una excepción que indica que no existen tipos registrados.</p>
     *
     * @return Una lista de cadenas {@link String} que representan los nombres de los tipos de cultivo encontrados.
     * @throws Exception Si ocurre un error al obtener los tipos de cultivo o si no se encuentran tipos registrados.
     * @see String
     * @see CropType
     */
    @Override
    @Cacheable(value = "crops", key = "'all_types'")
    public List<String> getAllTypes() throws Exception {
        return Optional.of(
                        Arrays.stream(CropType.values())
                                .map(Enum::name)
                                .collect(Collectors.toList())
                )
                .filter(types -> !types.isEmpty())
                .orElseThrow(() -> new Exception("No existe ningún tipo de cultivo"));
    }


    /**
     * Obtiene una lista de cultivos de la base de datos según el estado proporcionado.
     *
     * <p>Este método busca cultivos en la base de datos utilizando el estado de cultivo proporcionado como parámetro.
     * Primero, valida que el estado sea válido mediante el validador {@link CropValidator}. Si el estado es válido,
     * realiza una búsqueda en la base de datos usando el repositorio {@link CropRepository}. Si se encuentran cultivos con
     * el estado proporcionado, se mapean a objetos {@link CropDTO} utilizando el convertidor {@link CropMapper}.
     * Si no se encuentran cultivos o si el estado no es válido, se lanza una excepción correspondiente.</p>
     *
     * @param status El estado del cultivo que se desea obtener.
     * @return Una lista de objetos {@link CropDTO} que representan los cultivos encontrados con el estado proporcionado.
     * @throws Exception           Si no se encuentran cultivos con el estado proporcionado o si ocurre algún otro error.
     * @throws ValidationException Si el estado proporcionado no es válido según las reglas de validación del validador {@link CropValidator}.
     * @see CropDTO
     * @see CropValidator
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Cacheable(value = "crops", key = "'status_'+#status")
    public List<CropDTO> getCropsByStatus(String status) throws Exception {
        return Optional.of(status)
                .map(ValidStatus -> {
                    validatorCrop.validateStatus(ValidStatus);
                    if (!validatorCrop.isValid()) {
                        throw new ValidationException(validatorCrop.getErrors().toString());
                    }
                    validatorCrop.Reset();
                    return ValidStatus;
                })
                .map(repositoryCrop::findByStatus)
                .filter(crops -> !crops.isEmpty())
                .map(crops -> crops.stream()
                        .map(mapperCrop::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existen cultivos"));
    }

    /**
     * Obtiene una lista de todos los estados de cultivo registrados en la base de datos.
     *
     * <p>Este método consulta los estados de cultivo disponibles en la base de datos. Si no se encuentran estados de cultivo,
     * se lanza una excepción que indica que no existen estados registrados.</p>
     *
     * @return Una lista de cadenas {@link String} que representan los estados de cultivo encontrados.
     * @throws Exception Si ocurre un error al buscar los estados de cultivo o si no se encuentran estados registrados.
     * @see String
     * @see CropStatus
     */
    @Override
    @Cacheable(value = "crops", key = "'all_status'")
    public List<String> getAllStatus() throws Exception {
        return Optional.of(
                        Arrays.stream(CropStatus.values())
                                .map(Enum::name)
                                .collect(Collectors.toList())
                )
                .filter(status -> !status.isEmpty())
                .orElseThrow(() -> new Exception("No existe ningún estados para los cultivos"));
    }

    /**
     * Actualiza un cultivo existente en la base de datos con los nuevos datos proporcionados en un objeto {@link CropDTO}.
     *
     * <p>Este método recibe el ID del cultivo a actualizar y un objeto {@link CropDTO} con los nuevos datos.
     * Primero, busca el cultivo existente usando el ID proporcionado. Luego, actualiza los campos que no sean
     * nulos en el objeto {@link CropDTO}, manteniendo los valores existentes cuando el campo es nulo. Después,
     * valida los nuevos datos utilizando el validador {@link CropValidator}. Si las validaciones son correctas, el cultivo
     * se actualiza en la base de datos. Finalmente, el método devuelve el objeto actualizado {@link CropDTO}.</p>
     *
     * <p>Si las validaciones no pasan o si ocurre un error durante el proceso de actualización, se lanza una
     * {@link ValidationException} o una {@link Exception}, respectivamente.</p>
     *
     * @param id         El ID único del cultivo que se desea actualizar. Este parámetro es obligatorio.
     * @param updateCrop El objeto {@link CropDTO} que contiene los nuevos datos para actualizar el cultivo.
     *                   Los campos nulos no modificarán el valor existente.
     * @return El objeto {@link CropDTO} actualizado que representa el cultivo después de la actualización.
     * @throws Exception           Si ocurre un error al intentar actualizar el cultivo, como si el cultivo no existe.
     * @throws ValidationException Si alguna de las validaciones del estado, tipo o usuario falla según el validador {@link CropValidator}.
     * @see CropDTO
     * @see CropValidator
     * @see CropRepository
     * @see CropMapper
     */
    @Override
    @Transactional
    @CachePut(value = "crops", key = "'id_'+#id")
    public CropDTO updatedCrop(String id, CropDTO updateCrop) throws Exception {
        CropDTO existingCrop = getCropById(id);
        return Optional.of(updateCrop)
                .map(dto -> {
                    existingCrop.setType(dto.getType() != null ? dto.getType() : existingCrop.getType());
                    existingCrop.setStatus(dto.getStatus() != null ? dto.getStatus() : existingCrop.getStatus());
                    existingCrop.setUser(dto.getUser() != null ? dto.getUser() : existingCrop.getUser());
                    return existingCrop;
                })
                .map(dto -> {
                    validatorCrop.validateStatus(dto.getStatus());
                    validatorCrop.validateType(dto.getType());
                    try {
                        serviceUser.getUserById(existingCrop.getId());
                    } catch (Exception e) {
                        throw new ValidationException(e.getMessage() + ", asocia el cultivo a un usuario existente");
                    }
                    if (!validatorCrop.isValid()) {
                        throw new ValidationException(validatorCrop.getErrors().toString());
                    }

                    validatorCrop.Reset();
                    return dto;
                })
                .map(mapperCrop::toEntity)
                .map(repositoryCrop::save)
                .map(mapperCrop::toDTO)
                .orElseThrow(() -> new Exception("El cultivo no se pudo actualizar"));
    }


    /**
     * Elimina un cultivo de la base de datos según el ID proporcionado.
     *
     * <p>Este método recibe el ID de un cultivo y lo busca en la base de datos. Si el cultivo existe,
     * se elimina de la base de datos utilizando el repositorio {@link CropRepository}. Después de eliminarlo,
     * se devuelve un mensaje confirmando la eliminación exitosa del cultivo. Si el cultivo no existe,
     * se lanza una {@link Exception} indicando que el cultivo no fue encontrado.</p>
     *
     * @param id El ID único del cultivo que se desea eliminar.
     * @return Un mensaje de confirmación que indica que el cultivo con el ID proporcionado fue eliminado correctamente.
     * @throws Exception Si el cultivo no existe en la base de datos o si ocurre algún otro error durante la eliminación.
     * @see CropRepository
     */
    @Override
    @Transactional
    @CacheEvict(value = "crops", key = "'id_'+#id")
    public String deleteCrop(String id) throws Exception {
        return Optional.of(getCropById(id))
                .map(user -> {
                    repositoryCrop.deleteById(new ObjectId(user.getId()));
                    return "El Cultivo con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Cultivo no existe."));
    }
}


