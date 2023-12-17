package io.github.duke.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    private String id;
    private String email;
    private String company;
    private Long type;
    private Long dob;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "index1")
    public String getEmail() {
        return email;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "index2")
    public String getCompany() {
        return company;
    }

    @DynamoDbSecondarySortKey(indexNames = {"index1", "index2"})
    public Long getType() {
        return type;
    }

    @DynamoDbSortKey()
    public Long getDob() {
        return dob;
    }
}
