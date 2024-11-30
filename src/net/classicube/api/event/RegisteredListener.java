package net.classicube.api.event;

import java.lang.reflect.Method;

public class RegisteredListener {
    final Object listener;
    final Method method;
    final int priority;

    public RegisteredListener(Object listener, Method method, int priority) {
        this.listener = listener;
        this.method = method;
        this.priority = priority;
    }
}