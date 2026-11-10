import QtQuick
import QtQuick.Controls
import QtWebChannel
import QtWebEngine

Rectangle {
    id: root
    objectName: "districtMap"
    color: "#F4F7F5"
    radius: 12
    border.color: "#DCE6E0"
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

    WebEngineView {
        id: webView
        objectName: "districtMapWebView"
        anchors.fill: parent
        anchors.margins: 1
        url: Qt.resolvedUrl("../assets/maplibre/map.html")
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
        color: "#65746C"
        horizontalAlignment: Text.AlignHCenter
    }

    Label {
        anchors.centerIn: parent
        width: parent.width - 32
        visible: root.loadingError.length > 0
        text: root.loadingError
        color: "#A83232"
        wrapMode: Text.WordWrap
        horizontalAlignment: Text.AlignHCenter
    }
}
