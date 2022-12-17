package com.beyt.doc.grpc.service;

import com.beyt.doc.grpc.model.GenericInterceptor;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@SuppressWarnings("unchecked")
public class GrpcRepositoryCreator {
    private final Map<Class<?>, DynamicType.Loaded<Serializable>> converts = new ConcurrentHashMap<>();
    private final List<Class<?>> convertList = new ArrayList<>();

    public Object createGrpcControllerSafely(Class<?> clazz, String clientName, String prefix) {
        try {
            return createGrpcController(clazz, clientName, prefix);
        } catch (Throwable throwable) {
            return "ERROR_OCCURRED";
        }
    }

    @SneakyThrows
    public Object createGrpcController(Class<?> clazz, String clientName, String prefix) {
        List<Method> overridableMethods = getOverridableMethods(clazz);
        fillConvertList(overridableMethods);
        convertAllPtoToDto(clazz);
        Class<?> controllerType = createRuntimeController(clazz, clientName, prefix, overridableMethods);
        return controllerType.getConstructor().newInstance();
    }

    private Class<?> createRuntimeController(Class<?> clazz, String clientName, String prefix, List<Method> overridableMethods) {
        List<GenericInterceptor> interceptors = new ArrayList<>();
        DynamicType.Builder<Object> objectBuilder = new ByteBuddy()
                .subclass(Object.class)
                .name(getClass().getPackageName() + "." + clazz.getSimpleName() + "DynamicController")
                .annotateType(AnnotationDescription.Builder
                        .ofType(RestController.class)
                        .build())
                .defineProperty("client", clazz, true)
                .annotateField(AnnotationDescription.Builder
                        .ofType(GrpcClient.class)
                        .define("value", clientName)
                        .build());

        for (Method overridableMethod : overridableMethods) {
            Class<?>[] parameterTypes = overridableMethod.getParameterTypes();
            Class<? extends Message> parameter = (Class<? extends Message>) parameterTypes[0];
            Class<? extends Message> returnType = (Class<? extends Message>) overridableMethod.getReturnType();
            GenericInterceptor interceptor = new GenericInterceptor(clazz, parameter);
            interceptors.add(interceptor);
            objectBuilder = objectBuilder
                    .defineMethod(overridableMethod.getName(), converts.get(returnType).getLoaded(), Modifier.PUBLIC)
                    .withParameter(converts.get(parameter).getLoaded(), "param")
                    .annotateParameter(AnnotationDescription.Builder
                            .ofType(RequestBody.class)
                            .build())
                    .intercept(MethodDelegation.to(interceptor))
                    .annotateMethod(AnnotationDescription.Builder
                            .ofType(PostMapping.class)
                            .defineArray("value", "/" + clientName + "/" + prefix + "/" + overridableMethod.getName())
                            .build());
        }
        Class<?> controllerType = objectBuilder.make()
                .include(converts.values().stream().toList())
                .load(clazz.getClassLoader())
                .getLoaded();

        interceptors.forEach(i -> i.setControllerType(controllerType));
        return controllerType;
    }

    private void convertAllPtoToDto(Class<?> clazz) {
        int index = 0;
        int isChanged = 0;
        while (convertList.size() > 0) {
            if (convertList.size() <= index) {
                if (isChanged > 5) {
                    throw new IllegalStateException();
                }
                index = 0;
                isChanged++;
            }

            Class<?> convertee = convertList.get(index);

            DynamicType.Loaded<Serializable> converted = convertPtoToDto(convertee, clazz);
            if (Objects.isNull(converted)) {
                index++;
                continue;
            } else {
                converts.put(convertee, converted);
                convertList.remove(convertee);
                isChanged = 0;
            }
        }
    }

    private DynamicType.Loaded<Serializable> convertPtoToDto(Class<?> ptoClass, Class<?> clazz) {

        DynamicType.Builder<Serializable> builder = new ByteBuddy().subclass(Serializable.class)
                .name(getClass().getPackageName() + "." + ptoClass.getSimpleName() + "DynamicDTO");

        var fieldList = Stream.of(ptoClass.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).filter(f -> !f.getName().equalsIgnoreCase("unknownFields") && !f.getName().equalsIgnoreCase("memoizedIsInitialized")).toList();

        boolean isReturnNull = false;
        for (Field field : fieldList) {
            Class<?> type = field.getType();
            if (GeneratedMessageV3.class.isAssignableFrom(type)) {
                if (converts.containsKey(type)) {
                    builder = builder.defineProperty(field.getName().substring(0, field.getName().length() - 1), converts.get(type).getLoaded());
                } else if (ptoClass.isAssignableFrom(type)) {
                    continue;
                } else {
                    if (!convertList.contains(type)) {
                        convertList.add(type);
                    }
                    isReturnNull = true;
                }
            } else {
                builder = builder.defineProperty(field.getName().substring(0, field.getName().length() - 1), type);
            }
        }

        if (isReturnNull) {
            return null;
        }

        return builder
                .make().load(clazz.getClassLoader());
    }

    private void fillConvertList(List<Method> overridableMethods) {
        Set<Class<?>> convertees = new HashSet<>();

        for (Method overridableMethod : overridableMethods) {
            convertees.add(overridableMethod.getParameterTypes()[0]);
            convertees.add(overridableMethod.getReturnType());
        }
        convertList.addAll(convertees);
    }

    private List<Method> getOverridableMethods(Class<?> clazz) {
        return Stream.of(clazz.getMethods()).filter(c -> !Modifier.isFinal(c.getModifiers()) && !Modifier.isNative(c.getModifiers()) && !Modifier.isStatic(c.getModifiers())).filter(m -> !m.getDeclaringClass().equals(Object.class)).filter(m -> m.getParameterTypes().length == 1).toList();
    }

}
