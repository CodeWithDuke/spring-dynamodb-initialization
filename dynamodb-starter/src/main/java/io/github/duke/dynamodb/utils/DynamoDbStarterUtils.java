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

/**
 * Utility class for DynamoDB Starter.
 * <p>
 * This class provides various utility methods related to DynamoDB, such as determining the
 * {@link ScalarAttributeType} of a given class, extracting field names from method names,
 * retrieving annotated methods within a class, and obtaining local index names using annotations.
 *
 * @see ScalarAttributeType
 * @see DynamoDbPartitionKey
 * @see DynamoDbSortKey
 * @see DynamoDbSecondaryPartitionKey
 * @see DynamoDbSecondarySortKey
 * @see LocalDynamoSecondaryInfo
 */
public class DynamoDbStarterUtils {
    private DynamoDbStarterUtils() {
        // Private constructor to prevent instantiation of the utility class.
    }

    private static final String IS_PREFIX = "is";
    private static final String GET_PREFIX = "get";

    /**
     * Gets the {@link ScalarAttributeType} for a given class.
     *
     * @param clazz The class to determine the scalar attribute type for.
     * @return The corresponding scalar attribute type.
     *        <ul>
     *        <li>
     *        <p>
     *        <code>S</code> - the attribute is of type String
     *        </p>
     *        </li>
     *        <li>
     *        <p>
     *        <code>N</code> - the attribute is of type Number
     *        </p>
     *        </li>
     *        <li>
     *        <p>
     *        <code>B</code> - the attribute is of type Binary
     *        </p>
     *        </li>
     *        </ul>
     */
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

    /**
     * Extracts the field name from a given method name.
     *
     * @param methodName The method name.
     * @return The corresponding field name.
     */
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

    /**
     * Retrieves a list of annotated methods within a class.
     *
     * @param entity The class to inspect.
     * @return A list of annotated methods.
     */
    public static List<Method> getAnnotatedMethods(Class<?> entity) {
        return Arrays.stream(entity.getMethods())
                .filter(isMethodAnnotated()).toList();
    }

    /**
     * @return a predicate for filtering methods based on DynamoDB annotations.
     */
    private static Predicate<Method> isMethodAnnotated() {
        return x -> x.isAnnotationPresent(DynamoDbPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSortKey.class)
                || x.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSecondarySortKey.class);
    }

    /**
     * Gets local index names from the {@link LocalDynamoSecondaryInfo} annotation on a class.
     *
     * @param entity The class to check for annotations.
     * @return An array of local index names.
     */
    public static String[] getLocalIndexName(Class<?> entity) {
        if (entity.isAnnotationPresent(LocalDynamoSecondaryInfo.class)) {
            LocalDynamoSecondaryInfo localDynamoSecondaryInfo = entity.getAnnotation(LocalDynamoSecondaryInfo.class);
            return localDynamoSecondaryInfo.localIndexNames();
        }
        return new String[]{};
    }
}
