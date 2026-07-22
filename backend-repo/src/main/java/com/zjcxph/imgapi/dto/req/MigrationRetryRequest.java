package com.zjcxph.imgapi.dto.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class MigrationRetryRequest {
    private List<Integer> scanIds;
}
