package io.github.duke.dynamodb.config;

import io.awspring.cloud.dynamodb.DefaultDynamoDbTableNameResolver;
import io.github.duke.dynamodb.utils.DynamoDbStarterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import io.github.duke.dynamodb.annotation.DynamoDbDocument;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

@Slf4j
@Component
public class DynamoDbBeanListener implements ApplicationListener<ContextRefreshedEvent> {

    private final DynamoDbClient dynamoDbClient;

    private final DefaultDynamoDbTableNameResolver prefixedTableNameResolver;

    @Value("${dynamodb.starter.table.prefix}")
    private String tablePrefix;

    @Value("${dynamodb.starter.package.scan:''}")
    private String packageName;

    @Value("${dynamodb.starter.billing.mode}")
    private BillingMode billingMode;

    @Value("${dynamodb.starter.throughput.writeCapacity:10}")
    private long writeCapacity;

    @Value("${dynamodb.starter.throughput.readCapacity:10}")
    private long readCapacity;

    public DynamoDbBeanListener(DynamoDbClient dynamoDbClient, DefaultDynamoDbTableNameResolver prefixedTableNameResolver) {
        this.dynamoDbClient = dynamoDbClient;
        this.prefixedTableNameResolver = prefixedTableNameResolver;
    }


    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DynamoDbBean.class));
        ListTablesRequest tablesRequest = ListTablesRequest.builder()
                .exclusiveStartTableName(tablePrefix)
                .build();
        ListTablesResponse tableList = dynamoDbClient.listTables(tablesRequest);
        for (BeanDefinition bd : scanner.findCandidateComponents(packageName)) {
            Class<?> entity;
            try {
                entity = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found");
            }
            if (entity.isAnnotationPresent(DynamoDbDocument.class)) {
                continue;
            }
            String tableName = prefixedTableNameResolver.resolve(entity);
            if (tableList.tableNames().stream().anyMatch(x -> x.equals(tableName))) {
                log.debug("Table {} already existed. Skipping", tableName);
                continue;
            }
            List<Method> keys = Arrays.stream(entity.getMethods())
                    .filter(isMethodAnnotated()).toList();
            //define attribute
            List<AttributeDefinition> attributeDefinitions = new ArrayList<AttributeDefinition>();
            //define 2nd indices
            HashMap<String, List<KeySchemaElement>> indexKeySchema = new HashMap<>();
            //define primary indices
            List<KeySchemaElement> tableKeySchema = new ArrayList<>();

            attributeAndKeysResolver(attributeDefinitions, tableKeySchema, keys, indexKeySchema);
            List<GlobalSecondaryIndex> globalSecondaryIndices = globalSecondaryIndicesResolver(indexKeySchema);
            //hash key need to be added first
            tableKeySchema.sort(Comparator.comparing(t -> t.keyType().toString()));

            CreateTableRequest createTableRequest = tableRequestResolver(tableName, attributeDefinitions,
                    tableKeySchema, globalSecondaryIndices);
            try {
                dynamoDbClient.createTable(createTableRequest);
            } catch (ResourceInUseException e) {
                log.error(e.getMessage());
            }
            log.debug("Table {} installation successfully", tableName);
        }
    }


    private CreateTableRequest tableRequestResolver(String tableName, List<AttributeDefinition> attributeDefinitions,
                                                    List<KeySchemaElement> keySchemas, List<GlobalSecondaryIndex> globalSecondaryIndices) {
        CreateTableRequest.Builder createTableRequest = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(keySchemas);
        if (!CollectionUtils.isEmpty(globalSecondaryIndices)) {
            createTableRequest.globalSecondaryIndexes(globalSecondaryIndices);
        }

        if (billingMode.equals(BillingMode.PROVISIONED)) {
            createTableRequest
                    .provisionedThroughput(
                            ProvisionedThroughput.builder()
                                    .readCapacityUnits(readCapacity).writeCapacityUnits(writeCapacity).build())
                    .build();
        } else {
            createTableRequest
                    .billingMode(BillingMode.PAY_PER_REQUEST);
        }
        return createTableRequest.build();
    }

    private void attributeAndKeysResolver(List<AttributeDefinition> attributeDefinitions, List<KeySchemaElement> tableKeySchema, List<Method> keys, HashMap<String, List<KeySchemaElement>> secondIndexKeySchema) {
        for (Method key : keys) {
            attributeDefinitions.add(AttributeDefinition.builder()
                    .attributeName(DynamoDbStarterUtils.getFieldName(key.getName()))
                    .attributeType(DynamoDbStarterUtils.getScalarAttributeType(key.getReturnType()))
                    .build());
            tableKeySchemaResolver(key, tableKeySchema);
            secondIndexKeySchemaResolver(key, secondIndexKeySchema);
        }
    }

    private void tableKeySchemaResolver(Method key, List<KeySchemaElement> tableKeySchema) {
        if (key.isAnnotationPresent(DynamoDbPartitionKey.class)) {
            tableKeySchema.add(KeySchemaElement.builder().
                    attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.HASH).build());
        } else if (key.isAnnotationPresent(DynamoDbSortKey.class)) {
            tableKeySchema.add(KeySchemaElement.builder().
                    attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.RANGE).build());
        }
    }

    private void secondIndexKeySchemaResolver(Method key, HashMap<String, List<KeySchemaElement>> secondIndexKeySchema) {
        if (key.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
            DynamoDbSecondaryPartitionKey dynamoDbSecondaryPartitionKey = key.getAnnotation(DynamoDbSecondaryPartitionKey.class);
            String[] indexNames = dynamoDbSecondaryPartitionKey.indexNames();
            for (String index : indexNames) {
                List<KeySchemaElement> elements = secondIndexKeySchema.getOrDefault(index, new ArrayList<>());
                elements.add(KeySchemaElement.builder().
                        attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.HASH).build());
                secondIndexKeySchema.put(index, elements);
            }
        }
        if (key.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
            DynamoDbSecondarySortKey dynamoDbSecondarySortKey = key.getAnnotation(DynamoDbSecondarySortKey.class);
            String[] indexNames = dynamoDbSecondarySortKey.indexNames();
            for (String index : indexNames) {
                List<KeySchemaElement> elements = secondIndexKeySchema.getOrDefault(index, new ArrayList<>());
                elements.add(KeySchemaElement.builder().
                        attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.RANGE).build());
                secondIndexKeySchema.put(index, elements);
            }
        }
    }

    private List<GlobalSecondaryIndex> globalSecondaryIndicesResolver(HashMap<String, List<KeySchemaElement>> indexKeySchema) {
        List<GlobalSecondaryIndex> globalSecondaryIndices = new ArrayList<>();
        indexKeySchema.forEach((indexName, indexSchemaList) -> {
            indexSchemaList.sort(Comparator.comparing(t -> t.keyType().toString()));
            GlobalSecondaryIndex glsIndex = GlobalSecondaryIndex.builder().indexName(indexName)
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build()).keySchema(indexSchemaList)
                    .build();
            globalSecondaryIndices.add(glsIndex);
        });
        return globalSecondaryIndices;
    }



    private Predicate<Method> isMethodAnnotated() {
        return x -> x.isAnnotationPresent(DynamoDbPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSortKey.class)
                || x.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)
                || x.isAnnotationPresent(DynamoDbSecondarySortKey.class);
    }

}
