import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Dialogs
import QtQuick.Layouts

ApplicationWindow {
    id: window
    required property var controller
    width: 820
    height: 860
    minimumWidth: 560
    minimumHeight: 600
    visible: true
    title: "Buscador de colegios - ESCALE"
    color: "#F3F6FB"

    Material.theme: Material.Light
    Material.primary: "#1E4D73"
    Material.accent: "#148A8A"

    onClosing: controller.shutdown()

    ListModel {
        id: filterSetsModel
        objectName: "filterSetsModel"
    }

    function selectedManagement() {
        var selected = []
        if (publicManagement.checked)
            selected.push("publica")
        if (privateManagement.checked)
            selected.push("privada")
        return selected.join(",")
    }

    function selectedLevels() {
        var selected = []
        if (initialLevel.checked)
            selected.push("inicial")
        if (primaryLevel.checked)
            selected.push("primaria")
        if (secondaryLevel.checked)
            selected.push("secundaria")
        return selected.join(",")
    }

    function addFilterSet() {
        filterSetsModel.append({
            regionCode: regionFilter.currentValue || "",
            provinceCode: provinceFilter.currentValue || "",
            districtCode: districtFilter.currentValue || "",
            regionLabel: regionFilter.currentValue ? regionFilter.currentText : "",
            provinceLabel: provinceFilter.currentValue ? provinceFilter.currentText : "",
            districtLabel: districtFilter.currentValue ? districtFilter.currentText : "",
            management: selectedManagement(),
            level: selectedLevels()
        })
    }

    function selectedFilterSetsJson() {
        var filterSets = []
        for (var index = 0; index < filterSetsModel.count; index++) {
            var filterSet = filterSetsModel.get(index)
            filterSets.push({
                region_code: filterSet.regionCode,
                province_code: filterSet.provinceCode,
                district_code: filterSet.districtCode,
                management: filterSet.management,
                level: filterSet.level
            })
        }
        return JSON.stringify(filterSets)
    }

    ScrollView {
        id: filterScrollView
        objectName: "filterScrollView"
        anchors.top: parent.top
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: actionBar.top
        padding: 24
        clip: true
        contentWidth: availableWidth
        contentHeight: pageContent.implicitHeight
        ScrollBar.vertical.policy: ScrollBar.AsNeeded

        ColumnLayout {
            id: pageContent
            width: filterScrollView.availableWidth
            spacing: 18

            Frame {
                Layout.fillWidth: true
                padding: 24
                background: Rectangle {
                    radius: 18
                    color: "#173B5E"
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 8

                    Label {
                        text: "PADRÓN EDUCATIVO · PERÚ"
                        color: "#A9D7DE"
                        font.pixelSize: 12
                        font.weight: Font.Bold
                    }

                    Label {
                        Layout.fillWidth: true
                        text: "Encuentra colegios,\na tu manera."
                        color: "white"
                        font.pixelSize: 30
                        font.weight: Font.DemiBold
                        wrapMode: Text.WordWrap
                    }

                    Label {
                        Layout.fillWidth: true
                        text: "Combina ubicaciones y criterios; descarga todos los resultados en un solo Excel."
                        color: "#D7E5F0"
                        font.pixelSize: 14
                        wrapMode: Text.WordWrap
                    }
                }
            }

            Frame {
                Layout.fillWidth: true
                padding: 22
                background: Rectangle {
                    radius: 18
                    color: "white"
                    border.color: "#E1E8F0"
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 14

                    Label {
                        text: "Configura tu búsqueda"
                        color: "#1F2937"
                        font.pixelSize: 20
                        font.weight: Font.DemiBold
                    }

                    GridLayout {
                        Layout.fillWidth: true
                        columns: 2
                        columnSpacing: 18
                        rowSpacing: 10

                        Label {
                            text: "Departamento"
                            color: "#475569"
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: regionFilter
                            Layout.fillWidth: true
                            model: controller.regions
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.regionsEnabled && !controller.scrapeRunning
                            onActivated: controller.selectRegion(currentValue)
                        }

                        Label {
                            text: "Provincia"
                            color: "#475569"
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: provinceFilter
                            Layout.fillWidth: true
                            model: controller.provinces
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.provincesEnabled && !controller.scrapeRunning
                            onActivated: controller.selectProvince(currentValue)
                        }

                        Label {
                            text: "Distrito"
                            color: "#475569"
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: districtFilter
                            Layout.fillWidth: true
                            model: controller.districts
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.districtsEnabled && !controller.scrapeRunning
                        }

                        Label {
                            text: "Gestión"
                            color: "#475569"
                            font.weight: Font.Medium
                            Layout.alignment: Qt.AlignTop
                        }
                        Flow {
                            Layout.fillWidth: true
                            spacing: 8
                            CheckBox {
                                id: publicManagement
                                objectName: "publicManagement"
                                text: "Pública"
                                enabled: !controller.scrapeRunning
                            }
                            CheckBox {
                                id: privateManagement
                                objectName: "privateManagement"
                                text: "Privada"
                                enabled: !controller.scrapeRunning
                            }
                        }

                        Label {
                            text: "Nivel educativo"
                            color: "#475569"
                            font.weight: Font.Medium
                            Layout.alignment: Qt.AlignTop
                        }
                        Flow {
                            Layout.fillWidth: true
                            spacing: 8
                            CheckBox {
                                id: initialLevel
                                objectName: "initialLevel"
                                text: "Inicial"
                                enabled: !controller.scrapeRunning
                            }
                            CheckBox {
                                id: primaryLevel
                                objectName: "primaryLevel"
                                text: "Primaria"
                                enabled: !controller.scrapeRunning
                            }
                            CheckBox {
                                id: secondaryLevel
                                objectName: "secondaryLevel"
                                text: "Secundaria"
                                enabled: !controller.scrapeRunning
                            }

                            Label {
                                Layout.fillWidth: true
                                text: "Marca al menos una opción en Gestión y Nivel educativo para agregar esta combinación."
                                color: "#64748B"
                                wrapMode: Text.WordWrap
                                font.pixelSize: 12
                            }
                        }
                    }

                    Button {
                        objectName: "addFilterSetButton"
                        Layout.fillWidth: true
                        text: "Agregar a mis búsquedas"
                        highlighted: true
                        enabled: !controller.scrapeRunning
                            && window.selectedManagement().length > 0
                            && window.selectedLevels().length > 0
                        onClicked: window.addFilterSet()
                    }

                    RowLayout {
                        Layout.fillWidth: true

                        Label {
                            Layout.fillWidth: true
                            text: "Mis búsquedas"
                            color: "#1F2937"
                            font.pixelSize: 18
                            font.weight: Font.DemiBold
                        }

                        Rectangle {
                            implicitWidth: countLabel.implicitWidth + 20
                            implicitHeight: 30
                            radius: 15
                            color: "#E6F2F3"

                            Label {
                                id: countLabel
                                anchors.centerIn: parent
                                text: filterSetsModel.count + " filtros"
                                color: "#14666A"
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6

                        Repeater {
                            model: filterSetsModel
                            delegate: Frame {
                                Layout.fillWidth: true
                                padding: 12

                                background: Rectangle {
                                    radius: 12
                                    color: "#F8FAFC"
                                    border.color: "#E5EBF2"
                                }

                                RowLayout {
                                    anchors.fill: parent
                                    spacing: 12

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 4

                                        Label {
                                            Layout.fillWidth: true
                                            wrapMode: Text.WordWrap
                                            text: {
                                                var location = []
                                                if (regionLabel)
                                                    location.push(regionLabel)
                                                if (provinceLabel)
                                                    location.push(provinceLabel)
                                                if (districtLabel)
                                                    location.push(districtLabel)
                                                return location.length
                                                    ? location.join(" / ")
                                                    : "Todo el Perú"
                                            }
                                            color: "#1F2937"
                                            font.weight: Font.DemiBold
                                        }

                                        Label {
                                            Layout.fillWidth: true
                                            wrapMode: Text.WordWrap
                                            text: "Gestión: " + management.replace(/,/g, ", ")
                                                + "    ·    Nivel: " + level.replace(/,/g, ", ")
                                            color: "#64748B"
                                            font.pixelSize: 12
                                        }
                                    }

                                    Button {
                                        text: "Quitar"
                                        flat: true
                                        enabled: !controller.scrapeRunning
                                        Material.foreground: "#B42318"
                                        onClicked: filterSetsModel.remove(index)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Label {
                Layout.fillWidth: true
                text: "El Excel incluirá nombre, dirección, localidad, centro poblado (CP), turno, alumnos, secciones, longitud, latitud y un enlace directo a Google Maps."
                color: "#526173"
                wrapMode: Text.WordWrap
                font.pixelSize: 12
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6
                visible: controller.progressVisible

                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 1
                    color: "#E1E8F0"
                }

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
                    color: "#475569"
                    wrapMode: Text.WordWrap
                }
            }

            Label {
                Layout.fillWidth: true
                text: controller.statusText
                wrapMode: Text.Wrap
                textFormat: Text.PlainText
                color: "#475569"
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

    Rectangle {
        id: actionBar
        objectName: "actionBar"
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 100
        color: "white"
        border.color: "#DCE4ED"
        z: 1

        ColumnLayout {
            anchors.fill: parent
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            anchors.topMargin: 8
            anchors.bottomMargin: 10
            spacing: 4

            Label {
                Layout.fillWidth: true
                text: filterSetsModel.count > 0
                    ? filterSetsModel.count + " combinaciones listas para exportar"
                    : "Agrega al menos una búsqueda para continuar"
                color: "#64748B"
                font.pixelSize: 11
            }

            Button {
                objectName: "scrapeButton"
                Layout.fillWidth: true
                Layout.fillHeight: true
                text: controller.scrapeRunning
                    ? "Descargando datos..."
                    : "Descargar y generar Excel"
                highlighted: true
                enabled: !controller.scrapeRunning && filterSetsModel.count > 0
                onClicked: saveDialog.open()
            }
        }
    }

    FileDialog {
        id: saveDialog
        title: "Guardar padrón en Excel"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Libro de Excel (*.xlsx)"]
        currentFile: controller.defaultOutputUrl

        onAccepted: controller.startScrape(
            selectedFile.toString(),
            window.selectedFilterSetsJson()
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
