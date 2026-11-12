import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "styles" as EduStyle

StyledDialog {
    id: root
    objectName: "exportHistoryDialog"

    property var controller: null

    modal: true
    anchors.centerIn: parent
    width: Math.min(parent.width - 40, 720)
    height: Math.min(parent.height - 40, 560)
    standardButtons: Dialog.NoButton

    contentItem: ColumnLayout {
        spacing: 10

        Label {
            objectName: "exportHistoryWarning"
            Layout.fillWidth: true
            visible: root.controller !== null
                && root.controller.exportHistoryWarning.length > 0
            text: root.controller ? root.controller.exportHistoryWarning : ""
            color: EduStyle.Style.dangerColor
            wrapMode: Text.WordWrap
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            visible: !root.controller || root.controller.exportHistory.length === 0
            radius: EduStyle.Style.controlRadius
            color: EduStyle.Style.subtleSurfaceColor
            border.color: EduStyle.Style.borderColor

            Label {
                anchors.centerIn: parent
                text: "Todavía no hay exportaciones registradas."
                color: EduStyle.Style.mutedTextColor
            }
        }

        ListView {
            id: historyList
            objectName: "exportHistoryList"
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            spacing: 6
            model: root.controller ? root.controller.exportHistory : []
            delegate: Frame {
                width: historyList.width - historyScrollBar.width
                padding: 10

                background: Rectangle {
                    radius: EduStyle.Style.controlRadius
                    color: EduStyle.Style.subtleSurfaceColor
                    border.color: EduStyle.Style.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 3

                    Label {
                        Layout.fillWidth: true
                        text: modelData.format + " · " + modelData.status
                        color: modelData.status === "Completada"
                            ? EduStyle.Style.primaryEmphasisColor
                            : EduStyle.Style.dangerColor
                        font.weight: Font.DemiBold
                    }

                    Label {
                        Layout.fillWidth: true
                        text: modelData.file + " · " + modelData.timestamp
                        color: EduStyle.Style.textColor
                        wrapMode: Text.WordWrap
                    }

                    Label {
                        Layout.fillWidth: true
                        text: modelData.filters
                        color: EduStyle.Style.mutedTextColor
                        wrapMode: Text.WordWrap
                        font.pixelSize: EduStyle.Style.smallFontSize
                    }
                }
            }

            ScrollBar.vertical: StyledScrollBar {
                id: historyScrollBar
                objectName: "exportHistoryScrollBar"
                policy: ScrollBar.AsNeeded
            }
        }

        StyledButton {
            objectName: "closeExportHistoryButton"
            Layout.alignment: Qt.AlignRight
            text: "Cerrar"
            variant: "secondary"
            Accessible.name: "Cerrar historial de exportaciones"
            onClicked: root.close()
        }
    }
}
