/** 日期范围展开为多行后，沿纵轴避让视口，保留弹层完整操作区域。 */
export const rangePickerPopperOptions = {
  modifiers: [{ name: 'preventOverflow', options: { altAxis: true, padding: 16 } }],
}
