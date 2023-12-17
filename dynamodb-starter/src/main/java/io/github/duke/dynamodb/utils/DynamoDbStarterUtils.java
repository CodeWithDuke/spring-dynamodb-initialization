package io.github.duke.dynamodb.utils;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class DynamoDbStarterUtils {

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
        var fieldName = methodName.replace("get", "");
        return StringUtils.uncapitalize(fieldName);
    }
}
