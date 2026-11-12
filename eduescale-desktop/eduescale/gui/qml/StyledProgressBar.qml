import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

ProgressBar {
    id: control

    implicitHeight: EduStyle.Style.progressTrackHeight

    background: Rectangle {
        implicitHeight: EduStyle.Style.progressTrackHeight
        radius: height / 2
        color: EduStyle.Style.tabTrackColor
    }

    contentItem: Item {
        implicitHeight: EduStyle.Style.progressTrackHeight
        clip: true

        Rectangle {
            id: progressFill
            width: control.indeterminate
                ? parent.width * 0.3
                : parent.width * control.visualPosition
            height: parent.height
            radius: height / 2
            color: EduStyle.Style.primaryColor

            x: control.indeterminate ? -width : 0

            SequentialAnimation on x {
                running: control.indeterminate && control.visible
                loops: Animation.Infinite

                NumberAnimation {
                    from: -progressFill.width
                    to: progressFill.parent.width
                    duration: 1100
                    easing.type: Easing.InOutCubic
                }
            }
        }
    }
}
