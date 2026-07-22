package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.entity.MigrationJob;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class MigrationReadinessDTO {
    private boolean ready;
    private boolean ossConfigured;
    private boolean sourcePathConfigured;
    private boolean sourcePathReadable;
    private boolean noActiveJob;
    private long pendingCount;
    private int sampleSize;
    private int sampleReadableCount;
    private int sampleMissingCount;
    private int sampleInvalidCount;
    private String recommendedMode;
    private String recommendedAction;
    private MigrationJob activeJob;
    private List<String> warnings = new ArrayList<>();
}
