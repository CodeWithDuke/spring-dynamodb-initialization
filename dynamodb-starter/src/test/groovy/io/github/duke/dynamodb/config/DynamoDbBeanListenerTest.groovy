package io.github.duke.dynamodb.config

import io.awspring.cloud.dynamodb.DefaultDynamoDbTableNameResolver
import io.github.duke.dynamodb.UserEntity
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse
import software.amazon.awssdk.services.dynamodb.model.KeyType
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DynamoDbBeanListenerTest extends Specification {

    @MockBean
    private DynamoDbClient dynamoDbClient

    @MockBean
    private DefaultDynamoDbTableNameResolver prefixedTableNameResolver

    def tablePrefix = "test_"

    @Shared
    LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:2.3.2")).withReuse(true);

    def setup() {
        localstack.start()
        dynamoDbClient = DynamoDbClient.builder().endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        prefixedTableNameResolver = new DefaultDynamoDbTableNameResolver(tablePrefix)
    }

    def 'should create table when context is refreshed'() {
        given:
        def contextRefreshedEvent = Mock(ContextRefreshedEvent)
        def tableName = prefixedTableNameResolver.resolve(UserEntity)
        def primaryKey = "id"
        def type = KeyType.HASH
        def keySchemaSize = UserEntity.getDeclaredMethods().findAll(x -> x.isAnnotationPresent(DynamoDbSortKey) || x.isAnnotationPresent(DynamoDbPartitionKey)).size()
        def indexSize = UserEntity.getDeclaredMethods().findAll(x -> x.isAnnotationPresent(DynamoDbSortKey) || x.isAnnotationPresent(DynamoDbPartitionKey)).size()

        and:
        DynamoDbBeanListener listener = new DynamoDbBeanListener(dynamoDbClient, prefixedTableNameResolver)
        listener.tablePrefix = tablePrefix
        listener.packageName ="io.github.duke.dynamodb"
        listener.billingMode = BillingMode.PAY_PER_REQUEST

        when:
        listener.onApplicationEvent(contextRefreshedEvent)

        then:
        DescribeTableRequest describeTableRequest = DescribeTableRequest.builder().tableName(tableName).build() as DescribeTableRequest
        DescribeTableResponse tableDetail = dynamoDbClient.describeTable(describeTableRequest)
        tableDetail.table().tableName() == tableName
        tableDetail.table().keySchema().size() == keySchemaSize;
        tableDetail.table().keySchema()[0].attributeName() == primaryKey
        tableDetail.table().keySchema()[0].keyType() == type
        tableDetail.table().globalSecondaryIndexes().size() == indexSize
    }
}

