import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

ScrollBar {
    id: control

    width: EduStyle.Style.scrollBarWidth
    hoverEnabled: true
    padding: 2

    background: Rectangle {
        color: "transparent"
    }

    contentItem: Rectangle {
        implicitWidth: EduStyle.Style.scrollBarThumbWidth
        implicitHeight: EduStyle.Style.scrollBarThumbWidth
        radius: width / 2
        color: control.pressed
            ? EduStyle.Style.primaryColor
            : control.hovered
            ? EduStyle.Style.primaryHoverColor
            : EduStyle.Style.primaryBorderColor
        opacity: control.active ? 1 : 0.55
    }
}
