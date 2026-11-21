import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Styles 1.0 as EduStyle

Frame {
    id: root
    objectName: "schoolDetailsCard"

    required property var window
    signal closeRequested()
    
    // Iconos
    readonly property string iconSchoolManagement: "⊙"
    readonly property string iconSchoolLevel: "▤"
    readonly property string iconReminderTime: "⏰"
    readonly property string iconSchoolAddress: "⊙"
    readonly property string iconMapLocation: "⊙"
    readonly property string iconSchoolWebsite: "⌘"

    anchors.top: parent.top
    anchors.right: parent.right
    anchors.margins: 12
    width: Math.min(390, parent.width - 24)
    height: Math.min(
        parent.height - 24,
        Math.max(260, schoolDetailsContent.implicitHeight
            + schoolDetailsHeader.implicitHeight + 54)
    )
    visible: window.selectedSchoolDetails.schoolId !== undefined
    z: 3
    padding: 12

    function schoolCount(value) {
        return value === null || value === undefined
            ? "No informado" : Number(value).toLocaleString()
    }

    function schoolManagement(value) {
        if (value === "publica")
            return "Pública"
        if (value === "privada")
            return "Privada"
        return "No informada"
    }

    function schoolLevel(value) {
        if (value === "primaria")
            return "Primaria"
        if (value === "secundaria")
            return "Secundaria"
        if (value === "cetpro")
            return "CETPRO"
        return value || "Nivel no informado"
    }

    function schoolSettlementText() {
        var locality = String(window.selectedSchoolDetails.localidad || "").trim()
        var populatedCenter = String(
            window.selectedSchoolDetails.centroPoblado || ""
        ).trim()
        if (!locality)
            return populatedCenter || "No informada"
        if (!populatedCenter
                || locality.toLocaleLowerCase()
                    === populatedCenter.toLocaleLowerCase())
            return locality
        return locality + " · Centro poblado: " + populatedCenter
    }

    background: Rectangle {
        radius: window.cardRadius
        color: window.surfaceColor
        border.color: window.borderColor
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 10

        RowLayout {
            id: schoolDetailsHeader
            Layout.fillWidth: true
            spacing: 8

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2

                Label {
                    Layout.fillWidth: true
                    text: "FICHA DEL COLEGIO"
                    color: window.primaryColor
                    font.pixelSize: window.captionFontSize
                    font.weight: Font.DemiBold
                    font.letterSpacing: 0.8
                }

                Label {
                    objectName: "schoolDetailsName"
                    Layout.fillWidth: true
                    text: window.selectedSchoolDetails.nombre || "Colegio sin nombre"
                    color: window.textColor
                    font.pixelSize: window.sectionTitleFontSize
                    font.weight: Font.DemiBold
                    wrapMode: Text.WordWrap
                }
            }

            StyledButton {
                objectName: "closeSchoolDetailsButton"
                text: "Cerrar"
                variant: "secondary"
                implicitHeight: window.compactButtonHeight
                font.pixelSize: window.smallFontSize
                onClicked: root.closeRequested()
            }
        }

        ScrollView {
            id: schoolDetailsScroll
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            contentWidth: availableWidth

            ColumnLayout {
                id: schoolDetailsContent
                width: schoolDetailsScroll.availableWidth
                spacing: 12

                Label {
                    objectName: "schoolDetailsLocation"
                    Layout.fillWidth: true
                    text: [
                        window.selectedSchoolDetails.distrito,
                        window.selectedSchoolDetails.provincia,
                        window.selectedSchoolDetails.departamento
                    ].filter(function(value) {
                        return Boolean(value)
                    }).join(" · ") || "Ubicación no informada"
                    color: window.mutedTextColor
                    font.pixelSize: window.bodyFontSize
                    wrapMode: Text.WordWrap
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    Rectangle {
                        radius: height / 2
                        color: window.primarySubtleColor
                        implicitWidth: managementChipText.implicitWidth + 20
                        implicitHeight: 28

                        Label {
                            id: managementChipText
                            anchors.centerIn: parent
                            text: root.schoolManagement(
                                window.selectedSchoolDetails.gestion
                            )
                            color: window.primaryEmphasisColor
                            font.pixelSize: window.smallFontSize
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        radius: height / 2
                        color: window.subtleSurfaceColor
                        implicitHeight: 28

                        Label {
                            id: levelChipText
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            text: (
                                window.selectedSchoolDetails.niveles || []
                            ).map(root.schoolLevel).join(" · ")
                                || "Nivel no informado"
                            color: window.textColor
                            font.pixelSize: window.smallFontSize
                            verticalAlignment: Text.AlignVCenter
                            elide: Text.ElideRight
                        }
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    Rectangle {
                        Layout.fillWidth: true
                        implicitHeight: 66
                        radius: window.controlRadius
                        color: window.subtleSurfaceColor

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 2

                            Label {
                                objectName: "schoolDetailsAlumnos"
                                Layout.fillWidth: true
                                text: root.schoolCount(
                                    window.selectedSchoolDetails.alumnos
                                )
                                color: window.textColor
                                font.pixelSize: window.sectionTitleFontSize
                                font.weight: Font.DemiBold
                            }

                                Label {
                                text: root.iconSchoolManagement + " ALUMNOS"
                                color: window.mutedTextColor
                                font.pixelSize: window.captionFontSize
                            }
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        implicitHeight: 66
                        radius: window.controlRadius
                        color: window.subtleSurfaceColor

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 2

                            Label {
                                objectName: "schoolDetailsSecciones"
                                Layout.fillWidth: true
                                text: root.schoolCount(
                                    window.selectedSchoolDetails.secciones
                                )
                                color: window.textColor
                                font.pixelSize: window.sectionTitleFontSize
                                font.weight: Font.DemiBold
                            }

                            Label {
                                text: root.iconSchoolLevel + " SECCIONES"
                                color: window.mutedTextColor
                                font.pixelSize: window.captionFontSize
                            }
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        implicitHeight: 66
                        radius: window.controlRadius
                        color: window.subtleSurfaceColor

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 2

                            Label {
                                Layout.fillWidth: true
                                text: (
                                    window.selectedSchoolDetails.turnos || []
                                ).join(" · ") || "No informado"
                                color: window.textColor
                                font.pixelSize: window.bodyFontSize
                                font.weight: Font.DemiBold
                                elide: Text.ElideRight
                            }

                            Label {
                                text: root.iconReminderTime + " TURNOS"
                                color: window.mutedTextColor
                                font.pixelSize: window.captionFontSize
                            }
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 1
                    color: window.borderColor
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 4

                    Label {
                        text: root.iconSchoolAddress + " DIRECCIÓN"
                        color: window.mutedTextColor
                        font.pixelSize: window.captionFontSize
                        font.weight: Font.DemiBold
                        font.letterSpacing: 0.6
                    }

                    Label {
                        objectName: "schoolDetailsAddress"
                        Layout.fillWidth: true
                        text: window.selectedSchoolDetails.direccion || "No informada"
                        color: window.textColor
                        wrapMode: Text.WordWrap
                    }

                    Label {
                        text: root.iconMapLocation + " LOCALIDAD"
                        color: window.mutedTextColor
                        font.pixelSize: window.captionFontSize
                        font.weight: Font.DemiBold
                        font.letterSpacing: 0.6
                    }

                    Label {
                        objectName: "schoolDetailsSettlement"
                        Layout.fillWidth: true
                        text: root.schoolSettlementText()
                        color: window.textColor
                        wrapMode: Text.WordWrap
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 1
                    color: window.borderColor
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6

                    Label {
                        Layout.fillWidth: true
                        text: root.iconSchoolWebsite + " OFERTA EDUCATIVA"
                        color: window.textColor
                        font.weight: Font.DemiBold
                    }

                    Label {
                        objectName: "schoolDetailsOfferCount"
                        text: {
                            var count = (
                                window.selectedSchoolDetails.ofertaEducativa || []
                            ).length
                            return count + (count === 1 ? " oferta" : " ofertas")
                        }
                        color: window.mutedTextColor
                        font.pixelSize: window.smallFontSize
                    }
                }

                Repeater {
                    objectName: "schoolOfferList"
                    model: (
                        window.selectedSchoolDetails.ofertaEducativa || []
                    )

                    delegate: Rectangle {
                        Layout.fillWidth: true
                        implicitHeight: 48
                        radius: window.controlRadius
                        color: window.subtleSurfaceColor

                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            spacing: 8

                            Rectangle {
                                Layout.preferredWidth: 3
                                Layout.fillHeight: true
                                radius: 2
                                color: window.primaryColor
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2

                                Label {
                                    Layout.fillWidth: true
                                    text: root.schoolLevel(modelData.nivel)
                                        + (modelData.turno
                                            ? " · " + modelData.turno : "")
                                    color: window.textColor
                                    font.weight: Font.DemiBold
                                }

                                Label {
                                    Layout.fillWidth: true
                                    text: root.schoolCount(modelData.alumnos)
                                        + " alumnos · "
                                        + root.schoolCount(modelData.secciones)
                                        + " secciones"
                                    color: window.mutedTextColor
                                    font.pixelSize: window.smallFontSize
                                }
                            }
                        }
                    }
                }

                Label {
                    Layout.fillWidth: true
                    visible: (
                        window.selectedSchoolDetails.ofertaEducativa || []
                    ).length === 0
                    text: "Oferta educativa no informada."
                    color: window.mutedTextColor
                }
            }
        }
    }
}
