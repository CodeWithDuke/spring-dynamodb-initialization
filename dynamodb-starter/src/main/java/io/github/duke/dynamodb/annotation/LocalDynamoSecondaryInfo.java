package io.github.duke.dynamodb.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalDynamoSecondaryInfo {
    String[] localIndexNames();
}
