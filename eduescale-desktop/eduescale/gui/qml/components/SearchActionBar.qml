import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Styles 1.0 as EduStyle

ColumnLayout {
    id: root
    objectName: "actionBar"

    property var viewModel: null
    required property int windowWidth
    required property int searchCount
    property int workflowStep: 0

    signal scrapeRequested()

    spacing: 0

    Rectangle {
        id: actionSurface
        objectName: "actionSurface"
        Layout.fillWidth: true
        implicitHeight: actionContent.implicitHeight
            + actionContent.anchors.topMargin + actionContent.anchors.bottomMargin
        color: EduStyle.Style.surfaceColor
        border.color: EduStyle.Style.borderColor

        ColumnLayout {
            id: actionContent
            anchors.fill: parent
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            anchors.topMargin: 8
            anchors.bottomMargin: 24
            spacing: 4

            RowLayout {
                Layout.fillWidth: true
                visible: root.workflowStep === 0
                spacing: 6

                MaterialSymbol {
                    symbolName: root.searchCount > 0 ? "search" : "add"
                    symbolSize: EduStyle.Style.smallFontSize + 2
                    symbolColor: EduStyle.Style.mutedTextColor
                }

                Label {
                    Layout.fillWidth: true
                    text: root.searchCount > 0
                        ? root.searchCount + " búsquedas listas para revisar"
                        : "Agrega una búsqueda para continuar"
                    color: EduStyle.Style.mutedTextColor
                    font.pixelSize: EduStyle.Style.smallFontSize
                    wrapMode: Text.WordWrap
                }
            }

            StyledProgressBar {
                id: progressIndicator
                objectName: "progressIndicator"
                Layout.fillWidth: true
                Layout.preferredHeight: 8
                visible: root.viewModel != null
                    && root.viewModel.progressVisible
                    && root.workflowStep >= 2
                from: 0
                to: root.viewModel != null
                    ? Math.max(root.viewModel.progressMaximum, 1) : 1
                value: root.viewModel != null ? root.viewModel.progressValue : 0
                indeterminate: root.viewModel != null
                    ? root.viewModel.progressIndeterminate : false
            }

            RowLayout {
                Layout.fillWidth: true
                visible: root.workflowStep >= 2
                spacing: 6

                MaterialSymbol {
                    symbolName: root.viewModel != null
                        && (root.viewModel.scrapeRunning || root.viewModel.exportRunning)
                        ? "hourglass_top" : "info"
                    symbolSize: EduStyle.Style.smallFontSize + 2
                    symbolColor: EduStyle.Style.mutedTextColor
                    Layout.alignment: Qt.AlignTop
                }

                Label {
                    id: statusLabel
                    objectName: "statusLabel"
                    Layout.fillWidth: true
                    text: root.viewModel == null ? ""
                        : root.viewModel.scrapeRunning || root.viewModel.exportRunning
                        ? root.viewModel.progressMessage
                        : root.viewModel.statusText
                    color: EduStyle.Style.mutedTextColor
                    font.pixelSize: EduStyle.Style.smallFontSize
                    wrapMode: Text.WordWrap
                    maximumLineCount: 2
                    elide: Text.ElideRight
                }
            }

            GridLayout {
                id: actionButtons
                objectName: "actionButtons"
                Layout.fillWidth: true
                columns: root.windowWidth < 900 ? 2
                    : root.windowWidth < 1200 ? 3 : 4
                columnSpacing: 8
                rowSpacing: 8

                StyledButton {
                    id: scrapeButton
                    objectName: "scrapeButton"
                    labelObjectName: "scrapeButtonLabel"
                    variant: "primary"
                    iconSize: EduStyle.Style.bodyFontSize + 8
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight + 2
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    visible: root.viewModel != null
                        && root.workflowStep === 0
                        && !root.viewModel.scrapeRunning
                    iconName: "search"
                    text: root.viewModel == null ? "Revisar búsqueda"
                        : root.viewModel.cancelRequested
                        ? "Cancelando extracción..."
                        : root.viewModel.scrapeRunning
                        ? "Consultando datos..."
                        : root.viewModel.exportRunning
                        ? "Generando archivo..."
                        : "Revisar búsqueda"
                    enabled: root.viewModel != null
                        && !root.viewModel.scrapeRunning
                        && !root.viewModel.exportRunning
                        && root.searchCount > 0
                        && (root.viewModel.dataSource !== "offline"
                            || root.viewModel.offlineDatabaseAvailable)
                    Accessible.name: "Revisar filtros antes de iniciar la búsqueda"
                    onClicked: root.scrapeRequested()
                }

                StyledButton {
                    id: cancelScrapeButton
                    objectName: "cancelScrapeButton"
                    labelObjectName: "cancelScrapeButtonLabel"
                    variant: "danger"
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    visible: root.viewModel != null
                        && root.workflowStep === 2
                        && root.viewModel.scrapeRunning
                    enabled: root.viewModel != null
                        && !root.viewModel.cancelRequested
                    iconName: "cancel"
                    text: root.viewModel != null && root.viewModel.cancelRequested
                        ? "Cancelando..." : "Cancelar"
                    onClicked: root.viewModel.cancelScrape()
                }

            }
        }
    }
}
