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
    readonly property int compactButtonHeight: 36
    readonly property int actionButtonHeight: 40
    property string filterNotice: ""
    property string mapLevel: "department"
    property var selectedDepartmentCodes: []
    property var selectedProvinceCodes: []
    property var selectedDistrictCodes: []

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

    function effectiveDistrictCodes() {
        return controller.effectiveDistrictCodes(
            selectedDepartmentCodes,
            selectedProvinceCodes,
            selectedDistrictCodes
        )
    }

    function advanceMapLevel() {
        if (mapLevel === "department" && selectedDepartmentCodes.length) {
            mapLevel = "province"
            filterNotice = ""
        } else if (mapLevel === "province" && selectedProvinceCodes.length) {
            mapLevel = "district"
            filterNotice = ""
        }
    }

    function goBackMapLevel() {
        if (mapLevel === "district")
            mapLevel = "province"
        else if (mapLevel === "province")
            mapLevel = "department"
        filterNotice = ""
    }

    function updateMapSelection(level, codes) {
        if (level !== mapLevel) {
            filterNotice = "La selección del mapa no coincide con el nivel actual."
            return
        }
        if (level === "department") {
            selectedDepartmentCodes = codes.slice()
            selectedProvinceCodes = []
            selectedDistrictCodes = []
        } else if (level === "province") {
            selectedProvinceCodes = codes.slice()
            selectedDistrictCodes = []
        } else {
            selectedDistrictCodes = codes.slice()
        }
        filterNotice = ""
    }

    function clearMapSelection() {
        mapLevel = "department"
        selectedDepartmentCodes = []
        selectedProvinceCodes = []
        selectedDistrictCodes = []
        filterNotice = ""
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
        if (primaryLevel.checked)
            selected.push("primaria")
        if (secondaryLevel.checked)
            selected.push("secundaria")
        if (cetproLevel.checked)
            selected.push("cetpro")
        return selected.join(",")
    }

    function filterSetKey(filterSet) {
        return JSON.stringify([
            filterSet.regionCode || "",
            filterSet.provinceCode || "",
            filterSet.districtCode || "",
            (filterSet.management || "").split(",").sort().join(","),
            (filterSet.level || "").split(",").sort().join(",")
        ])
    }

    function addFilterSet() {
        var selectedCodes = effectiveDistrictCodes()
        if (!selectedCodes.length)
            selectedCodes.push("")

        var added = 0
        var duplicates = 0
        for (var codeIndex = 0; codeIndex < selectedCodes.length; codeIndex++) {
            var districtCode = selectedCodes[codeIndex]
            var properties = districtCode
                ? controller.mapDistrictProperties(districtCode) : null
            if (districtCode && !properties) {
                filterNotice = "No se encontró la información de uno de los distritos seleccionados."
                return
            }
            var filterSet = {
                regionCode: "",
                provinceCode: "",
                districtCode: districtCode,
                regionLabel: properties ? properties.department : "",
                provinceLabel: properties ? properties.province : "",
                districtLabel: properties ? properties.district : "Todos los distritos",
                management: selectedManagement(),
                level: selectedLevels()
            }
            var key = filterSetKey(filterSet)
            var alreadyAdded = false
            for (var index = 0; index < filterSetsModel.count; index++) {
                if (filterSetKey(filterSetsModel.get(index)) === key) {
                    alreadyAdded = true
                    break
                }
            }
            if (alreadyAdded) {
                duplicates++
                continue
            }
            filterSetsModel.append(filterSet)
            added++
        }

        if (!added) {
            filterNotice = "Esta combinación ya está en Mis búsquedas."
        } else if (duplicates) {
            filterNotice = "Se agregaron " + added + " distritos; "
                + duplicates + " combinaciones repetidas se omitieron."
        } else {
            filterNotice = ""
        }
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
        anchors.bottom: parent.bottom
        padding: 12
        clip: true
        contentWidth: availableWidth
        contentHeight: pageContent.implicitHeight
        ScrollBar.vertical: ScrollBar {
            objectName: "pageVerticalScrollBar"
            anchors.right: parent.right
            policy: ScrollBar.AlwaysOn
            interactive: true
            width: 12
        }

        ColumnLayout {
            id: pageContent
            width: filterScrollView.availableWidth
            spacing: 10

            Frame {
                Layout.fillWidth: true
                padding: 10
                background: Rectangle {
                    radius: window.cardRadius
                    color: "#FFFFFF"
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 0

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 10

                        Image {
                            objectName: "brandLogo"
                            Layout.preferredWidth: 42
                            Layout.preferredHeight: 42
                            source: Qt.resolvedUrl("../assets/edutrack-logo.png")
                            fillMode: Image.PreserveAspectFit
                        }

                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 2

                            Label {
                                objectName: "brandTitle"
                                text: "EduEscale"
                                color: window.textColor
                                font.pixelSize: 22
                                font.weight: Font.DemiBold
                            }

                            Label {
                                text: "Instituciones educativas del Perú · Busca, filtra y exporta"
                                color: window.mutedTextColor
                                font.pixelSize: 12
                            }
                        }
                    }
                }
            }

            GridLayout {
                id: searchWorkspace
                objectName: "searchWorkspace"
                Layout.fillWidth: true
                columns: window.width < 1100 ? 1 : 2
                columnSpacing: 12
                rowSpacing: 12

            Frame {
                id: criteriaCard
                objectName: "criteriaCard"
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignTop
                Layout.preferredWidth: searchWorkspace.columns === 2
                    ? filterScrollView.availableWidth * 0.66
                    : filterScrollView.availableWidth
                padding: 14
                background: Rectangle {
                    radius: window.cardRadius
                    color: "white"
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 8

                    Label {
                        text: "Ubicación y criterios"
                        color: window.textColor
                        font.pixelSize: 18
                        font.weight: Font.DemiBold
                    }

                    RowLayout {
                        objectName: "mapNavigation"
                        Layout.fillWidth: true
                        Label {
                            id: mapLevelLabel
                            objectName: "mapLevelLabel"
                            Layout.fillWidth: true
                            color: window.textColor
                            font.weight: Font.DemiBold
                            text: window.mapLevel === "department"
                                ? "Paso 1 de 3 · Departamentos"
                                : window.mapLevel === "province"
                                ? "Paso 2 de 3 · Provincias"
                                : "Paso 3 de 3 · Distritos"
                        }
                        Button {
                            objectName: "mapBackButton"
                            text: "Volver"
                            implicitHeight: window.compactButtonHeight
                            font.pixelSize: 12
                            padding: 6
                            visible: window.mapLevel !== "department"
                            enabled: !controller.scrapeRunning
                            onClicked: window.goBackMapLevel()
                        }
                        Button {
                            objectName: "mapAdvanceButton"
                            text: window.mapLevel === "department"
                                ? "Ver provincias" : "Ver distritos"
                            implicitHeight: window.compactButtonHeight
                            font.pixelSize: 12
                            padding: 6
                            visible: window.mapLevel !== "district"
                            enabled: !controller.scrapeRunning
                                && (window.mapLevel === "department"
                                    ? window.selectedDepartmentCodes.length > 0
                                    : window.selectedProvinceCodes.length > 0)
                            onClicked: window.advanceMapLevel()
                        }
                    }

                    GridLayout {
                        id: filterGrid
                        objectName: "filterGrid"
                        Layout.fillWidth: true
                        columns: window.width < 720 ? 1 : 2
                        columnSpacing: 12
                        rowSpacing: 6

                        DistrictMap {
                            id: districtMap
                            Layout.column: 0
                            Layout.row: 0
                            Layout.fillWidth: true
                            Layout.fillHeight: false
                            Layout.minimumHeight: 300
                            Layout.preferredHeight: 340
                            Layout.alignment: Qt.AlignTop
                            enabled: !controller.scrapeRunning
                            mapLevel: window.mapLevel
                            parentCodes: window.mapLevel === "province"
                                ? window.selectedDepartmentCodes
                                : window.mapLevel === "district"
                                ? window.selectedProvinceCodes : []
                            selectionCodes: window.mapLevel === "department"
                                ? window.selectedDepartmentCodes
                                : window.mapLevel === "province"
                                ? window.selectedProvinceCodes
                                : window.selectedDistrictCodes
                            departmentMetadata: controller.mapDepartmentMetadata
                            provinceMetadata: controller.mapProvinceMetadata
                            districtCodes: controller.districtCodes
                            onSelectedCodesChanged: window.filterNotice = ""
                            onMapSelectionChanged: function(level, codes) {
                                window.updateMapSelection(level, codes)
                            }
                        }
                        ColumnLayout {
                            objectName: "selectionControls"
                            Layout.column: filterGrid.columns === 2 ? 1 : 0
                            Layout.row: filterGrid.columns === 2 ? 0 : 1
                            Layout.fillWidth: filterGrid.columns === 1
                            Layout.fillHeight: true
                            Layout.preferredWidth: filterGrid.columns === 2
                                ? 260 : filterGrid.width
                            Layout.minimumWidth: filterGrid.columns === 2 ? 260 : 0
                            Layout.maximumWidth: filterGrid.columns === 2
                                ? 260 : 16777215
                            Layout.alignment: Qt.AlignTop | Qt.AlignLeft
                            spacing: 2

                            Label {
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                color: window.mutedTextColor
                                wrapMode: Text.WordWrap
                                text: {
                                    var count = window.effectiveDistrictCodes().length
                                    return count
                                        ? count + " distritos"
                                        : "Todo el Perú"
                                }
                            }
                            Button {
                                objectName: "mapClearButton"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                implicitHeight: window.compactButtonHeight
                                font.pixelSize: 12
                                padding: 6
                                text: "Limpiar selección"
                                enabled: (window.mapLevel !== "department"
                                    || window.selectedDepartmentCodes.length > 0
                                    || window.selectedProvinceCodes.length > 0
                                    || window.selectedDistrictCodes.length > 0)
                                    && !controller.scrapeRunning
                                onClicked: window.clearMapSelection()
                            }

                            Label {
                                text: "Gestión"
                                color: window.mutedTextColor
                                font.weight: Font.Medium
                            }
                            Flow {
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                spacing: 4
                                CheckBox {
                                    id: publicManagement
                                    objectName: "publicManagement"
                                    text: "Pública"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                CheckBox {
                                    id: privateManagement
                                    objectName: "privateManagement"
                                    text: "Privada"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                            }

                            Label {
                                text: "Nivel educativo"
                                color: window.mutedTextColor
                                font.weight: Font.Medium
                            }
                            Flow {
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                spacing: 4
                                CheckBox {
                                    id: primaryLevel
                                    objectName: "primaryLevel"
                                    text: "Primaria"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                CheckBox {
                                    id: secondaryLevel
                                    objectName: "secondaryLevel"
                                    text: "Secundaria"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                CheckBox {
                                    id: cetproLevel
                                    objectName: "cetproLevel"
                                    text: "CETPRO"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                            }

                            Label {
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                text: "Límites INEI 2023; Alto Trujillo y Santa Rosa de Loreto son aproximados."
                                color: window.mutedTextColor
                                wrapMode: Text.WordWrap
                                font.pixelSize: 10
                            }

                            Label {
                                objectName: "searchHelpText"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                text: window.filterNotice.length
                                    ? window.filterNotice
                                    : "Selecciona ubicación, gestión y nivel educativo."
                                color: window.mutedTextColor
                                wrapMode: Text.WordWrap
                                font.pixelSize: 11
                            }

                            Button {
                                id: addFilterSetButton
                                objectName: "addFilterSetButton"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                implicitHeight: window.actionButtonHeight
                                font.pixelSize: 12
                                padding: 8
                                text: "Agregar búsqueda"
                                highlighted: true
                                Material.foreground: enabled
                                    ? "#FFFFFF" : window.primaryEmphasisColor
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
                        }
                    }
                }
            }

            Frame {
                id: searchesCard
                objectName: "searchesCard"
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignTop
                Layout.preferredWidth: searchWorkspace.columns === 2
                    ? filterScrollView.availableWidth * 0.34
                    : filterScrollView.availableWidth
                padding: 14
                background: Rectangle {
                    radius: window.cardRadius
                    color: "white"
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 14

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

                    Item {
                        id: searchListViewport
                        objectName: "searchListViewport"
                        Layout.fillWidth: true
                        Layout.preferredHeight: Math.min(
                            Math.max(
                                searchListView.contentHeight,
                                emptySearchState.implicitHeight
                            ),
                            Math.max(180, window.height * 0.45)
                        )
                        clip: true

                        ListView {
                            id: searchListView
                            objectName: "searchListView"
                            anchors.fill: parent
                            clip: true
                            model: filterSetsModel
                            spacing: 6

                            delegate: Frame {
                                width: searchListView.width - searchListVerticalScrollBar.width
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
                                        implicitHeight: 32
                                        font.pixelSize: 11
                                        padding: 4
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

                            ScrollBar.vertical: ScrollBar {
                                id: searchListVerticalScrollBar
                                objectName: "searchListVerticalScrollBar"
                                anchors.right: parent.right
                                policy: ScrollBar.AlwaysOn
                                interactive: true
                                width: 12
                            }
                        }

                        Rectangle {
                            id: emptySearchState
                            objectName: "emptySearchState"
                            anchors.fill: parent
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
                    }
                }
            }
            }

    Rectangle {
        id: sourceBar
        objectName: "sourceBar"
        Layout.fillWidth: true
        Layout.preferredHeight: 36
        height: 36
        color: "#F4F7F5"
        border.color: window.borderColor

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
            palette.link: window.primaryColor
        }
    }

    Rectangle {
        id: actionBar
        objectName: "actionBar"
        Layout.fillWidth: true
        Layout.preferredHeight: controller.progressVisible
            ? window.width < 900 ? 224 : 164
            : window.width < 900 ? 216 : 152
        color: "white"
        border.color: window.borderColor

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

            ProgressBar {
                id: progressIndicator
                objectName: "progressIndicator"
                Layout.fillWidth: true
                Layout.preferredHeight: 8
                visible: controller.progressVisible
                from: 0
                to: Math.max(controller.progressMaximum, 1)
                value: controller.progressValue
                indeterminate: controller.progressIndeterminate
            }

            Label {
                id: statusLabel
                objectName: "statusLabel"
                Layout.fillWidth: true
                text: controller.scrapeRunning || controller.exportRunning
                    ? controller.progressMessage
                    : controller.statusText
                color: window.mutedTextColor
                font.pixelSize: 11
                wrapMode: Text.WordWrap
                maximumLineCount: 2
                elide: Text.ElideRight
            }

            GridLayout {
                id: actionButtons
                objectName: "actionButtons"
                Layout.fillWidth: true
                Layout.fillHeight: false
                columns: window.width < 900 ? 2 : 3
                columnSpacing: 8
                rowSpacing: 8

                Button {
                    id: scrapeButton
                    objectName: "scrapeButton"
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight + 2
                    font.pixelSize: 12
                    padding: 8
                    visible: !controller.scrapeRunning
                    text: controller.cancelRequested
                        ? "Cancelando extracción..."
                        : controller.scrapeRunning
                        ? "Consultando datos..."
                        : controller.exportRunning
                        ? "Generando archivo..."
                        : "Ejecutar búsqueda"
                    highlighted: true
                    enabled: !controller.scrapeRunning
                        && !controller.exportRunning
                        && filterSetsModel.count > 0
                    Material.foreground: enabled ? "#FFFFFF" : window.primaryEmphasisColor
                    onClicked: controller.startScrape(
                        controller.defaultOutputUrl,
                        window.selectedFilterSetsJson()
                    )
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
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: 12
                    padding: 8
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

                Button {
                    objectName: "downloadExcelButton"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: 12
                    padding: 8
                    text: "Exportar Excel"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: excelSaveDialog.open()
                    Material.foreground: window.primaryColor
                }

                Button {
                    objectName: "downloadPdfButton"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: 12
                    padding: 8
                    text: "Exportar PDF"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: pdfSaveDialog.open()
                    Material.foreground: window.primaryColor
                }

                Button {
                    objectName: "downloadEduScaleButton"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: 12
                    padding: 8
                    text: "Exportar .eduescale"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: eduScaleSaveDialog.open()
                    Material.foreground: window.primaryColor
                }

            }
        }
    }

        }
    }

    FileDialog {
        id: excelSaveDialog
        objectName: "excelSaveDialog"
        title: "Exportar Excel"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Libro de Excel (*.xlsx)"]
        defaultSuffix: "xlsx"
        currentFile: controller.defaultOutputUrl
        onAccepted: controller.exportExcelFile(selectedFile.toString())
    }

    FileDialog {
        id: pdfSaveDialog
        objectName: "pdfSaveDialog"
        title: "Exportar PDF"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Documento PDF (*.pdf)"]
        defaultSuffix: "pdf"
        currentFile: controller.defaultPdfOutputUrl
        onAccepted: controller.exportPdfFile(selectedFile.toString())
    }

    FileDialog {
        id: eduScaleSaveDialog
        objectName: "eduScaleSaveDialog"
        title: "Exportar .eduescale"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Archivo EduEscale (*.eduescale)"]
        defaultSuffix: "eduescale"
        currentFile: controller.defaultEduScaleOutputUrl
        onAccepted: controller.exportEduScaleFile(selectedFile.toString())
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
                implicitHeight: window.compactButtonHeight
                font.pixelSize: 12
                padding: 6
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
