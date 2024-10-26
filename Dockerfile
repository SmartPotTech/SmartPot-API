#  Uso de imagen base con Java 17
FROM openjdk:17-slim

# Directiorio donde se colocará la aplicación en el contenedor
WORKDIR /app

# Copiar el archivo jar del proyecto al directorio /app en el contenedor
COPY target/api-0.0.1-SNAPSHOT.jar /app/api-smarpot.jar

# Exponer el puerto que usa la aplicación
EXPOSE 3000

# Comando para ejecutar aplicación
CMD ["java","-jar","/app/api-smarpot.jar"]
