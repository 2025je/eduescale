import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

TextField {
    id: control

    implicitHeight: EduStyle.Style.textInputHeight
    font.pixelSize: EduStyle.Style.bodyFontSize
    color: EduStyle.Style.textColor
    placeholderTextColor: EduStyle.Style.mutedTextColor
    selectionColor: EduStyle.Style.primarySubtleHoverColor
    selectedTextColor: EduStyle.Style.textColor
    padding: 8

    background: Rectangle {
        radius: EduStyle.Style.controlRadius
        color: control.enabled
            ? EduStyle.Style.surfaceColor
            : EduStyle.Style.disabledSurfaceColor
        border.width: control.activeFocus ? 2 : 1
        border.color: !control.enabled
            ? EduStyle.Style.disabledBorderColor
            : control.activeFocus
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.borderColor
    }
}
