import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "styles" as EduStyle

ColumnLayout {
    id: root
    objectName: "actionBar"

    property var controller: null
    required property int windowWidth
    required property int searchCount

    signal scrapeRequested()
    signal excelExportRequested()
    signal pdfExportRequested()
    signal eduEscaleExportRequested()
    signal historyRequested()

    spacing: 0

    Rectangle {
        id: sourceBar
        objectName: "sourceBar"
        Layout.fillWidth: true
        Layout.preferredHeight: 32
        color: EduStyle.Style.canvasColor
        border.color: EduStyle.Style.borderColor

        Label {
            id: sourceLabel
            objectName: "sourceLabel"
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            verticalAlignment: Text.AlignVCenter
            text: '<a href="https://escale.minedu.gob.pe/padron-de-iiee">Fuente: Padrón de IIEE de ESCALE</a>'
            textFormat: Text.RichText
            onLinkActivated: link => Qt.openUrlExternally(link)
            palette.link: EduStyle.Style.primaryColor
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

            Label {
                Layout.fillWidth: true
                text: root.searchCount > 0
                    ? root.searchCount + " búsquedas listas para ejecutar y exportar"
                    : "Agrega una búsqueda para continuar"
                color: EduStyle.Style.mutedTextColor
                font.pixelSize: EduStyle.Style.smallFontSize
            }

            StyledProgressBar {
                id: progressIndicator
                objectName: "progressIndicator"
                Layout.fillWidth: true
                Layout.preferredHeight: 8
                visible: root.controller !== null
                    && root.controller.progressVisible
                from: 0
                to: root.controller ? Math.max(root.controller.progressMaximum, 1) : 1
                value: root.controller ? root.controller.progressValue : 0
                indeterminate: root.controller
                    ? root.controller.progressIndeterminate : false
            }

            Label {
                id: statusLabel
                objectName: "statusLabel"
                Layout.fillWidth: true
                text: !root.controller ? ""
                    : root.controller.scrapeRunning || root.controller.exportRunning
                    ? root.controller.progressMessage
                    : root.controller.statusText
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
                    visible: root.controller !== null
                        && !root.controller.scrapeRunning
                    text: !root.controller ? "Ejecutar búsqueda"
                        : root.controller.cancelRequested
                        ? "Cancelando extracción..."
                        : root.controller.scrapeRunning
                        ? "Consultando datos..."
                        : root.controller.exportRunning
                        ? "Generando archivo..."
                        : "Ejecutar búsqueda"
                    enabled: root.controller !== null
                        && !root.controller.scrapeRunning
                        && !root.controller.exportRunning
                        && root.searchCount > 0
                    Accessible.name: "Revisar filtros e iniciar búsqueda"
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
                    visible: root.controller !== null
                        && root.controller.scrapeRunning
                    enabled: root.controller !== null
                        && !root.controller.cancelRequested
                    text: root.controller && root.controller.cancelRequested
                        ? "Cancelando..." : "Cancelar"
                    onClicked: root.controller.cancelScrape()
                }

                StyledButton {
                    objectName: "downloadExcelButton"
                    labelObjectName: "downloadExcelButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: "Exportar Excel"
                    Accessible.name: "Exportar los resultados a Excel"
                    enabled: root.controller !== null
                        && root.controller.exportsAvailable
                        && !root.controller.exportRunning
                    onClicked: root.excelExportRequested()
                }

                StyledButton {
                    objectName: "downloadPdfButton"
                    labelObjectName: "downloadPdfButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: "Exportar PDF"
                    Accessible.name: "Exportar los resultados a PDF"
                    enabled: root.controller !== null
                        && root.controller.exportsAvailable
                        && !root.controller.exportRunning
                    onClicked: root.pdfExportRequested()
                }

                StyledButton {
                    objectName: "downloadEduEscaleButton"
                    labelObjectName: "downloadEduEscaleButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: "Exportar .eduescale"
                    Accessible.name: "Exportar los resultados a un archivo EduEscale"
                    enabled: root.controller !== null
                        && root.controller.exportsAvailable
                        && !root.controller.exportRunning
                    onClicked: root.eduEscaleExportRequested()
                }

                StyledButton {
                    objectName: "exportHistoryButton"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.minimumHeight: EduStyle.Style.actionButtonHeight
                    implicitHeight: EduStyle.Style.actionButtonHeight
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    padding: 8
                    text: "Historial"
                    Accessible.name: "Ver historial de exportaciones"
                    onClicked: root.historyRequested()
                }
            }
        }
    }
}
