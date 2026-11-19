import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Components 1.0
import EduEscale.Gui.Styles 1.0 as EduStyle

Item {
    id: root
    objectName: "schoolWizardReviewPage"

    required property var window
    anchors.fill: parent
    z: 10
    visible: window.workflowStep === 1

    Rectangle {
        anchors.fill: parent
        color: window.canvasColor
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        Label {
            Layout.fillWidth: true
            text: "Paso 2 de 4 · Revisar búsqueda"
            color: window.textColor
            font.pixelSize: window.appTitleFontSize
            font.weight: Font.DemiBold
        }

        Label {
            Layout.fillWidth: true
            text: window.viewModel.dataSource === "offline"
                ? "Se buscarán los datos de las búsquedas seleccionadas en la base offline."
                : "Se descargarán los datos de ESCALE para las búsquedas seleccionadas."
            color: window.mutedTextColor
            wrapMode: Text.WordWrap
        }

        Frame {
            Layout.fillWidth: true
            Layout.fillHeight: true
            padding: 16

            background: Rectangle {
                radius: window.cardRadius
                color: window.surfaceColor
                border.color: window.borderColor
            }

            ColumnLayout {
                anchors.fill: parent
                spacing: 10

                Label {
                    Layout.fillWidth: true
                    text: "Búsquedas que se ejecutarán"
                    color: window.textColor
                    font.weight: Font.DemiBold
                }

                Label {
                    objectName: "searchReviewSummary"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    text: window.currentSearchSummary()
                    color: window.mutedTextColor
                    wrapMode: Text.WordWrap
                    verticalAlignment: Text.AlignTop
                }
            }
        }

        RowLayout {
            Layout.fillWidth: true

            StyledButton {
                objectName: "backToSearchButton"
                text: "Volver a configurar"
                variant: "secondary"
                onClicked: window.returnToSearchConfiguration()
            }

            Item { Layout.fillWidth: true }

            StyledButton {
                objectName: "startSearchButton"
                text: "Iniciar búsqueda"
                variant: "primary"
                onClicked: window.startSchoolSearch()
            }
        }
    }
}
