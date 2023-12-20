package io.github.duke.dynamodb.utils;

import io.github.duke.dynamodb.annotation.LocalDynamoSecondaryInfo;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class DynamoDbStarterUtils {
    private DynamoDbStarterUtils() {
    }

    private static final String IS_PREFIX = "is";
    private static final String GET_PREFIX = "get";

    public static ScalarAttributeType getScalarAttributeType(Class<?> clazz) {
        if (clazz == null) {
            return ScalarAttributeType.UNKNOWN_TO_SDK_VERSION;
        } else if (String.class.isAssignableFrom(clazz)) {
            return ScalarAttributeType.S;
        } else if (Number.class.isAssignableFrom(clazz)) {
            return ScalarAttributeType.N;
        } else if (Boolean.class.isAssignableFrom(clazz)) {
            return ScalarAttributeType.B;
        } else {
            return ScalarAttributeType.UNKNOWN_TO_SDK_VERSION;
        }
    }

    public static String getFieldName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return methodName;
        }
        String fieldName;
        if (methodName.startsWith(IS_PREFIX)) {
            fieldName = methodName.replaceFirst(IS_PREFIX, "");
        } else if (methodName.startsWith(GET_PREFIX)) {
            fieldName = methodName.replaceFirst(GET_PREFIX, "");
        } else {
            fieldName = methodName;
        }
        return StringUtils.uncapitalize(fieldName);
    }

    public static List<Method> getAnnotatedMethods(Class<?> entity) {
        return Arrays.stream(entity.getMethods())
                .filter(isMethodAnnotated()).toList();
    }

    private static Predicate<Method> isMethodAnnotated() {
        return x -> x.isAnnotationPresent(DynamoDbPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSortKey.class)
                || x.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSecondarySortKey.class);
    }

    public static String[] getLocalIndexName(Class<?> entity) {
        if (entity.isAnnotationPresent(LocalDynamoSecondaryInfo.class)) {
            LocalDynamoSecondaryInfo localDynamoSecondaryInfo = entity.getAnnotation(LocalDynamoSecondaryInfo.class);
            return localDynamoSecondaryInfo.localIndexNames();
        }
        return new String[]{};
    }
}
