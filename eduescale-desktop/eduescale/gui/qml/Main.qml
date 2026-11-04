import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Dialogs
import QtQuick.Layouts

ApplicationWindow {
    id: window
    required property var controller
    width: controller.windowWidth
    height: controller.windowHeight
    minimumWidth: 560
    minimumHeight: 600
    visible: true
    title: "EduEscale | Buscador de colegios"
    color: "#F4F7F5"

    readonly property color primaryColor: "#176B50"
    readonly property color primaryHoverColor: "#145D46"
    readonly property color primaryActiveColor: "#104B39"
    readonly property color primarySubtleColor: "#E8F4ED"
    readonly property color primarySubtleHoverColor: "#D5E9DC"
    readonly property color primaryBorderColor: "#B9D7C6"
    readonly property color primaryEmphasisColor: "#164C39"
    readonly property color borderColor: "#E6ECE8"
    readonly property color textColor: "#1C262B"
    readonly property color mutedTextColor: "#65746C"
    readonly property int cardRadius: 20
    readonly property int controlRadius: 14

    Material.theme: Material.Light
    Material.primary: primaryColor
    Material.accent: primaryColor

    onClosing: {
        controller.saveWindowSize(width, height)
        controller.shutdown()
    }

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
        padding: 20
        clip: true
        contentWidth: availableWidth
        contentHeight: pageContent.implicitHeight
        ScrollBar.vertical.policy: ScrollBar.AsNeeded

        ColumnLayout {
            id: pageContent
            width: filterScrollView.availableWidth
            spacing: 16

            Frame {
                Layout.fillWidth: true
                padding: 20
                background: Rectangle {
                    radius: window.cardRadius
                    color: "#FFFFFF"
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 12

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 14

                        Rectangle {
                            Layout.preferredWidth: 52
                            Layout.preferredHeight: 52
                            radius: width / 2
                            color: window.primaryColor

                            Label {
                                anchors.centerIn: parent
                                text: "E"
                                color: "#FFFFFF"
                                font.pixelSize: 25
                                font.weight: Font.Bold
                            }
                        }

                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 2

                            Label {
                                objectName: "brandTitle"
                                text: "EduEscale"
                                color: window.textColor
                                font.pixelSize: 26
                                font.weight: Font.DemiBold
                            }

                            Label {
                                text: "Instituciones educativas del Perú"
                                color: window.mutedTextColor
                                font.pixelSize: 13
                            }
                        }
                    }

                    Label {
                        Layout.fillWidth: true
                        text: "Combina ubicaciones y criterios para encontrar colegios y exportar sus datos."
                        color: window.mutedTextColor
                        font.pixelSize: 14
                        wrapMode: Text.WordWrap
                    }
                }
            }

            Frame {
                Layout.fillWidth: true
                padding: 22
                background: Rectangle {
                    radius: window.cardRadius
                    color: "white"
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 14

                    Label {
                        text: "Configura tu búsqueda"
                        color: window.textColor
                        font.pixelSize: 20
                        font.weight: Font.DemiBold
                    }

                    GridLayout {
                        id: filterGrid
                        objectName: "filterGrid"
                        Layout.fillWidth: true
                        columns: window.width < 720 ? 1 : 2
                        columnSpacing: window.width < 720 ? 0 : 18
                        rowSpacing: window.width < 720 ? 4 : 10

                        Label {
                            text: "Departamento"
                            color: window.mutedTextColor
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: regionFilter
                            objectName: "regionFilter"
                            Layout.fillWidth: true
                            implicitHeight: 52
                            model: controller.regions
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.regionsEnabled && !controller.scrapeRunning
                            onActivated: controller.selectRegion(currentValue)
                            background: Rectangle {
                                radius: window.controlRadius
                                color: regionFilter.enabled ? "#FFFFFF" : "#F2F6F3"
                                border.width: regionFilter.activeFocus ? 2 : 1
                                border.color: regionFilter.activeFocus
                                    ? window.primaryColor
                                    : window.borderColor
                            }
                        }

                        Label {
                            text: "Provincia"
                            color: window.mutedTextColor
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: provinceFilter
                            objectName: "provinceFilter"
                            Layout.fillWidth: true
                            implicitHeight: 52
                            model: controller.provinces
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.provincesEnabled && !controller.scrapeRunning
                            onActivated: controller.selectProvince(currentValue)
                            background: Rectangle {
                                radius: window.controlRadius
                                color: provinceFilter.enabled ? "#FFFFFF" : "#F2F6F3"
                                border.width: provinceFilter.activeFocus ? 2 : 1
                                border.color: provinceFilter.activeFocus
                                    ? window.primaryColor
                                    : window.borderColor
                            }
                        }

                        Label {
                            text: "Distrito"
                            color: window.mutedTextColor
                            font.weight: Font.Medium
                        }
                        ComboBox {
                            id: districtFilter
                            objectName: "districtFilter"
                            Layout.fillWidth: true
                            implicitHeight: 52
                            model: controller.districts
                            textRole: "label"
                            valueRole: "code"
                            enabled: controller.districtsEnabled && !controller.scrapeRunning
                            background: Rectangle {
                                radius: window.controlRadius
                                color: districtFilter.enabled ? "#FFFFFF" : "#F2F6F3"
                                border.width: districtFilter.activeFocus ? 2 : 1
                                border.color: districtFilter.activeFocus
                                    ? window.primaryColor
                                    : window.borderColor
                            }
                        }

                        Label {
                            text: "Gestión"
                            color: window.mutedTextColor
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
                            color: window.mutedTextColor
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
                        }

                        Label {
                            objectName: "searchHelpText"
                            Layout.fillWidth: true
                            Layout.columnSpan: filterGrid.columns
                            text: "Marca al menos una opción en Gestión y Nivel educativo para agregar esta combinación."
                            color: window.mutedTextColor
                            wrapMode: Text.WordWrap
                            font.pixelSize: 12
                        }
                    }

                    Button {
                        id: addFilterSetButton
                        objectName: "addFilterSetButton"
                        Layout.fillWidth: true
                        implicitHeight: 52
                        text: "Agregar a mis búsquedas"
                        highlighted: true
                        Material.foreground: enabled ? "#FFFFFF" : window.primaryEmphasisColor
                        enabled: !controller.scrapeRunning
                            && window.selectedManagement().length > 0
                            && window.selectedLevels().length > 0
                        onClicked: window.addFilterSet()
                        background: Rectangle {
                            radius: window.controlRadius
                            color: !addFilterSetButton.enabled
                                ? window.primarySubtleColor
                                : addFilterSetButton.pressed
                                    ? window.primaryActiveColor
                                    : addFilterSetButton.hovered
                                        ? window.primaryHoverColor
                                        : window.primaryColor
                            border.width: 1
                            border.color: addFilterSetButton.enabled
                                ? window.primaryBorderColor
                                : window.primarySubtleColor
                            Behavior on color {
                                ColorAnimation { duration: 120 }
                            }
                        }
                    }

                    RowLayout {
                        Layout.fillWidth: true

                        Label {
                            Layout.fillWidth: true
                            text: "Mis búsquedas"
                            color: window.textColor
                            font.pixelSize: 18
                            font.weight: Font.DemiBold
                        }

                        Rectangle {
                            implicitWidth: countLabel.implicitWidth + 20
                            implicitHeight: 34
                            radius: height / 2
                            color: window.primarySubtleColor
                            border.width: 1
                            border.color: window.primaryBorderColor

                            Label {
                                id: countLabel
                                anchors.centerIn: parent
                                text: filterSetsModel.count + " filtros"
                                color: window.primaryEmphasisColor
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6

                        Rectangle {
                            objectName: "emptySearchState"
                            Layout.fillWidth: true
                            implicitHeight: emptySearchLabel.implicitHeight + 24
                            visible: filterSetsModel.count === 0
                            radius: window.controlRadius
                            color: "#F7FAF8"
                            border.width: 1
                            border.color: window.borderColor

                            Label {
                                id: emptySearchLabel
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.margins: 12
                                text: "Aún no agregas búsquedas. Elige los criterios y pulsa «Agregar a mis búsquedas»."
                                color: window.mutedTextColor
                                wrapMode: Text.WordWrap
                            }
                        }

                        Repeater {
                            model: filterSetsModel
                            delegate: Frame {
                                Layout.fillWidth: true
                                padding: 12

                                background: Rectangle {
                                    radius: window.controlRadius
                                    color: "#F7FAF8"
                                    border.width: 1
                                    border.color: window.borderColor
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
                                            color: window.textColor
                                            font.weight: Font.DemiBold
                                        }

                                        Label {
                                            Layout.fillWidth: true
                                            wrapMode: Text.WordWrap
                                            text: "Gestión: " + management.replace(/,/g, ", ")
                                                + "    ·    Nivel: " + level.replace(/,/g, ", ")
                                            color: window.mutedTextColor
                                            font.pixelSize: 12
                                        }
                                    }

                                    Button {
                                        text: "Quitar"
                                        flat: true
                                        enabled: !controller.scrapeRunning
                                        Material.foreground: "#B42318"
                                        background: Rectangle {
                                            radius: height / 2
                                            color: parent.pressed
                                                ? "#FCE8E6"
                                                : parent.hovered
                                                    ? "#FFF4F2"
                                                    : "transparent"
                                        }
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
                text: "El Excel incluirá nombre, dirección, localidad, CP, turno, alumnos y secciones. También se generará un archivo .eduescale para la app móvil."
                color: window.mutedTextColor
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
                    color: window.borderColor
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
                    color: window.mutedTextColor
                    wrapMode: Text.WordWrap
                }
            }

            Label {
                Layout.fillWidth: true
                text: controller.statusText
                wrapMode: Text.Wrap
                textFormat: Text.PlainText
                color: window.mutedTextColor
            }

            Button {
                Layout.fillWidth: true
                text: "Abrir carpeta del Excel"
                visible: controller.openFolderVisible
                onClicked: controller.openOutputFolder()
                Material.foreground: window.primaryColor
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
                palette.link: window.primaryColor
            }
        }
    }

    Rectangle {
        id: actionBar
        objectName: "actionBar"
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: controller.scrapeRunning ? 142 : 100
        color: "white"
        border.color: window.borderColor
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
                color: window.mutedTextColor
                font.pixelSize: 11
            }

            RowLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 10

                Button {
                    id: scrapeButton
                    objectName: "scrapeButton"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    implicitHeight: 52
                    text: controller.cancelRequested
                        ? "Cancelando extracción..."
                        : controller.scrapeRunning
                        ? "Descargando datos..."
                        : "Descargar y generar Excel"
                    highlighted: true
                    enabled: !controller.scrapeRunning && filterSetsModel.count > 0
                    Material.foreground: enabled ? "#FFFFFF" : window.primaryEmphasisColor
                    onClicked: saveDialog.open()
                    background: Rectangle {
                        radius: window.controlRadius
                        color: !scrapeButton.enabled
                            ? window.primarySubtleColor
                            : scrapeButton.pressed
                                ? window.primaryActiveColor
                                : scrapeButton.hovered
                                    ? window.primaryHoverColor
                                    : window.primaryColor
                        border.width: 1
                        border.color: scrapeButton.enabled
                            ? window.primaryBorderColor
                            : window.primarySubtleColor
                        Behavior on color {
                            ColorAnimation { duration: 120 }
                        }
                    }
                }

                Button {
                    id: cancelScrapeButton
                    objectName: "cancelScrapeButton"
                    Layout.preferredWidth: 120
                    Layout.fillHeight: true
                    visible: controller.scrapeRunning
                    enabled: !controller.cancelRequested
                    text: controller.cancelRequested ? "Cancelando..." : "Cancelar"
                    onClicked: controller.cancelScrape()
                    Material.foreground: "#B42318"
                    background: Rectangle {
                        radius: window.controlRadius
                        color: cancelScrapeButton.pressed
                            ? "#FCE8E6"
                            : cancelScrapeButton.hovered
                                ? "#FFF4F2"
                                : "#FFFFFF"
                        border.width: 1
                        border.color: "#E6B8B3"
                    }
                }
            }
        }
    }

    FileDialog {
        id: saveDialog
        title: "Guardar Excel y archivo EduScale"
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
        background: Rectangle {
            radius: window.cardRadius
            color: "#FFFFFF"
            border.width: 1
            border.color: window.borderColor
        }

        contentItem: ColumnLayout {
            spacing: 12

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true

                TextArea {
                    id: errorDetails
                    text: errorDialog.details
                    readOnly: true
                    selectByMouse: true
                    wrapMode: TextEdit.Wrap
                    background: Rectangle {
                        radius: window.controlRadius
                        color: "#F7FAF8"
                        border.width: errorDetails.activeFocus ? 2 : 1
                        border.color: errorDetails.activeFocus
                            ? window.primaryColor
                            : window.borderColor
                    }
                }
            }

            Button {
                Layout.alignment: Qt.AlignRight
                text: "Cerrar"
                onClicked: errorDialog.close()
                Material.foreground: window.primaryColor
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
