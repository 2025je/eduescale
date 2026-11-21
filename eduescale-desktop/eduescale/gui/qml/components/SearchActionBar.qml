import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Styles 1.0 as EduStyle
import "qrc:/Icons.qml" as Icons

ColumnLayout {
    id: root
    objectName: "actionBar"

    property var viewModel: null
    required property int windowWidth
    required property int searchCount
    property int workflowStep: 0

    signal scrapeRequested()
    signal excelExportRequested()
    signal pdfExportRequested()
    signal eduEscaleExportRequested()
    signal historyRequested()
    signal offlineDatabaseSelectionRequested()
    signal offlineDatabaseUpdateRequested()

    spacing: 0

    Rectangle {
        id: sourceBar
        objectName: "sourceBar"
        Layout.fillWidth: true
        Layout.preferredHeight: 32
        visible: root.workflowStep === 0
        color: EduStyle.Style.canvasColor
        border.color: EduStyle.Style.borderColor

        Label {
            id: sourceLabel
            objectName: "sourceLabel"
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: root.windowWidth < 720 ? 174 : 214
            verticalAlignment: Text.AlignVCenter
            text: '<a href="https://escale.minedu.gob.pe/padron-de-iiee">Fuente: Padrón de IIEE de ESCALE</a>'
            textFormat: Text.RichText
            onLinkActivated: link => Qt.openUrlExternally(link)
            palette.link: EduStyle.Style.primaryColor
        }

        StyledComboBox {
            id: dataSourceCombo
            objectName: "dataSourceCombo"
            anchors.right: parent.right
            anchors.rightMargin: 12
            anchors.verticalCenter: parent.verticalCenter
            width: root.windowWidth < 720 ? 150 : 190
            height: 28
            model: ["ESCALE en línea", "Base offline"]
            currentIndex: root.viewModel != null
                && root.viewModel.dataSource === "offline" ? 1 : 0
            enabled: root.viewModel != null
                && !root.viewModel.scrapeRunning
                && !root.viewModel.exportRunning
            Accessible.name: "Fuente de datos"
            onActivated: index => root.viewModel.setDataSource(
                index === 1 ? "offline" : "online"
            )
        }
    }

    Rectangle {
        id: actionSurface
        objectName: "actionSurface"
        Layout.fillWidth: true
        implicitHeight: actionContent.implicitHeight + 18
        color: EduStyle.Style.surfaceColor
        border.color: EduStyle.Style.borderColor

        ColumnLayout {
            id: actionContent
            anchors.fill: parent
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            anchors.topMargin: 8
            anchors.bottomMargin: 10
            spacing: 4

            RowLayout {
                id: offlineDatabaseControls
                objectName: "offlineDatabaseControls"
                Layout.fillWidth: true
                spacing: 8
                visible: root.viewModel != null
                    && root.viewModel.dataSource === "offline"
                    && root.workflowStep === 0

                StyledButton {
                    objectName: "selectOfflineDatabaseButton"
                    labelObjectName: "selectOfflineDatabaseButtonLabel"
                    text: Icons.Icons.filterDepartment + " Elegir base"
                    enabled: root.viewModel != null
                        && !root.viewModel.scrapeRunning
                        && !root.viewModel.exportRunning
                    onClicked: root.offlineDatabaseSelectionRequested()
                }

                StyledButton {
                    objectName: "updateOfflineDatabaseButton"
                    labelObjectName: "updateOfflineDatabaseButtonLabel"
                    text: Icons.Icons.actionRefresh + " Actualizar base"
                    enabled: root.viewModel != null
                        && !root.viewModel.scrapeRunning
                        && !root.viewModel.exportRunning
                    onClicked: root.offlineDatabaseUpdateRequested()
                }

                Label {
                    objectName: "offlineDatabaseStatsLabel"
                    Layout.fillWidth: true
                    text: root.viewModel != null
                        ? root.viewModel.offlineDatabaseName + " · "
                            + root.viewModel.offlineDatabaseStats : ""
                    color: EduStyle.Style.mutedTextColor
                    font.pixelSize: EduStyle.Style.smallFontSize
                    wrapMode: Text.WordWrap
                    maximumLineCount: 2
                    elide: Text.ElideRight
                }
            }

            Label {
                Layout.fillWidth: true
                visible: root.workflowStep === 0
                text: root.searchCount > 0
                    ? root.searchCount + " búsquedas listas para revisar"
                    : "Agrega una búsqueda para continuar"
                color: EduStyle.Style.mutedTextColor
                font.pixelSize: EduStyle.Style.smallFontSize
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

            Label {
                id: statusLabel
                objectName: "statusLabel"
                Layout.fillWidth: true
                visible: root.workflowStep >= 2
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
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight + 2
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    visible: root.viewModel != null
                        && root.workflowStep === 0
                        && !root.viewModel.scrapeRunning
                    text: root.viewModel == null ? Icons.Icons.filterSearch + " Revisar búsqueda"
                        : root.viewModel.cancelRequested
                        ? Icons.Icons.stateLoading + " Cancelando extracción..."
                        : root.viewModel.scrapeRunning
                        ? Icons.Icons.stateLoading + " Consultando datos..."
                        : root.viewModel.exportRunning
                        ? Icons.Icons.stateLoading + " Generando archivo..."
                        : Icons.Icons.filterSearch + " Revisar búsqueda"
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
                    text: root.viewModel != null && root.viewModel.cancelRequested
                        ? "Cancelando..." : "Cancelar"
                    onClicked: root.viewModel.cancelScrape()
                }

                StyledButton {
                    visible: root.viewModel != null
                        && (root.workflowStep === 3
                            || (root.workflowStep === 0
                                && root.viewModel.exportsAvailable))
                    objectName: "downloadExcelButton"
                    labelObjectName: "downloadExcelButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: Icons.Icons.fileExcel + " Exportar Excel"
                    Accessible.name: "Exportar los resultados a Excel"
                    enabled: root.viewModel != null
                        && root.viewModel.exportsAvailable
                        && !root.viewModel.exportRunning
                    onClicked: root.excelExportRequested()
                }

                StyledButton {
                    visible: root.viewModel != null
                        && (root.workflowStep === 3
                            || (root.workflowStep === 0
                                && root.viewModel.exportsAvailable))
                    objectName: "downloadPdfButton"
                    labelObjectName: "downloadPdfButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: Icons.Icons.filePdf + " Exportar PDF"
                    Accessible.name: "Exportar los resultados a PDF"
                    enabled: root.viewModel != null
                        && root.viewModel.exportsAvailable
                        && !root.viewModel.exportRunning
                    onClicked: root.pdfExportRequested()
                }

                StyledButton {
                    visible: root.viewModel != null
                        && (root.workflowStep === 3
                            || (root.workflowStep === 0
                                && root.viewModel.exportsAvailable))
                    objectName: "downloadEduEscaleButton"
                    labelObjectName: "downloadEduEscaleButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: Icons.Icons.fileZip + " Exportar .eduescale"
                    Accessible.name: "Exportar los resultados a un archivo EduEscale"
                    enabled: root.viewModel != null
                        && root.viewModel.exportsAvailable
                        && !root.viewModel.exportRunning
                    onClicked: root.eduEscaleExportRequested()
                }

                StyledButton {
                    visible: root.workflowStep === 0 || root.workflowStep === 3
                    objectName: "exportHistoryButton"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: Icons.Icons.navHistory + " Historial"
                    Accessible.name: "Ver historial de exportaciones"
                    onClicked: root.historyRequested()
                }
            }
        }
    }
}
