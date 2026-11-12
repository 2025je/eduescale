import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

CheckBox {
    id: control

    implicitHeight: Math.max(indicator.implicitHeight, contentItem.implicitHeight)
    implicitWidth: indicator.implicitWidth + spacing + contentItem.implicitWidth
    spacing: 8
    font.pixelSize: EduStyle.Style.bodyFontSize
    hoverEnabled: true

    indicator: Rectangle {
        objectName: control.objectName + "Indicator"
        implicitWidth: EduStyle.Style.checkBoxIndicatorSize
        implicitHeight: EduStyle.Style.checkBoxIndicatorSize
        x: control.leftPadding
        y: control.height / 2 - height / 2
        radius: EduStyle.Style.controlRadius - 2
        color: !control.enabled
            ? EduStyle.Style.disabledSurfaceColor
            : control.checked
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.surfaceColor
        border.width: 1
        border.color: control.activeFocus
            ? EduStyle.Style.primaryColor
            : !control.enabled
            ? EduStyle.Style.disabledBorderColor
            : control.checked
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.borderColor

        Text {
            anchors.centerIn: parent
            visible: control.checked
            text: "✓"
            color: EduStyle.Style.surfaceColor
            font.pixelSize: 13
            font.weight: Font.Bold
        }
    }

    contentItem: Text {
        leftPadding: control.indicator.width + control.spacing
        text: control.text
        font: control.font
        color: control.enabled
            ? EduStyle.Style.textColor
            : EduStyle.Style.disabledTextColor
        verticalAlignment: Text.AlignVCenter
        wrapMode: Text.WordWrap
    }

    background: Rectangle {
        radius: EduStyle.Style.controlRadius
        color: control.hovered
            ? EduStyle.Style.subtleSurfaceColor
            : "transparent"
    }
}
