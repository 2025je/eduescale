import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Universal
import QtQuick.Dialogs
import QtQuick.Layouts
import "styles" as EduStyle

ApplicationWindow {
    id: window
    required property var controller
    width: controller.windowWidth
    height: controller.windowHeight
    minimumWidth: 560
    minimumHeight: 600
    visible: true
    title: "EduEscale | Buscador de colegios"
    color: canvasColor

    readonly property bool darkTheme: controller.darkTheme
    readonly property color canvasColor: EduStyle.Style.canvasColor
    readonly property color surfaceColor: EduStyle.Style.surfaceColor
    readonly property color subtleSurfaceColor: EduStyle.Style.subtleSurfaceColor
    readonly property color disabledSurfaceColor: EduStyle.Style.disabledSurfaceColor
    readonly property color disabledBorderColor: EduStyle.Style.disabledBorderColor
    readonly property color disabledTextColor: EduStyle.Style.disabledTextColor
    readonly property color primaryColor: EduStyle.Style.primaryColor
    readonly property color primaryHoverColor: EduStyle.Style.primaryHoverColor
    readonly property color primaryActiveColor: EduStyle.Style.primaryActiveColor
    readonly property color primarySubtleColor: EduStyle.Style.primarySubtleColor
    readonly property color primarySubtleHoverColor:
        EduStyle.Style.primarySubtleHoverColor
    readonly property color primaryBorderColor: EduStyle.Style.primaryBorderColor
    readonly property color primaryEmphasisColor: EduStyle.Style.primaryEmphasisColor
    readonly property color borderColor: EduStyle.Style.borderColor
    readonly property color textColor: EduStyle.Style.textColor
    readonly property color mutedTextColor: EduStyle.Style.mutedTextColor
    readonly property int appTitleFontSize: EduStyle.Style.appTitleFontSize
    readonly property int sectionTitleFontSize: EduStyle.Style.sectionTitleFontSize
    readonly property int bodyFontSize: EduStyle.Style.bodyFontSize
    readonly property int smallFontSize: EduStyle.Style.smallFontSize
    readonly property int captionFontSize: EduStyle.Style.captionFontSize
    readonly property int cardRadius: EduStyle.Style.cardRadius
    readonly property int controlRadius: EduStyle.Style.controlRadius
    readonly property int compactButtonHeight: EduStyle.Style.compactButtonHeight
    readonly property int actionButtonHeight: EduStyle.Style.actionButtonHeight
    property string filterNotice: ""
    property string mapLevel: "department"
    property var selectedDepartmentCodes: []
    property var selectedProvinceCodes: []
    property var selectedDistrictCodes: []

    Universal.theme: darkTheme ? Universal.Dark : Universal.Light
    Universal.accent: primaryColor
    Universal.background: surfaceColor
    Universal.foreground: textColor

    Binding {
        target: EduStyle.Style
        property: "darkTheme"
        value: window.controller.darkTheme
    }

    onClosing: {
        controller.saveWindowSize(width, height)
        controller.shutdown()
    }

    ListModel {
        id: filterSetsModel
        objectName: "filterSetsModel"
    }

    function effectiveDistrictCodes() {
        if (locationTabs.currentIndex === 1) {
            return controller.effectiveDistrictCodes(
                departmentCombo.currentValue ? [String(departmentCombo.currentValue)] : [],
                provinceCombo.currentValue ? [String(provinceCombo.currentValue)] : [],
                districtCombo.currentValue ? [String(districtCombo.currentValue)] : []
            )
        }
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

    function clearLocationSelection() {
        clearMapSelection()
        departmentCombo.currentIndex = 0
        provinceCombo.currentIndex = 0
        districtCombo.currentIndex = 0
        controller.selectRegion("")
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
        ScrollBar.vertical: StyledScrollBar {
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
                    color: window.surfaceColor
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
                                font.pixelSize: window.appTitleFontSize
                                font.weight: Font.DemiBold
                            }

                            Label {
                                text: "Instituciones educativas del Perú · Busca, filtra y exporta"
                                color: window.mutedTextColor
                                font.pixelSize: window.bodyFontSize
                            }
                        }

                        StyledButton {
                            objectName: "themeToggleButton"
                            Layout.alignment: Qt.AlignVCenter
                            text: window.darkTheme ? "Tema claro" : "Tema oscuro"
                            variant: "secondary"
                            onClicked: window.controller.setDarkTheme(!window.darkTheme)
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
                    color: window.surfaceColor
                    border.width: 1
                    border.color: window.borderColor
                }

                ColumnLayout {
                    anchors.fill: parent
                    spacing: 8

                    Label {
                        text: "Ubicación y criterios"
                        color: window.textColor
                        font.pixelSize: window.sectionTitleFontSize
                        font.weight: Font.DemiBold
                    }

                    GridLayout {
                        id: filterGrid
                        objectName: "filterGrid"
                        Layout.fillWidth: true
                        columns: window.width < 720 ? 1 : 2
                        columnSpacing: 12
                        rowSpacing: 6

                        ColumnLayout {
                            id: locationSelector
                            objectName: "locationSelector"
                            Layout.column: 0
                            Layout.row: 0
                            Layout.fillWidth: true
                            spacing: 8

                            TabBar {
                                id: locationTabs
                                objectName: "locationTabs"
                                Layout.fillWidth: true
                                enabled: !controller.scrapeRunning
                                padding: 4
                                spacing: 2
                                background: Rectangle {
                                    radius: window.controlRadius
                                    color: EduStyle.Style.tabTrackColor
                                    border.width: 1
                                    border.color: window.borderColor
                                }

                                StyledTabButton {
                                    id: mapLocationTabButton
                                    objectName: "mapLocationTab"
                                    labelObjectName: "mapLocationTabLabel"
                                    indicatorObjectName: "mapLocationTabIndicator"
                                    text: "Mapa"
                                    width: (locationTabs.availableWidth
                                        - locationTabs.spacing) / 2
                                }
                                StyledTabButton {
                                    id: comboLocationTabButton
                                    objectName: "comboLocationTab"
                                    labelObjectName: "comboLocationTabLabel"
                                    indicatorObjectName: "comboLocationTabIndicator"
                                    text: "Por ubicación"
                                    width: (locationTabs.availableWidth
                                        - locationTabs.spacing) / 2
                                }
                            }

                            StackLayout {
                                id: locationPages
                                objectName: "locationPages"
                                Layout.fillWidth: true
                                Layout.minimumHeight: 300
                                Layout.preferredHeight: 340
                                currentIndex: locationTabs.currentIndex

                                ColumnLayout {
                                    id: mapSelectionPage
                                    objectName: "mapSelectionPage"
                                    spacing: 8

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
                                        StyledButton {
                                            objectName: "mapBackButton"
                                            variant: "secondary"
                                            text: "Volver"
                                            implicitHeight: window.compactButtonHeight
                                            font.pixelSize: window.bodyFontSize
                                            padding: 6
                                            visible: window.mapLevel !== "department"
                                            enabled: !controller.scrapeRunning
                                            onClicked: window.goBackMapLevel()
                                        }
                                        StyledButton {
                                            objectName: "mapAdvanceButton"
                                            labelObjectName: "mapAdvanceButtonLabel"
                                            variant: "secondary"
                                            text: window.mapLevel === "department"
                                                ? "Ver provincias" : "Ver distritos"
                                            implicitHeight: window.compactButtonHeight
                                            font.pixelSize: window.bodyFontSize
                                            padding: 6
                                            visible: window.mapLevel !== "district"
                                            enabled: !controller.scrapeRunning
                                                && (window.mapLevel === "department"
                                                    ? window.selectedDepartmentCodes.length > 0
                                                    : window.selectedProvinceCodes.length > 0)
                                            onClicked: window.advanceMapLevel()
                                        }
                                    }

                                    DistrictMap {
                                        id: districtMap
                                        objectName: "districtMap"
                                        Layout.fillWidth: true
                                        Layout.fillHeight: true
                                        Layout.minimumHeight: 260
                                        Layout.preferredHeight: 300
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
                                }

                                ColumnLayout {
                                    id: comboSelectionPage
                                    objectName: "comboSelectionPage"
                                    spacing: 12

                                    StyledComboBox {
                                        id: departmentCombo
                                        objectName: "departmentCombo"
                                        Layout.fillWidth: true
                                        model: controller.regions
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: controller.regionsEnabled
                                            && !controller.scrapeRunning
                                        onActivated: function(index) {
                                            controller.selectRegion(currentValue)
                                            provinceCombo.currentIndex = 0
                                            districtCombo.currentIndex = 0
                                            window.filterNotice = ""
                                        }
                                    }
                                    StyledComboBox {
                                        id: provinceCombo
                                        objectName: "provinceCombo"
                                        Layout.fillWidth: true
                                        model: controller.provinces
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: controller.provincesEnabled
                                            && !controller.scrapeRunning
                                        onActivated: function(index) {
                                            controller.selectProvince(currentValue)
                                            districtCombo.currentIndex = 0
                                            window.filterNotice = ""
                                        }
                                    }
                                    StyledComboBox {
                                        id: districtCombo
                                        objectName: "districtCombo"
                                        Layout.fillWidth: true
                                        model: controller.districts
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: controller.districtsEnabled
                                            && !controller.scrapeRunning
                                        onActivated: window.filterNotice = ""
                                    }
                                    Item {
                                        Layout.fillHeight: true
                                    }
                                }
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
                                objectName: "selectedDistrictCountLabel"
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
                            StyledButton {
                                id: mapClearButton
                                objectName: "mapClearButton"
                                labelObjectName: "mapClearButtonLabel"
                                variant: "secondary"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                implicitHeight: window.compactButtonHeight
                                font.pixelSize: window.bodyFontSize
                                padding: 6
                                text: "Limpiar selección"
                                enabled: (window.mapLevel !== "department"
                                    || window.selectedDepartmentCodes.length > 0
                                    || window.selectedProvinceCodes.length > 0
                                    || window.selectedDistrictCodes.length > 0
                                    || departmentCombo.currentValue
                                    || provinceCombo.currentValue
                                    || districtCombo.currentValue)
                                    && !controller.scrapeRunning
                                onClicked: window.clearLocationSelection()
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
                                StyledCheckBox {
                                    id: publicManagement
                                    objectName: "publicManagement"
                                    text: "Pública"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
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
                                StyledCheckBox {
                                    id: primaryLevel
                                    objectName: "primaryLevel"
                                    text: "Primaria"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
                                    id: secondaryLevel
                                    objectName: "secondaryLevel"
                                    text: "Secundaria"
                                    enabled: !controller.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
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
                                font.pixelSize: window.captionFontSize
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
                                font.pixelSize: window.smallFontSize
                            }

                            StyledButton {
                                id: addFilterSetButton
                                objectName: "addFilterSetButton"
                                labelObjectName: "addFilterSetButtonLabel"
                                variant: "primary"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                implicitHeight: window.actionButtonHeight
                                font.pixelSize: window.bodyFontSize
                                padding: 8
                                text: "Agregar búsqueda"
                                enabled: !controller.scrapeRunning
                                    && window.selectedManagement().length > 0
                                    && window.selectedLevels().length > 0
                                onClicked: window.addFilterSet()
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
                    color: window.surfaceColor
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
                            font.pixelSize: window.sectionTitleFontSize
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
                                font.pixelSize: window.bodyFontSize
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
                                    color: window.subtleSurfaceColor
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
                                            font.pixelSize: window.bodyFontSize
                                        }
                                    }

                                    StyledButton {
                                        text: "Quitar"
                                        variant: "quiet"
                                        implicitHeight: 32
                                        font.pixelSize: window.smallFontSize
                                        padding: 4
                                        enabled: !controller.scrapeRunning
                                        onClicked: filterSetsModel.remove(index)
                                    }
                                }
                            }

                            ScrollBar.vertical: StyledScrollBar {
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
                            color: window.subtleSurfaceColor
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
        color: window.canvasColor
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
        color: window.surfaceColor
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
                font.pixelSize: window.smallFontSize
            }

            StyledProgressBar {
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
                font.pixelSize: window.smallFontSize
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

                StyledButton {
                    id: scrapeButton
                    objectName: "scrapeButton"
                    labelObjectName: "scrapeButtonLabel"
                    variant: "primary"
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight + 2
                    font.pixelSize: window.bodyFontSize
                    padding: 8
                    visible: !controller.scrapeRunning
                    text: controller.cancelRequested
                        ? "Cancelando extracción..."
                        : controller.scrapeRunning
                        ? "Consultando datos..."
                        : controller.exportRunning
                        ? "Generando archivo..."
                        : "Ejecutar búsqueda"
                    enabled: !controller.scrapeRunning
                        && !controller.exportRunning
                        && filterSetsModel.count > 0
                    onClicked: controller.startScrape(
                        controller.defaultOutputUrl,
                        window.selectedFilterSetsJson()
                    )
                }

                StyledButton {
                    id: cancelScrapeButton
                    objectName: "cancelScrapeButton"
                    labelObjectName: "cancelScrapeButtonLabel"
                    variant: "danger"
                    Layout.columnSpan: actionButtons.columns
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: window.bodyFontSize
                    padding: 8
                    visible: controller.scrapeRunning
                    enabled: !controller.cancelRequested
                    text: controller.cancelRequested ? "Cancelando..." : "Cancelar"
                    onClicked: controller.cancelScrape()
                }

                StyledButton {
                    objectName: "downloadExcelButton"
                    labelObjectName: "downloadExcelButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: window.bodyFontSize
                    padding: 8
                    text: "Exportar Excel"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: excelSaveDialog.open()
                }

                StyledButton {
                    objectName: "downloadPdfButton"
                    labelObjectName: "downloadPdfButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: window.bodyFontSize
                    padding: 8
                    text: "Exportar PDF"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: pdfSaveDialog.open()
                }

                StyledButton {
                    objectName: "downloadEduEscaleButton"
                    labelObjectName: "downloadEduEscaleButtonLabel"
                    variant: "secondary"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.minimumHeight: window.actionButtonHeight
                    implicitHeight: window.actionButtonHeight
                    font.pixelSize: window.bodyFontSize
                    padding: 8
                    text: "Exportar .eduescale"
                    enabled: controller.exportsAvailable && !controller.exportRunning
                    onClicked: eduEscaleSaveDialog.open()
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
        id: eduEscaleSaveDialog
        objectName: "eduEscaleSaveDialog"
        title: "Exportar .eduescale"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Archivo EduEscale (*.eduescale)"]
        defaultSuffix: "eduescale"
        currentFile: controller.defaultEduEscaleOutputUrl
        onAccepted: {
            eduEscaleOptionsDialog.outputUrl = selectedFile.toString()
            eduEscaleOptionsDialog.open()
        }
    }

    StyledDialog {
        id: eduEscaleOptionsDialog
        objectName: "eduEscaleOptionsDialog"
        property string outputUrl: ""
        property string validationMessage: ""
        title: "Opciones del archivo .eduescale"
        modal: true
        anchors.centerIn: parent
        width: Math.min(window.width - 40, 440)
        standardButtons: Dialog.NoButton
        onClosed: {
            eduEscalePasswordField.clear()
            eduEscalePasswordConfirmationField.clear()
            validationMessage = ""
        }

        contentItem: ColumnLayout {
            spacing: 10

            StyledCheckBox {
                id: compressEduEscaleCheckBox
                objectName: "compressEduEscaleCheckBox"
                Layout.fillWidth: true
                text: "Comprimir el archivo (no cifra su contenido)"
                enabled: !protectEduEscaleCheckBox.checked
            }

            StyledCheckBox {
                id: protectEduEscaleCheckBox
                objectName: "protectEduEscaleCheckBox"
                Layout.fillWidth: true
                text: "Proteger con contraseña"
                onToggled: eduEscaleOptionsDialog.validationMessage = ""
            }

            StyledTextField {
                id: eduEscalePasswordField
                objectName: "eduEscalePasswordField"
                Layout.fillWidth: true
                visible: protectEduEscaleCheckBox.checked
                enabled: visible
                echoMode: TextInput.Password
                placeholderText: "Contraseña (mínimo 8 caracteres)"
            }

            StyledTextField {
                id: eduEscalePasswordConfirmationField
                objectName: "eduEscalePasswordConfirmationField"
                Layout.fillWidth: true
                visible: protectEduEscaleCheckBox.checked
                enabled: visible
                echoMode: TextInput.Password
                placeholderText: "Confirmar contraseña"
            }

            Label {
                Layout.fillWidth: true
                visible: eduEscaleOptionsDialog.validationMessage.length > 0
                text: eduEscaleOptionsDialog.validationMessage
                color: EduStyle.Style.dangerColor
                wrapMode: Text.Wrap
            }

            RowLayout {
                Layout.alignment: Qt.AlignRight
                spacing: 8

                StyledButton {
                    text: "Cancelar"
                    variant: "secondary"
                    onClicked: eduEscaleOptionsDialog.close()
                }

                StyledButton {
                    text: "Exportar"
                    variant: "primary"
                    onClicked: {
                        if (protectEduEscaleCheckBox.checked) {
                            if (eduEscalePasswordField.text.length < 8) {
                                eduEscaleOptionsDialog.validationMessage =
                                    "La contraseña debe tener al menos 8 caracteres."
                                return
                            }
                            if (eduEscalePasswordField.text !==
                                    eduEscalePasswordConfirmationField.text) {
                                eduEscaleOptionsDialog.validationMessage =
                                    "Las contraseñas no coinciden."
                                return
                            }
                        }
                        controller.exportEduEscaleFile(
                            eduEscaleOptionsDialog.outputUrl,
                            protectEduEscaleCheckBox.checked
                                ? eduEscalePasswordField.text
                                : "",
                            compressEduEscaleCheckBox.checked
                        )
                        eduEscalePasswordField.clear()
                        eduEscalePasswordConfirmationField.clear()
                        eduEscaleOptionsDialog.close()
                    }
                }
            }
        }
    }

    StyledDialog {
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

                StyledTextArea {
                    id: errorDetails
                    text: errorDialog.details
                    readOnly: true
                    selectByMouse: true
                    wrapMode: TextEdit.Wrap
                }
            }

            StyledButton {
                Layout.alignment: Qt.AlignRight
                text: "Cerrar"
                variant: "secondary"
                implicitHeight: window.compactButtonHeight
                font.pixelSize: window.bodyFontSize
                padding: 6
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
