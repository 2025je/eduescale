import QtQuick
import QtQuick.Controls
import QtWebChannel
import QtWebEngine
import "styles" as EduStyle

Rectangle {
    id: root
    objectName: "districtMap"
    color: EduStyle.Style.subtleSurfaceColor
    radius: EduStyle.Style.cardRadius
    border.color: EduStyle.Style.borderColor
    border.width: 1
    clip: true

    property var departmentMetadata: ({})
    property var provinceMetadata: ({})
    property var districtCodes: []
    property string mapLevel: "department"
    property var parentCodes: []
    property var selectionCodes: []
    property var selectedCodes: []
    property string loadingError: ""
    property bool mapReady: false
    property int mapRequestId: 0
    property int renderedFeatureCount: 0
    property real mapZoom: 0
    property string mapBoundsJson: ""
    property real mapMinimumZoom: 0
    readonly property int featureCount: districtCodes.length
    readonly property int expectedFeatureCount: {
        if (mapLevel === "department")
            return Object.keys(departmentMetadata).length

        var count = 0
        if (mapLevel === "province") {
            var provinceCodes = Object.keys(provinceMetadata)
            for (var index = 0; index < provinceCodes.length; index++) {
                var province = provinceMetadata[provinceCodes[index]]
                if (parentCodes.indexOf(province.departmentCode) >= 0)
                    count++
            }
            return count
        }
        for (var parentIndex = 0; parentIndex < parentCodes.length; parentIndex++) {
            var selectedProvince = provinceMetadata[parentCodes[parentIndex]]
            if (selectedProvince)
                count += selectedProvince.districtCount
        }
        return count
    }
    signal mapSelectionChanged(string level, var codes)

    function syncMapView() {
        if (!mapReady)
            return
        selectedCodes = selectionCodes.slice()
        mapRequestId++
        webView.runJavaScript(
            "window.setMapView("
                + JSON.stringify(mapLevel) + ","
                + JSON.stringify(parentCodes) + ","
                + JSON.stringify(selectionCodes) + ","
                + mapRequestId + ")"
        )
    }

    function syncSelection() {
        if (!mapReady)
            return
        selectedCodes = selectionCodes.slice()
        webView.runJavaScript(
            "window.setSelectedCodes(" + JSON.stringify(selectionCodes) + ")"
        )
    }

    function clearSelection() {
        selectedCodes = []
        mapSelectionChanged(mapLevel, [])
        syncMapView()
    }

    onMapLevelChanged: syncMapView()
    onParentCodesChanged: syncMapView()
    onSelectionCodesChanged: syncSelection()
    onMapReadyChanged: syncMapView()

    QtObject {
        id: mapBridge
        objectName: "mapBridge"

        function setSelectionFromWeb(level, codesJson) {
            var codes
            try {
                codes = JSON.parse(codesJson)
            } catch (error) {
                root.loadingError = "La selección del mapa no es válida."
                return
            }
            if (String(level) !== root.mapLevel || !Array.isArray(codes)) {
                root.loadingError = "La selección del mapa no es válida."
                return
            }

            var metadata = root.mapLevel === "department"
                ? root.departmentMetadata
                : root.provinceMetadata
            var uniqueCodes = []
            for (var index = 0; index < codes.length; index++) {
                var code = String(codes[index])
                var knownCode = root.mapLevel === "district"
                    ? root.districtCodes.indexOf(code) >= 0 : Boolean(metadata[code])
                if (!knownCode) {
                    root.loadingError = "El mapa devolvió un código desconocido: "
                        + code + " (nivel " + root.mapLevel + ")."
                    return
                }
                if (uniqueCodes.indexOf(code) < 0)
                    uniqueCodes.push(code)
            }
            root.selectedCodes = uniqueCodes
            root.loadingError = ""
            root.mapSelectionChanged(root.mapLevel, uniqueCodes)
        }

        function reportMapReady(level, count, requestId, zoom, boundsJson, minZoom) {
            if (Number(requestId) !== root.mapRequestId
                    || String(level) !== root.mapLevel)
                return
            if (Number(count) !== root.expectedFeatureCount) {
                root.loadingError = "La capa geográfica está incompleta o no es válida."
                return
            }
            root.renderedFeatureCount = Number(count)
            root.mapZoom = Number(zoom)
            root.mapBoundsJson = String(boundsJson)
            root.mapMinimumZoom = Number(minZoom)
            root.loadingError = ""
            root.mapReady = true
        }

        function reportMapError(message) {
            root.mapReady = false
            root.loadingError = "No se pudo cargar el mapa: " + String(message)
        }
    }

    WebChannel {
        id: districtChannel
        Component.onCompleted: registerObject("mapBridge", mapBridge)
    }

    WebEngineProfilePrototype {
        id: mapProfilePrototype
        storageName: "eduescale-map"
        httpCacheType: WebEngineProfile.DiskHttpCache
    }

    WebEngineView {
        id: webView
        objectName: "districtMapWebView"
        anchors.fill: parent
        anchors.margins: 1
        webChannel: districtChannel
        settings.localContentCanAccessFileUrls: true
        settings.localContentCanAccessRemoteUrls: true

        onLoadingChanged: function(loadRequest) {
            if (loadRequest.status === WebEngineView.LoadFailedStatus) {
                root.mapReady = false
                root.loadingError = "No se pudo abrir el mapa local."
            }
        }
    }

    Component.onCompleted: {
        const profile = mapProfilePrototype.instance()
        if (!profile) {
            loadingError = "No se pudo crear el perfil del mapa."
            return
        }
        profile.httpUserAgent =
            "EduEscaleDesktop/1.0 (+https://github.com/2025je/eduescale)"
        webView.profile = profile
        webView.url = Qt.resolvedUrl("../assets/maplibre/map.html")
    }

    StyledComboBox {
        id: mapStyleSelector
        objectName: "mapStyleSelector"
        anchors.top: parent.top
        anchors.left: parent.left
        anchors.margins: 12
        width: EduStyle.Style.mapStyleSelectorWidth
        implicitHeight: EduStyle.Style.mapStyleSelectorHeight
        z: 2
        enabled: root.mapReady
        textRole: "label"
        valueRole: "styleKey"
        model: ListModel {
            ListElement { label: "Liberty"; styleKey: "liberty" }
            ListElement { label: "Bright"; styleKey: "bright" }
            ListElement { label: "Positron"; styleKey: "positron" }
            ListElement { label: "Dark"; styleKey: "dark" }
            ListElement { label: "Fiord"; styleKey: "fiord" }
            ListElement { label: "Estándar OSM"; styleKey: "osm" }
            ListElement { label: "Topográfico"; styleKey: "topographic" }
            ListElement { label: "Satélite"; styleKey: "satellite" }
        }
        onActivated: function(index) {
            webView.runJavaScript(
                "window.setMapStyle("
                    + JSON.stringify(model.get(index).styleKey) + ")"
            )
        }
    }

    Timer {
        interval: 30000
        running: webView.loadProgress === 100 && !root.mapReady
            && !root.loadingError
        repeat: false

        onTriggered: {
            if (!root.mapReady && !root.loadingError)
                root.loadingError = "El mapa tardó demasiado en cargar."
        }
    }

    Label {
        anchors.centerIn: parent
        width: parent.width - 32
        visible: !root.mapReady && !root.loadingError.length
        text: "Cargando mapa..."
        color: EduStyle.Style.mutedTextColor
        horizontalAlignment: Text.AlignHCenter
    }

    Label {
        anchors.centerIn: parent
        width: parent.width - 32
        visible: root.loadingError.length > 0
        text: root.loadingError
        color: EduStyle.Style.dangerColor
        wrapMode: Text.WordWrap
        horizontalAlignment: Text.AlignHCenter
    }
}
