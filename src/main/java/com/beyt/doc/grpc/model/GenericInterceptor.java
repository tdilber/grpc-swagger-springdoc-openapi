package com.beyt.doc.grpc.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
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
    private static ObjectMapper converter;

    public GenericInterceptor(Class<?> clazz, Class<? extends Message> paramTypePTO) {
        this.clazz = clazz;
        this.paramTypePTO = paramTypePTO;
        converter = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        converter.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    public void setControllerType(Class<?> controllerType) {
        this.controllerType = controllerType;
    }

    @RuntimeType
    @SneakyThrows
    public Object intercept(@AllArguments Object[] allArguments,
                            @This Object thiz,
                            @Origin Method method) {
        Object argument = allArguments[0];
        String s = "TEST " + argument.getClass().getName() + " " + argument;
        Class<?> returnTypeDTO = method.getReturnType();
        SimpleBeanPropertyFilter theFilter = SimpleBeanPropertyFilter
                .serializeAllExcept("unknownFields", "memoizedIsInitialized");
        FilterProvider filters = new SimpleFilterProvider()
                .addFilter("myFilter", theFilter);

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

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }
}
