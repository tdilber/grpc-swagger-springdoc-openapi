package com.beyt.doc.grpc.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import lombok.SneakyThrows;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.io.IOException;
import java.lang.reflect.Method;

public class GenericInterceptor {

    private Class<?> clazz;
    private Class<?> controllerType;
    private Class<? extends Message> paramTypePTO;
    private final static ObjectMapper converter;
    private final static FilterProvider filters;

    static {
        converter = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        SimpleBeanPropertyFilter theFilter = SimpleBeanPropertyFilter
                .serializeAllExcept("unknownFields", "memoizedIsInitialized");
        filters = new SimpleFilterProvider()
                .addFilter("myFilter", theFilter);
    }

    public GenericInterceptor(Class<?> clazz, Class<? extends Message> paramTypePTO) {
        this.clazz = clazz;
        this.paramTypePTO = paramTypePTO;
    }

    public void setControllerType(Class<?> controllerType) {
        this.controllerType = controllerType;
    }

    @RuntimeType
    @SneakyThrows
    public Object intercept(@AllArguments Object[] allArguments,
                            @This Object thiz,
                            @Origin Method method) {
        Class<?> returnTypeDTO = method.getReturnType();

        String dtoStr = converter.writer(filters).writeValueAsString(allArguments[0]);

        Message.Builder newBuilder = (Message.Builder) paramTypePTO.getDeclaredMethod("newBuilder").invoke(null);

        Object param = fromJson(newBuilder, dtoStr, paramTypePTO);

        Object client = controllerType.getMethod("getClient").invoke(thiz);

        Message resultPto = (Message) clazz.getDeclaredMethod(method.getName(), paramTypePTO).invoke(client, param);

        String resultPtoStr = toJson(resultPto);

        return converter.readValue(resultPtoStr, returnTypeDTO);
    }

    public static <T extends Message> Message fromJson(Message.Builder newBuilder, String json, Class<T> messageType) throws IOException {
        JsonFormat.parser().ignoringUnknownFields().merge(json, newBuilder);
        return (T) newBuilder.build();
    }

    public static String toJson(MessageOrBuilder messageOrBuilder) throws IOException {
        return JsonFormat.printer().print(messageOrBuilder);
    }
}
