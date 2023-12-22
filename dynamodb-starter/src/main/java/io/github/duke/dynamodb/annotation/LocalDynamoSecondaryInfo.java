package io.github.duke.dynamodb.annotation;

import java.lang.annotation.*;

/**
 * Indicates that a class provides local DynamoDB secondary index information.
 * <p>
 * This annotation allows specifying local index names for the class. When creating tables
 * or performing other DynamoDB-related operations, the provided local index names can be used
 * to create local secondary indexes instead of global secondary indexes.
 * <p>
 * Constraints:
 * - <strong>Note:</strong>  Local secondary indexes can only be created when the table is initially created.
 * - The table must have composite key(partition and range key), and the local partition key should be one of them.
 * Example usage:
 * <pre>
 * {@code
 * @DynamoDbBean
 * @LocalDynamoSecondaryInfo(localIndexNames = "index1")
 * public class Example {
 *     private String id;
 *     private String createdAt;
 *
 *     @DynamoDbPartitionKey
 *     @DynamoDbAttribute(value = "id")
 *     @DynamoDbSecondaryPartitionKey(indexNames = "index1")
 *     public String getId() {
 *         return id;
 *     }
 *
 *     @DynamoDbSecondarySortKey(indexNames = "index1")
 *     public String getCreatedAt() {
 *         return createdAt;
 *     }
 *
 *     // other index not included in localIndexNames will be global
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalDynamoSecondaryInfo {
    String[] localIndexNames();
}
