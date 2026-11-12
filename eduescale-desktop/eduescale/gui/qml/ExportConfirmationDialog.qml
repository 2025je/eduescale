import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "styles" as EduStyle

StyledDialog {
    id: root
    objectName: "exportConfirmationDialog"

    property string description: ""
    property string searchSummary: ""
    property string destinationName: ""
    property string continueLabel: "Continuar"

    signal confirmed()

    modal: true
    anchors.centerIn: parent
    width: Math.min(parent.width - 40, 560)
    standardButtons: Dialog.NoButton

    contentItem: ColumnLayout {
        spacing: 12

        Label {
            Layout.fillWidth: true
            text: root.description
            color: EduStyle.Style.textColor
            wrapMode: Text.WordWrap
        }

        ScrollView {
            Layout.fillWidth: true
            Layout.preferredHeight: Math.min(searchSummaryLabel.implicitHeight, 180)
            visible: root.searchSummary.length > 0
            clip: true

            Label {
                id: searchSummaryLabel
                width: parent.width
                text: root.searchSummary
                color: EduStyle.Style.mutedTextColor
                wrapMode: Text.WordWrap
            }
        }

        Label {
            Layout.fillWidth: true
            visible: root.destinationName.length > 0
            text: "Archivo: " + root.destinationName
            color: EduStyle.Style.mutedTextColor
            wrapMode: Text.WordWrap
        }

        RowLayout {
            Layout.alignment: Qt.AlignRight
            spacing: 8

            StyledButton {
                objectName: "cancelConfirmationButton"
                text: "Cancelar"
                variant: "secondary"
                onClicked: root.close()
            }

            StyledButton {
                objectName: "confirmActionButton"
                text: root.continueLabel
                variant: "primary"
                onClicked: {
                    root.confirmed()
                    root.close()
                }
            }
        }
    }

}
