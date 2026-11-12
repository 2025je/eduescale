import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

TextArea {
    id: control

    font.pixelSize: EduStyle.Style.bodyFontSize
    color: EduStyle.Style.textColor
    selectionColor: EduStyle.Style.primarySubtleHoverColor
    selectedTextColor: EduStyle.Style.textColor
    padding: 8

    background: Rectangle {
        radius: EduStyle.Style.controlRadius
        color: control.enabled
            ? EduStyle.Style.subtleSurfaceColor
            : EduStyle.Style.disabledSurfaceColor
        border.width: control.activeFocus ? 2 : 1
        border.color: !control.enabled
            ? EduStyle.Style.disabledBorderColor
            : control.activeFocus
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.borderColor
    }
}
