package com.zjcxph.imgapi.dto.req;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MigrationJobRequest {

    /** pilot / batch / full */
    private String mode = "pilot";

    /** pilot、batch 模式的最大处理数量；full 模式忽略该值。 */
    private Integer limit;

    /** 可选目录范围；为空时按全部待迁移记录处理。 */
    private String folder;

    /** full 模式必须填写固定确认短语，避免误触。 */
    private String confirmation;
}
