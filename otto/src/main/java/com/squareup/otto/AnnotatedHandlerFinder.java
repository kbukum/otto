/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.otto;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Helper methods for finding methods annotated with {@link Produce} and {@link Subscribe}.
 *
 * @author Cliff Biffle
 * @author Louis Wasserman
 * @author Jake Wharton
 */
final class AnnotatedHandlerFinder {

  /** Cache event bus producer methods for each class. */
  private static final ConcurrentMap<Class<?>, Map<EventKey, Method>> PRODUCERS_CACHE =
    new ConcurrentHashMap<Class<?>, Map<EventKey, Method>>();

  /** Cache event bus subscriber methods for each class. */
  private static final ConcurrentMap<Class<?>, Map<EventKey, Set<Method>>> SUBSCRIBERS_CACHE =
    new ConcurrentHashMap<Class<?>, Map<EventKey, Set<Method>>>();

  private static void loadAnnotatedProducerMethods(Class<?> listenerClass,
      Map<EventKey, Method> producerMethods) {
    Map<EventKey, Set<Method>> subscriberMethods = new HashMap<EventKey, Set<Method>>();
    loadAnnotatedMethods(listenerClass, producerMethods, subscriberMethods);
  }

  private static void loadAnnotatedSubscriberMethods(Class<?> listenerClass,
      Map<EventKey, Set<Method>> subscriberMethods) {
    Map<EventKey, Method> producerMethods = new HashMap<EventKey, Method>();
    loadAnnotatedMethods(listenerClass, producerMethods, subscriberMethods);
  }

  /**
   * Load all methods annotated with {@link Produce} or {@link Subscribe} into their respective caches for the
   * specified class.
   */
  private static void loadAnnotatedMethods(Class<?> listenerClass,
      Map<EventKey, Method> producerMethods, Map<EventKey, Set<Method>> subscriberMethods) {
    for (Method method : listenerClass.getDeclaredMethods()) {
      // The compiler sometimes creates synthetic bridge methods as part of the
      // type erasure process. As of JDK8 these methods now include the same
      // annotations as the original declarations. They should be ignored for
      // subscribe/produce.
      if (method.isBridge()) {
        continue;
      }
      if (method.isAnnotationPresent(Subscribe.class)) {
        Subscribe subscribe = method.getAnnotation(Subscribe.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
          throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation but requires "
              + parameterTypes.length + " arguments.  Methods must require a single argument.");
        }

        Class<?> eventType = parameterTypes[0];
        if (eventType.isInterface()) {
          throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation on " + eventType
              + " which is an interface.  Subscription must be on a concrete class type.");
        }

        if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
          throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation on " + eventType
              + " but is not 'public'.");
        }

        EventKey key = new EventKey(subscribe.value(), eventType);
        Set<Method> methods = subscriberMethods.get(key);
        if (methods == null) {
          methods = new HashSet<Method>();
          subscriberMethods.put(key, methods);
        }
        methods.add(method);
      } else if (method.isAnnotationPresent(Produce.class)) {
          Produce produce = method.getAnnotation(Produce.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 0) {
          throw new IllegalArgumentException("Method " + method + "has @Produce annotation but requires "
              + parameterTypes.length + " arguments.  Methods must require zero arguments.");
        }
        if (method.getReturnType() == Void.class) {
          throw new IllegalArgumentException("Method " + method
              + " has a return type of void.  Must declare a non-void type.");
        }

        Class<?> eventType = method.getReturnType();
        if (eventType.isInterface()) {
          throw new IllegalArgumentException("Method " + method + " has @Produce annotation on " + eventType
              + " which is an interface.  Producers must return a concrete class type.");
        }
        if (eventType.equals(Void.TYPE)) {
          throw new IllegalArgumentException("Method " + method + " has @Produce annotation but has no return type.");
        }

        if ((method.getModifiers() & Modifier.PUBLIC) == 0) {
          throw new IllegalArgumentException("Method " + method + " has @Produce annotation on " + eventType
              + " but is not 'public'.");
        }

        EventKey eventKey = new EventKey(produce.value(), eventType);

        if (producerMethods.containsKey(eventKey)) {
          throw new IllegalArgumentException("Producer for type " + eventType + " has already been registered.");
        }
        producerMethods.put(eventKey, method);
      }
    }

    PRODUCERS_CACHE.put(listenerClass, producerMethods);
    SUBSCRIBERS_CACHE.put(listenerClass, subscriberMethods);
  }

  /** This implementation finds all methods marked with a {@link Produce} annotation. */
  static Map<EventKey, EventProducer> findAllProducers(Object listener) {
    final Class<?> listenerClass = listener.getClass();
    Map<EventKey, EventProducer> handlersInMethod = new HashMap<EventKey, EventProducer>();

    Map<EventKey, Method> methods = PRODUCERS_CACHE.get(listenerClass);
    if (null == methods) {
      methods = new HashMap<EventKey, Method>();
      loadAnnotatedProducerMethods(listenerClass, methods);
    }
    if (!methods.isEmpty()) {
      for (Map.Entry<EventKey, Method> e : methods.entrySet()) {
        EventProducer producer = new EventProducer(listener, e.getValue());
        handlersInMethod.put(e.getKey(), producer);
      }
    }
    return handlersInMethod;
  }

  /** This implementation finds all methods marked with a {@link Subscribe} annotation. */
  static Map<EventKey, Set<EventHandler>> findAllSubscribers(Object listener) {
    Class<?> listenerClass = listener.getClass();
    Map<EventKey, Set<EventHandler>> handlersInMethod = new HashMap<EventKey, Set<EventHandler>>();

    Map<EventKey, Set<Method>> methods = SUBSCRIBERS_CACHE.get(listenerClass);
    if (null == methods) {
      methods = new HashMap<EventKey, Set<Method>>();
      loadAnnotatedSubscriberMethods(listenerClass, methods);
    }
    if (!methods.isEmpty()) {
      for (Map.Entry<EventKey, Set<Method>> e : methods.entrySet()) {
        Set<EventHandler> handlers = new HashSet<EventHandler>();
        for (Method m : e.getValue()) {
          handlers.add(new EventHandler(listener, m));
        }
        handlersInMethod.put(e.getKey(), handlers);
      }
    }

    return handlersInMethod;
  }

  private AnnotatedHandlerFinder() {
    // No instances.
  }

}
