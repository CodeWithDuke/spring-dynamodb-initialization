package io.github.duke.dynamodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes marked with this annotation represent DynamoDB documents
 * or sub-documents, and the table creation process can be skipped
 * to avoid unnecessary operations during application startup
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamoDbDocument {
}
