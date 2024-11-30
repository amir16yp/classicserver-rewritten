package net.classicube.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventRegistry {
    // Concurrent map to store event listeners
    private static final Map<Class<? extends Event>, List<RegisteredListener>>
            eventListeners = new ConcurrentHashMap<>();

    // Register a listener for all its annotated event handler methods
    public static void registerListener(Object listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)) {
                // Check if the method has correct event handler signature
                if (method.getParameterCount() != 1 ||
                        !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalArgumentException(
                            "Invalid event handler method: " + method.getName()
                    );
                }

                // Get event type and priority
                Class<? extends Event> eventType =
                        (Class<? extends Event>) method.getParameterTypes()[0];
                EventHandler annotation = method.getAnnotation(EventHandler.class);

                // Make the method accessible
                method.setAccessible(true);

                // Add to listeners map
                eventListeners.computeIfAbsent(
                        eventType,
                        k -> new CopyOnWriteArrayList<>()
                ).add(new RegisteredListener(
                        listener, method, annotation.priority()
                ));

                // Sort listeners by priority
                eventListeners.get(eventType).sort(
                        (a, b) -> Integer.compare(b.priority, a.priority)
                );
            }
        }
    }

    public static void unregisterListener(Object listener) {
        // Iterate through all registered event types
        for (Map.Entry<Class<? extends Event>, List<RegisteredListener>> entry :
                eventListeners.entrySet()) {

            // Remove all listeners associated with the given listener object
            entry.getValue().removeIf(
                    registeredListener -> registeredListener.listener == listener
            );

            // If no listeners remain for this event type, remove the entry
            if (entry.getValue().isEmpty()) {
                eventListeners.remove(entry.getKey());
            }
        }
    }

    public static <T extends Event> T callEvent(T event) {
        List<RegisteredListener> listeners =
                eventListeners.getOrDefault(event.getClass(), Collections.emptyList());

        for (RegisteredListener registeredListener : listeners) {
            try {
                // Invoke the event handler method
                registeredListener.method.invoke(
                        registeredListener.listener, event
                );

                // Stop processing if event is cancelled
                if (event.isCancelled()) {
                    break;
                }
            } catch (Exception e) {
                System.err.println("Error processing event: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return event;
    }

    // Annotation to mark event handler methods
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface EventHandler {
        int priority() default 0;
    }

}