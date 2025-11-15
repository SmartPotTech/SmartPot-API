package app.smartpot.api.mail.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Clase de configuración para habilitar y gestionar la ejecución asincrónica en la aplicación.
 *
 * <p>Esta clase habilita la ejecución de tareas asincrónicas mediante la anotación {@link Async} en la aplicación Spring.
 * Además, configura un {@link ThreadPoolTaskExecutor} para gestionar la ejecución de tareas en un pool de hilos de manera
 * eficiente. El pool de hilos permite controlar el número de hilos simultáneos, la capacidad de la cola de tareas y otras
 * propiedades relacionadas con la gestión de hilos.</p>
 *
 * <p>Usar esta configuración es recomendable para aplicaciones de producción, donde es importante tener control sobre la
 * ejecución de tareas asincrónicas para optimizar el uso de recursos y mejorar el rendimiento.</p>
 *
 * <p>Esta clase se activa con la anotación {@link EnableAsync}, lo que habilita el soporte de ejecución asincrónica en la
 * aplicación Spring.</p>
 *
 * @see org.springframework.scheduling.annotation.Async
 * @see ThreadPoolTaskExecutor
 * @see TaskExecutor
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Crea un {@link ThreadPoolTaskExecutor} que gestiona las tareas asincrónicas en un pool de hilos.
     *
     * <p>Este método define un pool de hilos con un número mínimo y máximo de hilos, y configura la capacidad de la cola
     * de tareas pendientes. El ejecutor se utiliza para ejecutar tareas anotadas con {@link Async} de manera eficiente.</p>
     *
     * <p>El {@link ThreadPoolTaskExecutor} asegura que las tareas asincrónicas no consuman recursos innecesarios al crear
     * nuevos hilos de manera indiscriminada, y permite la reutilización de hilos para mejorar el rendimiento en aplicaciones
     * de alto rendimiento.</p>
     *
     * <p>Los parámetros configurados en el {@link ThreadPoolTaskExecutor} son:</p>
     *
     * <ul>
     *   <li><b>corePoolSize</b>: El número mínimo de hilos que se deben mantener en el pool de hilos, incluso si están
     *       inactivos. Este valor asegura que siempre haya una cantidad mínima de hilos disponibles para ejecutar tareas
     *       sin la necesidad de crear hilos nuevos.</li>
     *   <li><b>maxPoolSize</b>: El número máximo de hilos que pueden ejecutarse simultáneamente en el pool. Cuando el número
     *       de tareas concurrentes es mayor que el número de hilos en el pool, se creará un nuevo hilo hasta que se alcance
     *       este límite.</li>
     *   <li><b>queueCapacity</b>: La capacidad de la cola de tareas pendientes. Cuando todos los hilos del pool están ocupados,
     *       las tareas adicionales se colocan en la cola. Si la cola se llena, y no se pueden crear más hilos, las tareas
     *       adicionales se rechazarán o esperarán hasta que haya espacio disponible.</li>
     *   <li><b>threadNamePrefix</b>: El prefijo utilizado para nombrar los hilos creados por el ejecutor. Esto ayuda a identificar
     *       fácilmente los hilos de ejecución asincrónica en los registros (logs) y durante la depuración.</li>
     * </ul>
     *
     * @return El {@link TaskExecutor} configurado, utilizado por Spring para ejecutar tareas asincrónicas.
     */
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Async-Executor-");
        executor.initialize();
        return executor;
    }

    /**
     * Método de cierre que se invoca al destruir la aplicación.
     * Asegura que el {@link ThreadPoolTaskExecutor} se detenga correctamente, evitando fugas de memoria.
     */
    @PreDestroy
    public void shutdownExecutor() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.getThreadPoolExecutor().awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
