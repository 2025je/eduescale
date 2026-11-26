import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Components 1.0
import EduEscale.Gui.Styles 1.0 as EduStyle

Item {
    id: root
    objectName: "schoolWizardReviewPage"

    required property var window
    anchors.left: parent.left
    anchors.right: parent.right
    anchors.bottom: parent.bottom
    anchors.top: parent.top
    anchors.topMargin: root.window.dashboardHeaderBottom + 8
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

        RowLayout {
            Layout.fillWidth: true

            Label {
                objectName: "reviewTitle"
                Layout.fillWidth: true
                Layout.minimumWidth: 0
                text: "Paso 2 de 4 · Revisar búsqueda"
                color: window.textColor
                font.pixelSize: window.appTitleFontSize
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }

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
            objectName: "searchReviewFrame"
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.minimumHeight: 0
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

                ScrollView {
                    id: summaryScroll
                    objectName: "searchReviewScroll"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: 0
                    clip: true
                    contentWidth: Math.max(
                        0, availableWidth - reviewScrollBar.width - 4
                    )

                    ScrollBar.vertical: StyledScrollBar {
                        id: reviewScrollBar
                        objectName: "searchReviewScrollBar"
                        parent: summaryScroll
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom
                        anchors.right: parent.right
                        z: 2
                        policy: ScrollBar.AsNeeded
                        visible: summaryScroll.contentHeight
                            > summaryScroll.availableHeight + 1
                        interactive: true
                        active: true
                        opacity: 1
                        minimumSize: 0.08
                        transitions: []

                        background: Rectangle {
                            radius: width / 2
                            color: EduStyle.Style.subtleSurfaceColor
                        }
                        contentItem: Rectangle {
                            implicitWidth: EduStyle.Style.scrollBarThumbWidth
                            implicitHeight: EduStyle.Style.scrollBarThumbWidth
                            radius: width / 2
                            color: EduStyle.Style.primaryColor
                        }
                    }

                    Label {
                        objectName: "searchReviewSummary"
                        width: summaryScroll.contentWidth
                        text: window.currentSearchSummary()
                        color: window.mutedTextColor
                        wrapMode: Text.Wrap
                    }
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
