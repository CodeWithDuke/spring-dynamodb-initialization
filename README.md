# DynamoDB Starter for Spring

Welcome to DynamoDB Starter for Spring! This library simplifies the integration of AWS DynamoDB sdk version 2 with your Spring application.
The primary goal of Dynamodb Starter project is to make it easier to handle table and index automatically creation and modification.

## Getting Started

### 1. Setup
Download the JAR though [Maven Central](https://central.sonatype.com/artifact/io.github.codewithduke/dynamodb-starter) ([`SNAPSHOT` builds](https://central.sonatype.com/artifact/io.github.codewithduke/dynamodb-starter) are available via the [OSSRH snapshot repository](https://github.com/CodeWithDuke/spring-dynamodb-initialization) ):

```xml
<dependency>
    <groupId>io.github.codewithduke</groupId>
    <artifactId>dynamodb-starter</artifactId>
    <version>1.0.1</version>
</dependency>

```
### 2. Annotate Your Entities

Make sure to annotate your entities and entity objects with `@DynamoDbBean`. For child objects or arrays, include `@DynamoDbDocument`.
Dynamodb Entity
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@LocalDynamoSecondaryInfo(localIndexNames = "index1")
public class AccidentInformation {
    private String id;
    private String phoneNumber;
    private Integer time;
    private String latList;
    private String lonList;
    private VehicleNumber vehicleNumber;
    private String vehicleModel;
    private String address;
    private Integer accidentType;
    private String createdAt;
    private Boolean deleteFlag;
    private String receiveType;
    private String client;
    private String line;
    private String receivedNo;
    private String receivedDate;


    @DynamoDbPartitionKey
    @DynamoDbAttribute(value = "id")
    @DynamoDbSecondaryPartitionKey(indexNames = "index1")
    public String getId() {
        return id;
    }

    @DynamoDbSortKey
    public Integer getAccidentType() {
        return accidentType;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "client-receivedDate")
    public String getClient() {
        return client;
    }

    @DynamoDbSecondarySortKey(indexNames = {"client-receivedDate", "line-receivedDate"})
    public String getReceivedDate() {
        return receivedDate;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "line-receivedDate")
    public String getLine() {
        return line;
    }

    @DynamoDbSecondarySortKey(indexNames = "index1")
    public String getCreatedAt() {
        return createdAt;
    }
```
Dynamodb Child Object
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@DynamoDbDocument
public class VehicleNumber {
    private String vehicleNumberIssuingOffice;
    private String vehicleNumberClassCode;
    private String vehicleNumberKana;
    private String vehicleNumberFourDigit;
}
```
### 3. Add Configuration Properties

In your application properties file, include the following properties to configure DynamoDB:

```properties
# Set a prefix for your DynamoDB table name (optional)
dynamodb.starter.table.prefix=local_

# Choose your billing mode (options: PROVISIONED, PAY_PER_REQUEST)
dynamodb.starter.billing.mode=PAY_PER_REQUEST

# Set the write capacity for your DynamoDB table
dynamodb.starter.throughput.writeCapacity=10

# Set the read capacity for your DynamoDB table
dynamodb.starter.throughput.readCapacity=10

# Set the package name
dynamodb.starter.package.scan=your.package.name