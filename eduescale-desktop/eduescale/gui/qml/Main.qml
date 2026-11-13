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
    property string pendingAction: ""
    property string pendingOutputUrl: ""
    property string pendingSearchesJson: ""
    property string pendingEduEscalePassword: ""
    property bool pendingEduEscaleProtected: false
    property bool pendingEduEscaleCompressed: false
    property string mapLevel: "department"
    property var selectedDepartmentCodes: []
    property var selectedProvinceCodes: []
    property var selectedDistrictCodes: []

    Universal.theme: controller.themeMode === "system"
        ? Universal.System
        : darkTheme ? Universal.Dark : Universal.Light
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
                name: "Búsqueda " + (filterSetsModel.count + 1),
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
                name: filterSet.name,
                region_code: filterSet.regionCode,
                province_code: filterSet.provinceCode,
                district_code: filterSet.districtCode,
                department: filterSet.regionLabel || null,
                province: filterSet.provinceLabel || null,
                district: filterSet.districtCode ? filterSet.districtLabel : null,
                management: filterSet.management,
                level: filterSet.level
            })
        }
        return JSON.stringify(filterSets)
    }

    function currentSearchSummary() {
        var summary = []
        for (var index = 0; index < filterSetsModel.count; index++) {
            var search = filterSetsModel.get(index)
            var location = []
            if (search.regionLabel)
                location.push(search.regionLabel)
            if (search.provinceLabel)
                location.push(search.provinceLabel)
            if (search.districtCode)
                location.push(search.districtLabel)
            if (!location.length)
                location.push("Todo el Perú")
            summary.push(search.name + ": " + location.join(" / ")
                + " · Gestión: " + search.management.replace(/,/g, ", ")
                + " · Nivel: " + search.level.replace(/,/g, ", "))
        }
        return summary.join("\n")
    }

    function fileNameFromUrl(url) {
        var path = url.split(/[?#]/)[0]
        var fileName = path.substring(path.lastIndexOf("/") + 1)
        return decodeURIComponent(fileName)
    }

    function openPendingConfirmation() {
        var operation = pendingAction
        exportConfirmationDialog.title = operation === "scrape"
            ? "Confirmar búsqueda"
            : operation === "excel"
            ? "Confirmar exportación Excel"
            : operation === "pdf"
            ? "Confirmar exportación PDF"
            : "Confirmar exportación .eduescale"
        exportConfirmationDialog.description = operation === "scrape"
            ? controller.dataSource === "offline"
                ? "Se buscarán los datos de las búsquedas seleccionadas en la base offline."
                : "Se descargarán los datos de ESCALE para las búsquedas seleccionadas."
            : operation === "eduescale" && pendingEduEscaleProtected
            ? "Se guardará un archivo .eduescale protegido con contraseña."
            : operation === "eduescale" && pendingEduEscaleCompressed
            ? "Se guardará un archivo .eduescale comprimido."
            : "Se generará un archivo con los datos exportados."
        exportConfirmationDialog.continueLabel = operation === "scrape"
            ? "Iniciar búsqueda" : "Generar archivo"
        exportConfirmationDialog.destinationName = fileNameFromUrl(pendingOutputUrl)
        exportConfirmationDialog.searchSummary = currentSearchSummary()
        exportConfirmationDialog.open()
    }

    function requestConfirmation(operation, outputUrl) {
        pendingAction = operation
        pendingOutputUrl = outputUrl
        pendingSearchesJson = selectedFilterSetsJson()
        openPendingConfirmation()
    }

    ColumnLayout {
        id: dashboardShell
        objectName: "dashboardShell"
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: implicitHeight
        spacing: 0

        DashboardHeader {
            id: dashboardHeader
            Layout.fillWidth: true
            Layout.margins: 10
            controller: window.controller
        }

        ScrollView {
        id: filterScrollView
        objectName: "filterScrollView"
        Layout.fillWidth: true
        Layout.fillHeight: false
        Layout.leftMargin: 12
        Layout.rightMargin: 12
        Layout.bottomMargin: 8
        Layout.preferredHeight: Math.min(
            pageContent.implicitHeight + padding * 2,
            Math.max(
                160,
                window.contentItem.height - dashboardHeader.height - 20
                    - searchActionBar.implicitHeight - 8
            )
        )
        padding: 8
        clip: true
        contentWidth: availableWidth
        contentHeight: pageContent.implicitHeight
        ScrollBar.vertical: StyledScrollBar {
            objectName: "pageVerticalScrollBar"
            anchors.right: parent.right
            policy: ScrollBar.AsNeeded
            visible: filterScrollView.contentHeight
                > filterScrollView.availableHeight + 1
            interactive: true
            width: 12
        }

        ColumnLayout {
            id: pageContent
            width: filterScrollView.availableWidth
            spacing: 10

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

            SavedSearchesPanel {
                id: searchesCard
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignTop
                Layout.preferredWidth: searchWorkspace.columns === 2
                    ? filterScrollView.availableWidth * 0.34
                    : filterScrollView.availableWidth
                controller: window.controller
                searchModel: filterSetsModel
                windowHeight: window.height
                scrapeRunning: window.controller
                    ? window.controller.scrapeRunning : false
                filterNotice: window.filterNotice
            }
        }

        }
    }

    SearchActionBar {
        id: searchActionBar
        Layout.fillWidth: true
        controller: window.controller
        windowWidth: window.width
        searchCount: filterSetsModel.count
        onScrapeRequested: window.requestConfirmation(
            "scrape", window.controller.defaultOutputUrl
        )
        onExcelExportRequested: excelSaveDialog.open()
        onPdfExportRequested: pdfSaveDialog.open()
        onEduEscaleExportRequested: eduEscaleSaveDialog.open()
        onHistoryRequested: exportHistoryDialog.open()
        onOfflineDatabaseSelectionRequested: offlineDatabaseOpenDialog.open()
        onOfflineDatabaseUpdateRequested: {
            if (controller.offlineDatabaseAvailable)
                controller.updateOfflineDatabase(controller.offlineDatabaseUrl)
            else
                offlineDatabaseSaveDialog.open()
        }
    }
    }

    FileDialog {
        id: offlineDatabaseOpenDialog
        objectName: "offlineDatabaseOpenDialog"
        title: "Seleccionar base offline de EduEscale"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Base de datos EduEscale (*.sqlite3)"]
        onAccepted: controller.selectOfflineDatabase(selectedFile.toString())
    }

    FileDialog {
        id: offlineDatabaseSaveDialog
        objectName: "offlineDatabaseSaveDialog"
        title: "Descargar base de datos de ESCALE"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Base de datos EduEscale (*.sqlite3)"]
        defaultSuffix: "sqlite3"
        currentFile: controller.defaultOfflineDatabaseUrl
        onAccepted: controller.updateOfflineDatabase(selectedFile.toString())
    }

    FileDialog {
        id: excelSaveDialog
        objectName: "excelSaveDialog"
        title: "Exportar Excel"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Libro de Excel (*.xlsx)"]
        defaultSuffix: "xlsx"
        currentFile: controller.defaultOutputUrl
        onAccepted: window.requestConfirmation("excel", selectedFile.toString())
    }

    FileDialog {
        id: pdfSaveDialog
        objectName: "pdfSaveDialog"
        title: "Exportar PDF"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Documento PDF (*.pdf)"]
        defaultSuffix: "pdf"
        currentFile: controller.defaultPdfOutputUrl
        onAccepted: window.requestConfirmation("pdf", selectedFile.toString())
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
                            window.pendingAction = "eduescale"
                            window.pendingOutputUrl = eduEscaleOptionsDialog.outputUrl
                            window.pendingSearchesJson = window.selectedFilterSetsJson()
                            window.pendingEduEscaleProtected =
                                protectEduEscaleCheckBox.checked
                            window.pendingEduEscaleCompressed =
                                !protectEduEscaleCheckBox.checked
                                && compressEduEscaleCheckBox.checked
                            window.pendingEduEscalePassword =
                                protectEduEscaleCheckBox.checked
                                ? eduEscalePasswordField.text : ""
                            eduEscaleOptionsDialog.close()
                            Qt.callLater(window.openPendingConfirmation)
                        }
                }
            }
        }
    }

    ExportConfirmationDialog {
        id: exportConfirmationDialog
        objectName: "exportConfirmationDialog"
        onConfirmed: {
            if (window.pendingAction === "scrape") {
                controller.startScrape(
                        window.pendingOutputUrl,
                        window.pendingSearchesJson
                )
            } else if (window.pendingAction === "excel") {
                controller.exportExcelFile(window.pendingOutputUrl)
            } else if (window.pendingAction === "pdf") {
                controller.exportPdfFile(window.pendingOutputUrl)
            } else if (window.pendingAction === "eduescale") {
                controller.exportEduEscaleFile(
                        window.pendingOutputUrl,
                        window.pendingEduEscalePassword,
                        window.pendingEduEscaleCompressed
                )
            }
        }
        onClosed: {
            window.pendingAction = ""
            window.pendingOutputUrl = ""
            window.pendingSearchesJson = ""
            window.pendingEduEscalePassword = ""
            window.pendingEduEscaleProtected = false
            window.pendingEduEscaleCompressed = false
        }
    }

    ExportHistoryDialog {
        id: exportHistoryDialog
        controller: window.controller
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
