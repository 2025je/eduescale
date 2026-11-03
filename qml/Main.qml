import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Dialogs
import QtQuick.Layouts

ApplicationWindow {
    id: window
    width: 760
    height: 760
    minimumWidth: 560
    minimumHeight: 600
    visible: true
    title: "Buscador de colegios - ESCALE"

    Material.theme: Material.Light
    Material.accent: Material.Indigo

    onClosing: controller.shutdown()

    property var managementOptions: [
        { label: "Todas", code: "" },
        { label: "Pública", code: "publica" },
        { label: "Privada", code: "privada" }
    ]
    property var levelOptions: [
        { label: "Todos", code: "" },
        { label: "Inicial", code: "inicial" },
        { label: "Primaria", code: "primaria" },
        { label: "Secundaria", code: "secundaria" }
    ]

    ScrollView {
        anchors.fill: parent
        padding: 24
        clip: true
        ScrollBar.vertical.policy: ScrollBar.AsNeeded

        ColumnLayout {
            width: parent.width
            spacing: 18

            Label {
                Layout.fillWidth: true
                text: "Buscador de colegios del Perú"
                font.pixelSize: 26
                font.weight: Font.DemiBold
                wrapMode: Text.WordWrap
            }

            Label {
                Layout.fillWidth: true
                text: "Consulta el padrón oficial de ESCALE con Scrapy y exporta los resultados a Excel."
                wrapMode: Text.WordWrap
                opacity: 0.8
            }

            Frame {
                Layout.fillWidth: true
                padding: 20
                background: Rectangle {
                    radius: 12
                    color: "white"
                    border.color: "#d8dce5"
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 14

                    Label {
                        text: "Filtros"
                        font.pixelSize: 18
                        font.weight: Font.Medium
                    }

                    GridLayout {
                        Layout.fillWidth: true
                        columns: 2
                        columnSpacing: 18
                        rowSpacing: 10

                        Label { text: "Departamento" }
                        ComboBox {
                            id: regionFilter
                            Layout.fillWidth: true
                            model: controller.regions
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.regionsEnabled && !controller.scrapeRunning
                            onActivated: controller.selectRegion(currentValue)
                        }

                        Label { text: "Provincia" }
                        ComboBox {
                            id: provinceFilter
                            Layout.fillWidth: true
                            model: controller.provinces
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.provincesEnabled && !controller.scrapeRunning
                            onActivated: controller.selectProvince(currentValue)
                        }

                        Label { text: "Distrito" }
                        ComboBox {
                            id: districtFilter
                            Layout.fillWidth: true
                            model: controller.districts
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.districtsEnabled && !controller.scrapeRunning
                        }

                        Label { text: "Gestión" }
                        ComboBox {
                            id: managementFilter
                            Layout.fillWidth: true
                            model: window.managementOptions
                            textRole: "label"
                            valueRole: "code"
                            enabled: !controller.scrapeRunning
                        }

                        Label { text: "Nivel educativo" }
                        ComboBox {
                            id: levelFilter
                            Layout.fillWidth: true
                            model: window.levelOptions
                            textRole: "label"
                            valueRole: "code"
                            enabled: !controller.scrapeRunning
                        }
                    }
                }
            }

            Label {
                Layout.fillWidth: true
                text: "El Excel incluirá nombre, dirección, localidad, centro poblado (CP), turno, alumnos, secciones, longitud, latitud y un enlace directo a Google Maps."
                wrapMode: Text.WordWrap
                opacity: 0.8
            }

            Button {
                Layout.fillWidth: true
                Layout.minimumHeight: 48
                text: controller.scrapeRunning
                    ? "Extrayendo datos..."
                    : "Scrapear y generar Excel"
                highlighted: true
                enabled: !controller.scrapeRunning
                onClicked: saveDialog.open()
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6
                visible: controller.progressVisible

                ProgressBar {
                    Layout.fillWidth: true
                    from: 0
                    to: Math.max(controller.progressMaximum, 1)
                    value: controller.progressValue
                    indeterminate: controller.progressIndeterminate
                }

                Label {
                    Layout.fillWidth: true
                    text: controller.progressMessage
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                }
            }

            Label {
                Layout.fillWidth: true
                text: controller.statusText
                wrapMode: Text.Wrap
                textFormat: Text.PlainText
                opacity: 0.85
            }

            Button {
                Layout.fillWidth: true
                text: "Abrir carpeta del Excel"
                visible: controller.openFolderVisible
                onClicked: controller.openOutputFolder()
            }

            Item {
                Layout.fillHeight: true
                Layout.minimumHeight: 4
            }

            Label {
                Layout.fillWidth: true
                text: '<a href="https://escale.minedu.gob.pe/padron-de-iiee">Fuente: Padrón de IIEE de ESCALE</a>'
                textFormat: Text.RichText
                onLinkActivated: link => Qt.openUrlExternally(link)
            }
        }
    }

    FileDialog {
        id: saveDialog
        title: "Guardar padrón en Excel"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Libro de Excel (*.xlsx)"]
        currentFile: Qt.resolvedUrl("../colegios_escale.xlsx")

        onAccepted: controller.startScrape(
            selectedFile.toString(),
            regionFilter.currentValue,
            provinceFilter.currentValue,
            districtFilter.currentValue,
            managementFilter.currentValue,
            levelFilter.currentValue
        )
    }

    Dialog {
        id: errorDialog
        property string details: ""
        title: "Error"
        modal: true
        anchors.centerIn: parent
        width: Math.min(window.width - 40, 760)
        height: Math.min(window.height - 40, 560)
        standardButtons: Dialog.NoButton

        contentItem: ColumnLayout {
            spacing: 12

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true

                TextArea {
                    text: errorDialog.details
                    readOnly: true
                    selectByMouse: true
                    wrapMode: TextEdit.Wrap
                }
            }

            Button {
                Layout.alignment: Qt.AlignRight
                text: "Cerrar"
                onClicked: errorDialog.close()
            }
        }
    }

    Connections {
        target: controller

        function onErrorRaised(title, details) {
            errorDialog.title = title
            errorDialog.details = details
            errorDialog.open()
        }
    }
}
