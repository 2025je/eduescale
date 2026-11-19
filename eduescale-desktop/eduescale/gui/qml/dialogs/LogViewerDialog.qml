import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Components 1.0
import EduEscale.Gui.Styles 1.0 as EduStyle

StyledDialog {
    id: root
    objectName: "logViewerDialog"

    required property var viewModel
    readonly property bool available: !!viewModel

    title: "Registros de diagnóstico"
    modal: true
    anchors.centerIn: parent
    width: Math.min(parent.width - 40, 820)
    height: Math.min(parent.height - 40, 600)
    standardButtons: Dialog.NoButton
    onOpened: {
        if (root.available)
            root.viewModel.selectFile(logFileSelector.currentIndex)
    }

    function updateHighlighting() {
        if (!root.viewModel || !logTextArea || !rawLogToggle || !logTextArea.textDocument)
            return
        root.viewModel.configureHighlighting(logTextArea.textDocument, {
            timestamp: EduStyle.Style.logTimestampColor.toString(),
            source: EduStyle.Style.logSourceColor.toString(),
            debug: EduStyle.Style.logDebugColor.toString(),
            info: EduStyle.Style.logInfoColor.toString(),
            warning: EduStyle.Style.logWarningColor.toString(),
            error: EduStyle.Style.logErrorColor.toString(),
            critical: EduStyle.Style.logCriticalColor.toString(),
            detail: EduStyle.Style.logDetailColor.toString()
        }, !rawLogToggle.checked)
    }

    Component.onCompleted: updateHighlighting()
    onViewModelChanged: updateHighlighting()

    Connections {
        target: EduStyle.Style
        function onDarkThemeChanged() {
            root.updateHighlighting()
        }
    }

    Timer {
        objectName: "logRefreshTimer"
        interval: 2000
        running: root.visible && root.available
        repeat: true
        onTriggered: {
            if (root.available)
                root.viewModel.refresh()
        }
    }

    contentItem: ColumnLayout {
        spacing: 10

        Label {
            Layout.fillWidth: true
            text: "Los registros se actualizan automáticamente y pueden contener rutas y detalles de errores."
            color: EduStyle.Style.mutedTextColor
            wrapMode: Text.WordWrap
        }

        StyledComboBox {
            id: logFileSelector
            objectName: "logFileSelector"
            Layout.fillWidth: true
            Accessible.name: "Archivo de registros"
            enabled: root.available
            model: root.available ? root.viewModel.files : []
            onActivated: index => {
                if (root.available)
                    root.viewModel.selectFile(index)
            }
        }

        StyledCheckBox {
            id: rawLogToggle
            objectName: "rawLogToggle"
            text: "Mostrar texto original"
            enabled: root.available
            onCheckedChanged: root.updateHighlighting()
        }

        Label {
            objectName: "logViewerError"
            Layout.fillWidth: true
            visible: !root.available || root.viewModel.error.length > 0
            text: root.available ? root.viewModel.error
                : "El visor de registros no está disponible."
            color: EduStyle.Style.dangerColor
            wrapMode: Text.WordWrap
        }

        ScrollView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.minimumHeight: 0
            clip: true

            StyledTextArea {
                id: logTextArea
                objectName: "logViewerContent"
                text: !root.available ? "" : rawLogToggle.checked
                    ? root.viewModel.rawContent : root.viewModel.content
                placeholderText: "No hay registros en este archivo."
                readOnly: true
                selectByMouse: true
                textFormat: TextEdit.PlainText
                wrapMode: TextEdit.Wrap
            }
        }

        RowLayout {
            Layout.fillWidth: true

            StyledButton {
                objectName: "firstLogPageButton"
                text: "Primero"
                enabled: root.available && root.viewModel.canPrevious
                onClicked: {
                    if (root.available)
                        root.viewModel.goToPage(1)
                }
            }

            StyledButton {
                objectName: "previousLogPageButton"
                text: "Anterior"
                enabled: root.available && root.viewModel.canPrevious
                onClicked: {
                    if (root.available)
                        root.viewModel.previousPage()
                }
            }

            Label {
                text: "Bloque"
                color: EduStyle.Style.mutedTextColor
            }

            SpinBox {
                objectName: "logPageSelector"
                Layout.preferredWidth: 105
                from: 1
                to: root.available ? root.viewModel.pageCount : 1
                value: root.available ? root.viewModel.pageNumber : 1
                enabled: root.available
                editable: true
                Accessible.name: "Número de bloque"
                onValueModified: {
                    if (root.available)
                        root.viewModel.goToPage(value)
                }
            }

            Label {
                objectName: "logPageLabel"
                text: "de " + (root.available ? root.viewModel.pageCount : 1)
                color: EduStyle.Style.mutedTextColor
            }

            StyledButton {
                objectName: "nextLogPageButton"
                text: "Siguiente"
                enabled: root.available && root.viewModel.canNext
                onClicked: {
                    if (root.available)
                        root.viewModel.nextPage()
                }
            }

            StyledButton {
                objectName: "lastLogPageButton"
                text: "Último"
                enabled: root.available && root.viewModel.canNext
                onClicked: {
                    if (root.available)
                        root.viewModel.goToPage(root.viewModel.pageCount)
                }
            }
        }

        StyledButton {
            objectName: "closeLogsButton"
            Layout.alignment: Qt.AlignRight
            text: "Cerrar"
            Accessible.name: "Cerrar registros de diagnóstico"
            onClicked: root.close()
        }
    }
}
