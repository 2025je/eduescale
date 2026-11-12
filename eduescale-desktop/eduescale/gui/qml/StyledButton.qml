import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

Button {
    id: control

    property string variant: "secondary"
    property string labelObjectName: ""

    implicitHeight: EduStyle.Style.compactButtonHeight
    font.pixelSize: EduStyle.Style.bodyFontSize
    font.weight: variant === "primary" ? Font.DemiBold : Font.Normal
    padding: 8
    highlighted: variant === "primary"

    contentItem: Text {
        objectName: control.labelObjectName
        text: control.text
        font: control.font
        color: !control.enabled
            ? EduStyle.Style.disabledTextColor
            : control.variant === "primary"
            ? EduStyle.Style.surfaceColor
            : control.variant === "danger" || control.variant === "quiet"
            ? EduStyle.Style.dangerColor
            : EduStyle.Style.primaryColor
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }

    background: Rectangle {
        radius: control.variant === "quiet"
            ? height / 2 : EduStyle.Style.controlRadius
        color: !control.enabled
            ? EduStyle.Style.disabledSurfaceColor
            : control.variant === "primary"
            ? control.pressed
                ? EduStyle.Style.primaryActiveColor
                : control.hovered
                ? EduStyle.Style.primaryHoverColor
                : EduStyle.Style.primaryColor
            : control.variant === "danger" || control.variant === "quiet"
            ? control.pressed
                ? EduStyle.Style.dangerSurfaceColor
                : control.hovered
                ? EduStyle.Style.dangerHoverColor
                : control.variant === "quiet"
                ? "transparent"
                : EduStyle.Style.surfaceColor
            : control.pressed
            ? EduStyle.Style.primarySubtleHoverColor
            : control.hovered
            ? EduStyle.Style.primarySubtleColor
            : EduStyle.Style.surfaceColor
        border.width: control.variant === "quiet"
            ? 0
            : control.activeFocus ? 2 : 1
        border.color: control.activeFocus
            ? EduStyle.Style.primaryColor
            : !control.enabled
            ? EduStyle.Style.disabledBorderColor
            : control.variant === "primary"
            ? EduStyle.Style.primaryBorderColor
            : control.variant === "danger"
            ? EduStyle.Style.dangerBorderColor
            : control.variant === "quiet"
            ? "transparent"
            : EduStyle.Style.borderColor
    }
}
