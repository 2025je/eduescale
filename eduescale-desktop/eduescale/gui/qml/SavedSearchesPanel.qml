import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import "styles" as EduStyle

Frame {
    id: root
    objectName: "searchesCard"

    required property var controller
    required property var searchModel
    required property int windowHeight
    property bool scrapeRunning: false
    property string filterNotice: ""
    property string fileNotice: ""

    padding: 14

    background: Rectangle {
        radius: EduStyle.Style.cardRadius
        color: EduStyle.Style.surfaceColor
        border.width: 1
        border.color: EduStyle.Style.borderColor
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

    function portableSearchesJson() {
        var searches = []
        for (var index = 0; index < searchModel.count; index++) {
            var search = searchModel.get(index)
            searches.push({
                name: search.name,
                department: search.regionCode ? search.regionLabel : null,
                province: search.provinceCode ? search.provinceLabel : null,
                district: search.districtCode ? search.districtLabel : null,
                department_code: search.regionCode || null,
                province_code: search.provinceCode || null,
                district_code: search.districtCode || null,
                management: search.management.split(",").filter(Boolean),
                levels: search.level.split(",").filter(Boolean)
            })
        }
        return JSON.stringify({
            format: "eduescale.saved-searches",
            version: 1,
            searches: searches
        }, null, 2)
    }

    function mergeImportedSearches(searchesJson) {
        var imported = JSON.parse(searchesJson)
        var added = 0
        var duplicates = 0
        for (var index = 0; index < imported.length; index++) {
            var search = imported[index]
            var duplicate = false
            for (var existingIndex = 0; existingIndex < searchModel.count; existingIndex++) {
                if (filterSetKey(searchModel.get(existingIndex)) === filterSetKey(search)) {
                    duplicate = true
                    break
                }
            }
            if (duplicate) {
                duplicates++
                continue
            }
            searchModel.append(search)
            added++
        }
        fileNotice = added
            ? "Se importaron " + added + " búsquedas"
                + (duplicates ? "; " + duplicates + " duplicadas se omitieron." : ".")
            : "No se agregaron búsquedas; todas ya existían."
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 10

        RowLayout {
            Layout.fillWidth: true

            Label {
                Layout.fillWidth: true
                text: "Mis búsquedas"
                color: EduStyle.Style.textColor
                font.pixelSize: EduStyle.Style.sectionTitleFontSize
                font.weight: Font.DemiBold
            }

            Rectangle {
                implicitWidth: countLabel.implicitWidth + 20
                implicitHeight: 34
                radius: height / 2
                color: EduStyle.Style.primarySubtleColor
                border.width: 1
                border.color: EduStyle.Style.primaryBorderColor

                Label {
                    id: countLabel
                    objectName: "searchCountLabel"
                    anchors.centerIn: parent
                    text: searchModel.count + " filtros"
                    color: EduStyle.Style.primaryEmphasisColor
                    font.pixelSize: EduStyle.Style.bodyFontSize
                    font.weight: Font.DemiBold
                }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 8

            StyledButton {
                objectName: "importSavedSearchesButton"
                Layout.fillWidth: true
                text: "Importar"
                variant: "secondary"
                Accessible.name: "Importar búsquedas guardadas desde un archivo JSON"
                onClicked: importSavedSearchesDialog.open()
            }

            StyledButton {
                objectName: "exportSavedSearchesButton"
                Layout.fillWidth: true
                text: "Compartir"
                variant: "secondary"
                Accessible.name: "Exportar búsquedas guardadas a un archivo JSON"
                enabled: searchModel.count > 0
                onClicked: exportSavedSearchesDialog.open()
            }
        }

        Label {
            objectName: "savedSearchesNotice"
            Layout.fillWidth: true
            visible: text.length > 0
            text: fileNotice || filterNotice
            color: fileNotice.length
                ? EduStyle.Style.primaryEmphasisColor
                : EduStyle.Style.mutedTextColor
            wrapMode: Text.WordWrap
            font.pixelSize: EduStyle.Style.smallFontSize
        }

        Item {
            id: searchListViewport
            objectName: "searchListViewport"
            Layout.fillWidth: true
            Layout.preferredHeight: Math.min(
                Math.max(searchListView.contentHeight, emptySearchState.implicitHeight),
                Math.max(180, windowHeight * 0.45)
            )
            clip: true

            ListView {
                id: searchListView
                objectName: "searchListView"
                anchors.fill: parent
                clip: true
                model: searchModel
                spacing: 6

                delegate: Frame {
                    width: searchListView.width - searchListVerticalScrollBar.width
                    padding: 12

                    background: Rectangle {
                        radius: EduStyle.Style.controlRadius
                        color: EduStyle.Style.subtleSurfaceColor
                        border.width: 1
                        border.color: EduStyle.Style.borderColor
                    }

                    RowLayout {
                        anchors.fill: parent
                        spacing: 10

                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4

                            StyledTextField {
                                objectName: "savedSearchName_" + index
                                Layout.fillWidth: true
                                text: name
                                placeholderText: "Nombre de búsqueda"
                                Accessible.name: "Nombre de búsqueda"
                                onEditingFinished: {
                                    var normalizedName = text.trim()
                                    if (normalizedName.length) {
                                        searchModel.setProperty(index, "name", normalizedName)
                                    } else {
                                        text = name
                                    }
                                }
                            }

                            Label {
                                Layout.fillWidth: true
                                wrapMode: Text.WordWrap
                                text: {
                                    var location = []
                                    if (regionLabel)
                                        location.push(regionLabel)
                                    if (provinceLabel)
                                        location.push(provinceLabel)
                                    if (districtCode)
                                        location.push(districtLabel)
                                    return location.length
                                        ? location.join(" / ")
                                        : "Todo el Perú"
                                }
                                color: EduStyle.Style.textColor
                                font.weight: Font.DemiBold
                            }

                            Label {
                                Layout.fillWidth: true
                                wrapMode: Text.WordWrap
                                text: "Gestión: " + management.replace(/,/g, ", ")
                                    + " · Nivel: " + level.replace(/,/g, ", ")
                                color: EduStyle.Style.mutedTextColor
                                font.pixelSize: EduStyle.Style.bodyFontSize
                            }
                        }

                        StyledButton {
                            objectName: "removeSavedSearch_" + index
                            text: "Quitar"
                            variant: "quiet"
                            implicitHeight: 32
                            font.pixelSize: EduStyle.Style.smallFontSize
                            padding: 4
                            enabled: !scrapeRunning
                            Accessible.name: "Quitar búsqueda " + name
                            onClicked: searchModel.remove(index)
                        }
                    }
                }

                ScrollBar.vertical: StyledScrollBar {
                    id: searchListVerticalScrollBar
                    objectName: "searchListVerticalScrollBar"
                    anchors.right: parent.right
                    policy: ScrollBar.AsNeeded
                    visible: searchListView.contentHeight
                        > searchListView.height + 1
                    interactive: true
                    width: EduStyle.Style.scrollBarWidth
                }
            }

            Rectangle {
                id: emptySearchState
                objectName: "emptySearchState"
                anchors.fill: parent
                implicitHeight: emptySearchLabel.implicitHeight + 24
                visible: searchModel.count === 0
                radius: EduStyle.Style.controlRadius
                color: EduStyle.Style.subtleSurfaceColor
                border.width: 1
                border.color: EduStyle.Style.borderColor

                Label {
                    id: emptySearchLabel
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.margins: 12
                    text: "Todavía no hay búsquedas. Elige ubicación, gestión y nivel educativo; después agrégala."
                    color: EduStyle.Style.mutedTextColor
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                }
            }
        }
    }

    FileDialog {
        id: importSavedSearchesDialog
        objectName: "importSavedSearchesDialog"
        title: "Importar búsquedas"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Búsquedas EduEscale (*.json)"]
        onAccepted: root.controller.loadSavedSearches(selectedFile.toString())
    }

    FileDialog {
        id: exportSavedSearchesDialog
        objectName: "exportSavedSearchesDialog"
        title: "Compartir búsquedas"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Búsquedas EduEscale (*.json)"]
        defaultSuffix: "json"
        onAccepted: {
            if (root.controller.saveSavedSearches(
                    selectedFile.toString(),
                    root.portableSearchesJson()
                )) {
                fileNotice = "Archivo de búsquedas guardado."
            }
        }
    }

    Connections {
        target: root.controller

        function onSavedSearchesLoaded(searchesJson) {
            root.mergeImportedSearches(searchesJson)
        }
    }
}
