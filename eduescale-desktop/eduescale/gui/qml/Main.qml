import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Universal
import QtQuick.Dialogs
import QtQuick.Layouts
import EduEscale.Gui.Components 1.0
import EduEscale.Gui.Dialogs 1.0
import EduEscale.Gui.Maps 1.0
import EduEscale.Gui.Pages 1.0
import EduEscale.Gui.Panels 1.0
import EduEscale.Gui.Styles 1.0 as EduStyle

ApplicationWindow {
    id: window
    required property var viewModel
    width: viewModel.windowWidth
    height: viewModel.windowHeight
    minimumWidth: 560
    minimumHeight: 600
    visible: true
    title: "EduEscale | Buscador de colegios"
    color: canvasColor

    readonly property bool darkTheme: viewModel.darkTheme
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
    property var pendingSearches: []
    property string pendingEduEscalePassword: ""
    property bool pendingEduEscaleProtected: false
    property bool pendingEduEscaleCompressed: false
    property string mapLevel: "department"
    property var selectedDepartmentCodes: []
    property var selectedProvinceCodes: []
    property var selectedDistrictCodes: []
    property int workflowStep: 0
    property string schoolWizardState: "starting"
    property var selectedSchoolDetails: ({})
    property string schoolWizardErrorTitle: ""
    property string schoolWizardErrorDetails: ""

    Universal.theme: viewModel.themeMode === "system"
        ? Universal.System
        : darkTheme ? Universal.Dark : Universal.Light
    Universal.accent: primaryColor
    Universal.background: surfaceColor
    Universal.foreground: textColor

    Binding {
        target: EduStyle.Style
        property: "darkTheme"
        value: window.viewModel.darkTheme
    }

    onClosing: {
        viewModel.saveWindowSize(width, height)
        viewModel.shutdown()
    }

    ListModel {
        id: filterSetsModel
        objectName: "filterSetsModel"
    }

    function effectiveDistrictCodes() {
        if (locationTabs.currentIndex === 1) {
            return viewModel.effectiveDistrictCodes(
                departmentCombo.currentValue ? [String(departmentCombo.currentValue)] : [],
                provinceCombo.currentValue ? [String(provinceCombo.currentValue)] : [],
                districtCombo.currentValue ? [String(districtCombo.currentValue)] : []
            )
        }
        return viewModel.effectiveDistrictCodes(
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
        viewModel.selectRegion("")
    }

    function hasActiveFilters() {
        return filterSetsModel.count > 0
            || mapLevel !== "department"
            || selectedDepartmentCodes.length > 0
            || selectedProvinceCodes.length > 0
            || selectedDistrictCodes.length > 0
            || departmentCombo.currentValue
            || provinceCombo.currentValue
            || districtCombo.currentValue
            || publicManagement.checked
            || privateManagement.checked
            || primaryLevel.checked
            || secondaryLevel.checked
            || cetproLevel.checked
    }

    function clearAllFilters() {
        filterSetsModel.clear()
        clearLocationSelection()
        publicManagement.checked = false
        privateManagement.checked = false
        primaryLevel.checked = false
        secondaryLevel.checked = false
        cetproLevel.checked = false
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

    function addFilterSet() {
        var selectedCodes = effectiveDistrictCodes()
        if (!selectedCodes.length)
            selectedCodes.push("")

        var added = 0
        var duplicates = 0
        for (var codeIndex = 0; codeIndex < selectedCodes.length; codeIndex++) {
            var districtCode = selectedCodes[codeIndex]
            var properties = districtCode
                ? viewModel.mapDistrictProperties(districtCode) : null
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
            var key = viewModel.savedSearchKey(filterSet)
            var alreadyAdded = false
            for (var index = 0; index < filterSetsModel.count; index++) {
                if (viewModel.savedSearchKey(filterSetsModel.get(index)) === key) {
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

    function selectedFilterSets() {
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
        return filterSets
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
        exportConfirmationDialog.title = operation === "excel"
            ? "Confirmar exportación Excel"
            : operation === "pdf"
            ? "Confirmar exportación PDF"
            : "Confirmar exportación .eduescale"
        exportConfirmationDialog.description =
            operation === "eduescale" && pendingEduEscaleProtected
            ? "Se guardará un archivo .eduescale protegido con contraseña."
            : operation === "eduescale" && pendingEduEscaleCompressed
            ? "Se guardará un archivo .eduescale comprimido."
            : "Se generará un archivo con los datos exportados."
        exportConfirmationDialog.continueLabel = "Generar archivo"
        exportConfirmationDialog.destinationName = fileNameFromUrl(pendingOutputUrl)
        exportConfirmationDialog.searchSummary = currentSearchSummary()
        exportConfirmationDialog.open()
    }

    function startSchoolSearch() {
        schoolWizardState = "starting"
        selectedSchoolDetails = ({})
        schoolWizardErrorTitle = ""
        schoolWizardErrorDetails = ""
        liveSchoolsMap.clearSchoolLocations()
        workflowStep = 2
        viewModel.startScrape(pendingOutputUrl, pendingSearches)
        if (!viewModel.scrapeRunning && workflowStep === 2)
            workflowStep = 1
    }

    function returnToSearchConfiguration() {
        workflowStep = 0
        selectedSchoolDetails = ({})
        schoolWizardState = "starting"
        schoolWizardErrorTitle = ""
        schoolWizardErrorDetails = ""
    }

    function requestConfirmation(operation, outputUrl) {
        pendingAction = operation
        pendingOutputUrl = outputUrl
        pendingSearches = selectedFilterSets()
        if (operation === "scrape") {
            workflowStep = 1
            return
        }
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
            viewModel: window.viewModel
            onLogsRequested: logViewerDialog.open()
        }

        Frame {
            id: wizardStepHeader
            objectName: "wizardStepHeader"
            Layout.fillWidth: true
            Layout.leftMargin: 12
            Layout.rightMargin: 12
            Layout.bottomMargin: 8
            visible: window.workflowStep === 0
            padding: 10
            background: Rectangle {
                radius: window.controlRadius
                color: window.primarySubtleColor
                border.color: window.primaryBorderColor
            }

            Label {
                anchors.fill: parent
                text: "Paso 1 de 4 · Configura la búsqueda y agrega los filtros que necesites."
                color: window.primaryEmphasisColor
                wrapMode: Text.WordWrap
                verticalAlignment: Text.AlignVCenter
            }
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
                window.contentItem.height - dashboardHeader.height
                - wizardStepHeader.height
                - wizardStepHeader.Layout.bottomMargin - 20
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
                                enabled: !viewModel.scrapeRunning
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
                                Layout.preferredHeight: 310
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
                                            enabled: !viewModel.scrapeRunning
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
                                            enabled: !viewModel.scrapeRunning
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
                                        enabled: !viewModel.scrapeRunning
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
                                        departmentMetadata: viewModel.mapDepartmentMetadata
                                        provinceMetadata: viewModel.mapProvinceMetadata
                                        districtCodes: viewModel.districtCodes
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
                                        model: viewModel.regions
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: viewModel.regionsEnabled
                                            && !viewModel.scrapeRunning
                                        onActivated: function(index) {
                                            viewModel.selectRegion(currentValue)
                                            provinceCombo.currentIndex = 0
                                            districtCombo.currentIndex = 0
                                            window.filterNotice = ""
                                        }
                                    }
                                    StyledComboBox {
                                        id: provinceCombo
                                        objectName: "provinceCombo"
                                        Layout.fillWidth: true
                                        model: viewModel.provinces
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: viewModel.provincesEnabled
                                            && !viewModel.scrapeRunning
                                        onActivated: function(index) {
                                            viewModel.selectProvince(currentValue)
                                            districtCombo.currentIndex = 0
                                            window.filterNotice = ""
                                        }
                                    }
                                    StyledComboBox {
                                        id: districtCombo
                                        objectName: "districtCombo"
                                        Layout.fillWidth: true
                                        model: viewModel.districts
                                        textRole: "label"
                                        valueRole: "code"
                                        enabled: viewModel.districtsEnabled
                                            && !viewModel.scrapeRunning
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
                                    && !viewModel.scrapeRunning
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
                                    enabled: !viewModel.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
                                    id: privateManagement
                                    objectName: "privateManagement"
                                    text: "Privada"
                                    enabled: !viewModel.scrapeRunning
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
                                    enabled: !viewModel.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
                                    id: secondaryLevel
                                    objectName: "secondaryLevel"
                                    text: "Secundaria"
                                    enabled: !viewModel.scrapeRunning
                                    onToggled: window.filterNotice = ""
                                }
                                StyledCheckBox {
                                    id: cetproLevel
                                    objectName: "cetproLevel"
                                    text: "CETPRO"
                                    enabled: !viewModel.scrapeRunning
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
                                enabled: !viewModel.scrapeRunning
                                    && window.selectedManagement().length > 0
                                    && window.selectedLevels().length > 0
                                onClicked: window.addFilterSet()
                            }

                            StyledButton {
                                objectName: "clearAllFiltersButton"
                                variant: "secondary"
                                Layout.fillWidth: true
                                Layout.maximumWidth: filterGrid.columns === 2
                                    ? 260 : 16777215
                                implicitHeight: window.actionButtonHeight
                                font.pixelSize: window.bodyFontSize
                                padding: 8
                                text: "Limpiar todos los filtros"
                                Accessible.name: "Limpiar todos los filtros"
                                enabled: !viewModel.scrapeRunning
                                    && window.hasActiveFilters()
                                onClicked: window.clearAllFilters()
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
                viewModel: window.viewModel
                searchModel: filterSetsModel
                windowHeight: window.height
                scrapeRunning: window.viewModel
                    ? window.viewModel.scrapeRunning : false
                filterNotice: window.filterNotice
            }
        }

        }
    }

    SearchActionBar {
        id: searchActionBar
        visible: window.workflowStep === 0
        Layout.fillWidth: true
        viewModel: window.viewModel
        windowWidth: window.width
        searchCount: filterSetsModel.count
        workflowStep: window.workflowStep
        onScrapeRequested: window.requestConfirmation(
            "scrape", window.viewModel.defaultOutputUrl
        )
        onExcelExportRequested: excelSaveDialog.open()
        onPdfExportRequested: pdfSaveDialog.open()
        onEduEscaleExportRequested: eduEscaleSaveDialog.open()
        onHistoryRequested: exportHistoryDialog.open()
        onOfflineDatabaseSelectionRequested: offlineDatabaseOpenDialog.open()
        onOfflineDatabaseUpdateRequested: {
            if (viewModel.offlineDatabaseAvailable)
                viewModel.updateOfflineDatabase(viewModel.offlineDatabaseUrl)
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
        onAccepted: {
            if (viewModel.selectOfflineDatabase(selectedFile.toString()))
                offlineDatabaseSummaryDialog.open()
        }
    }

    StyledDialog {
        id: offlineDatabaseSummaryDialog
        objectName: "offlineDatabaseSummaryDialog"
        title: "Resumen de colegios por ubicación"
        modal: true
        anchors.centerIn: parent
        width: Math.min(window.width - 40, 760)
        height: Math.min(window.height - 40, 560)
        standardButtons: Dialog.NoButton

        contentItem: ColumnLayout {
            spacing: 10

            Label {
                Layout.fillWidth: true
                text: {
                    var summary = window.viewModel.offlineDatabaseLocationSummary
                    return summary.total_schools.toLocaleString()
                        + " colegios · "
                        + summary.department_count.toLocaleString()
                        + " departamentos · "
                        + summary.province_count.toLocaleString()
                        + " provincias · "
                        + summary.district_count.toLocaleString()
                        + " distritos"
                }
                color: EduStyle.Style.mutedTextColor
                wrapMode: Text.WordWrap
            }

            RowLayout {
                Layout.fillWidth: true
                Label {
                    Layout.fillWidth: true
                    text: "Departamento / provincia / distrito"
                    font.weight: Font.DemiBold
                    color: EduStyle.Style.textColor
                }
                Label {
                    Layout.preferredWidth: 90
                    text: "Colegios"
                    horizontalAlignment: Text.AlignRight
                    font.weight: Font.DemiBold
                    color: EduStyle.Style.textColor
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                visible: window.viewModel.offlineDatabaseLocationSummary
                    .locations.length === 0
                radius: EduStyle.Style.controlRadius
                color: EduStyle.Style.subtleSurfaceColor
                border.color: EduStyle.Style.borderColor

                Label {
                    anchors.centerIn: parent
                    text: "La base no contiene colegios."
                    color: EduStyle.Style.mutedTextColor
                }
            }

            ListView {
                id: offlineDatabaseLocationList
                objectName: "offlineDatabaseLocationList"
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                spacing: 4
                visible: window.viewModel.offlineDatabaseLocationSummary
                    .locations.length > 0
                model: window.viewModel.offlineDatabaseLocationSummary.locations

                delegate: Frame {
                    width: offlineDatabaseLocationList.width
                        - offlineDatabaseLocationScrollBar.width
                    padding: 8

                    background: Rectangle {
                        radius: EduStyle.Style.controlRadius
                        color: EduStyle.Style.subtleSurfaceColor
                        border.color: EduStyle.Style.borderColor
                    }

                    RowLayout {
                        anchors.fill: parent
                        Label {
                            Layout.fillWidth: true
                            text: modelData.region + " / " + modelData.province
                                + " / " + modelData.district
                            color: EduStyle.Style.textColor
                            wrapMode: Text.WordWrap
                        }
                        Label {
                            Layout.preferredWidth: 90
                            text: modelData.schools.toLocaleString()
                            color: EduStyle.Style.textColor
                            horizontalAlignment: Text.AlignRight
                        }
                    }
                }

                ScrollBar.vertical: StyledScrollBar {
                    id: offlineDatabaseLocationScrollBar
                    objectName: "offlineDatabaseLocationScrollBar"
                    policy: ScrollBar.AsNeeded
                }
            }

            StyledButton {
                objectName: "closeOfflineDatabaseSummaryButton"
                Layout.alignment: Qt.AlignRight
                text: "Cerrar"
                variant: "secondary"
                Accessible.name: "Cerrar resumen por ubicación"
                onClicked: offlineDatabaseSummaryDialog.close()
            }
        }
    }

    FileDialog {
        id: offlineDatabaseSaveDialog
        objectName: "offlineDatabaseSaveDialog"
        title: "Descargar base de datos de ESCALE"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Base de datos EduEscale (*.sqlite3)"]
        defaultSuffix: "sqlite3"
        currentFile: viewModel.defaultOfflineDatabaseUrl
        onAccepted: viewModel.updateOfflineDatabase(selectedFile.toString())
    }

    FileDialog {
        id: excelSaveDialog
        objectName: "excelSaveDialog"
        title: "Exportar Excel"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Libro de Excel (*.xlsx)"]
        defaultSuffix: "xlsx"
        currentFile: viewModel.defaultOutputUrl
        onAccepted: window.requestConfirmation("excel", selectedFile.toString())
    }

    FileDialog {
        id: pdfSaveDialog
        objectName: "pdfSaveDialog"
        title: "Exportar PDF"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Documento PDF (*.pdf)"]
        defaultSuffix: "pdf"
        currentFile: viewModel.defaultPdfOutputUrl
        onAccepted: window.requestConfirmation("pdf", selectedFile.toString())
    }

    FileDialog {
        id: eduEscaleSaveDialog
        objectName: "eduEscaleSaveDialog"
        title: "Exportar .eduescale"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Archivo EduEscale (*.eduescale)"]
        defaultSuffix: "eduescale"
        currentFile: viewModel.defaultEduEscaleOutputUrl
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
                            window.pendingSearches = window.selectedFilterSets()
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
            if (window.pendingAction === "excel") {
                viewModel.exportExcelFile(window.pendingOutputUrl)
            } else if (window.pendingAction === "pdf") {
                viewModel.exportPdfFile(window.pendingOutputUrl)
            } else if (window.pendingAction === "eduescale") {
                viewModel.exportEduEscaleFile(
                        window.pendingOutputUrl,
                        window.pendingEduEscalePassword,
                        window.pendingEduEscaleCompressed
                )
            }
        }
        onClosed: {
            window.pendingAction = ""
            window.pendingOutputUrl = ""
            window.pendingSearches = []
            window.pendingEduEscalePassword = ""
            window.pendingEduEscaleProtected = false
            window.pendingEduEscaleCompressed = false
        }
    }

    SchoolSearchReviewPage {
        id: schoolWizardReviewPage
        window: window
        onLogsRequested: logViewerDialog.open()
    }
    Item {
        id: schoolWizardPage
        objectName: "schoolWizardPage"
        anchors.fill: parent
        z: 10
        visible: window.workflowStep >= 2

        Rectangle {
            anchors.fill: parent
            color: window.canvasColor
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 16
            spacing: 10

            RowLayout {
                Layout.fillWidth: true

                Label {
                    objectName: "schoolWizardTitle"
                    Layout.fillWidth: true
                    Layout.minimumWidth: 0
                    text: window.workflowStep === 2
                        ? "Paso 3 de 4 · Descarga y mapa"
                        : "Paso 4 de 4 · Resultados"
                    color: window.textColor
                    font.pixelSize: window.appTitleFontSize
                    font.weight: Font.DemiBold
                    elide: Text.ElideRight
                }

                LogsButton {
                    objectName: "wizardLogsButton"
                    onClicked: logViewerDialog.open()
                }
            }

            Label {
                objectName: "schoolWizardStatusLabel"
                Layout.fillWidth: true
                text: window.workflowStep === 2
                    ? window.schoolWizardState === "starting"
                        ? "Preparando la búsqueda..."
                        : window.viewModel.progressMessage
                    : window.schoolWizardState === "completed"
                    ? window.viewModel.statusText
                    : window.schoolWizardState === "cancelled"
                    ? "Búsqueda cancelada. El mapa conserva los colegios parciales."
                    : window.schoolWizardErrorTitle.length
                    ? window.schoolWizardErrorTitle
                    : window.viewModel.statusText
                color: window.textColor
                font.weight: Font.DemiBold
                wrapMode: Text.WordWrap
            }

            Label {
                Layout.fillWidth: true
                visible: window.schoolWizardErrorDetails.length > 0
                text: window.schoolWizardErrorDetails
                color: window.mutedTextColor
                wrapMode: Text.WordWrap
            }

            StyledProgressBar {
                objectName: "schoolWizardProgress"
                Layout.fillWidth: true
                visible: window.workflowStep === 2
                    && window.viewModel.progressVisible
                from: 0
                to: Math.max(window.viewModel.progressMaximum, 1)
                value: window.viewModel.progressValue
                indeterminate: window.viewModel.progressIndeterminate
            }

            Item {
                id: liveMapArea
                objectName: "liveMapArea"
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.minimumHeight: 300

                DistrictMap {
                    id: liveSchoolsMap
                    objectName: "liveSchoolsMap"
                    anchors.fill: parent
                    schoolMode: true
                    departmentMetadata: window.viewModel.mapDepartmentMetadata
                    provinceMetadata: window.viewModel.mapProvinceMetadata
                    districtCodes: window.viewModel.districtCodes
                    onSchoolSelected: function(schoolId) {
                        var details = window.viewModel.schoolDetails(schoolId)
                        if (details.schoolId)
                            window.selectedSchoolDetails = details
                    }
                }

                Label {
                    anchors.centerIn: parent
                    visible: liveSchoolsMap.mapReady
                        && liveSchoolsMap.schoolPointCount === 0
                        && window.workflowStep === 2
                    text: "Los colegios con coordenadas aparecerán aquí mientras se descargan."
                    color: window.mutedTextColor
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                    width: parent.width - 40
                }

                SchoolDetailsCard {
                    id: schoolDetailsCard
                    window: window
                    onCloseRequested: window.selectedSchoolDetails = ({})
                }
            }

            Label {
                objectName: "schoolWizardPointCount"
                Layout.fillWidth: true
                text: liveSchoolsMap.schoolPointCount.toLocaleString()
                    + " colegios con coordenadas en el mapa"
                color: window.mutedTextColor
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 8

                Item { Layout.fillWidth: true }

                StyledButton {
                    objectName: "schoolWizardCancelButton"
                    visible: window.workflowStep === 2
                    enabled: window.viewModel.scrapeRunning
                        && !window.viewModel.cancelRequested
                    text: window.viewModel.cancelRequested
                        ? "Cancelando..." : "Cancelar búsqueda"
                    variant: "danger"
                    onClicked: window.viewModel.cancelScrape()
                }

                StyledButton {
                    objectName: "schoolWizardExcelButton"
                    visible: window.workflowStep === 3
                        && window.viewModel.exportsAvailable
                    text: "Exportar Excel"
                    variant: "secondary"
                    onClicked: excelSaveDialog.open()
                }

                StyledButton {
                    objectName: "schoolWizardPdfButton"
                    visible: window.workflowStep === 3
                        && window.viewModel.exportsAvailable
                    text: "Exportar PDF"
                    variant: "secondary"
                    onClicked: pdfSaveDialog.open()
                }

                StyledButton {
                    objectName: "schoolWizardEduEscaleButton"
                    visible: window.workflowStep === 3
                        && window.viewModel.exportsAvailable
                    text: "Exportar .eduescale"
                    variant: "secondary"
                    onClicked: eduEscaleSaveDialog.open()
                }

                StyledButton {
                    objectName: "schoolWizardCloseButton"
                    visible: window.workflowStep === 3
                    text: "Volver a búsquedas"
                    variant: "primary"
                    onClicked: window.returnToSearchConfiguration()
                }
            }
        }
    }

    LogViewerDialog {
        id: logViewerDialog
        viewModel: window.viewModel ? window.viewModel.logViewer : null
    }

    ExportHistoryDialog {
        id: exportHistoryDialog
        viewModel: window.viewModel
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
        target: viewModel

        function onSchoolLocationsReceived(locations) {
            liveSchoolsMap.appendSchoolLocations(locations)
        }

        function onSchoolSearchStarted() {
            window.schoolWizardState = "running"
            window.workflowStep = 2
        }

        function onSchoolSearchCompleted() {
            window.schoolWizardState = "completed"
            window.workflowStep = 3
        }

        function onSchoolSearchStopped(reason) {
            window.schoolWizardState = reason
            window.workflowStep = 3
        }

        function onErrorRaised(title, details) {
            if (schoolWizardPage.visible && window.workflowStep >= 2) {
                window.schoolWizardErrorTitle = title
                window.schoolWizardErrorDetails = details
                return
            }
            errorDialog.title = title
            errorDialog.details = details
            errorDialog.open()
        }
    }
}
