import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

TabButton {
    id: control

    property string labelObjectName: ""
    property string indicatorObjectName: ""

    implicitHeight: EduStyle.Style.tabButtonHeight
    font.weight: checked ? Font.DemiBold : Font.Normal

    contentItem: Text {
        objectName: control.labelObjectName
        text: control.text
        font: control.font
        color: control.checked
            ? EduStyle.Style.primaryEmphasisColor
            : control.hovered
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.textColor
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }

    background: Item {
        Rectangle {
            anchors.fill: parent
            radius: EduStyle.Style.controlRadius - 2
            color: control.checked
                ? EduStyle.Style.surfaceColor
                : control.pressed
                ? EduStyle.Style.primarySubtleHoverColor
                : control.hovered
                ? EduStyle.Style.primarySubtleColor
                : "transparent"
        }

        Rectangle {
            objectName: control.indicatorObjectName
            width: parent.width - EduStyle.Style.tabIndicatorInset * 2
            height: EduStyle.Style.tabIndicatorHeight
            radius: height / 2
            anchors.bottom: parent.bottom
            anchors.horizontalCenter: parent.horizontalCenter
            color: EduStyle.Style.primaryColor
            visible: control.checked
        }
    }
}
