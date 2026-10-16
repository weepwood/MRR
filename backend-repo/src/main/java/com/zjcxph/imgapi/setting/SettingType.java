package com.zjcxph.imgapi.setting;

/**
 * 设置项的值类型。决定前端控件形态与服务端校验方式。
 */
public enum SettingType {

    /** 布尔开关，取值 true / false。 */
    BOOLEAN,

    /** 整数，受 min / max 约束。 */
    INTEGER,

    /** 小数。 */
    DECIMAL,

    /** 自由文本。 */
    TEXT,

    /** 枚举，取值限定在 allowedValues 内。 */
    ENUM
}
