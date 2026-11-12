import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import "styles" as EduStyle

Frame {
    id: root
    objectName: "dashboardHeader"

    property var controller: null

    padding: 10

    background: Rectangle {
        radius: EduStyle.Style.cardRadius
        color: EduStyle.Style.surfaceColor
        border.width: 1
        border.color: EduStyle.Style.borderColor
    }

    RowLayout {
        anchors.fill: parent
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
                color: EduStyle.Style.textColor
                font.pixelSize: EduStyle.Style.appTitleFontSize
                font.weight: Font.DemiBold
            }

            Label {
                text: "Panel de control · Instituciones educativas del Perú"
                color: EduStyle.Style.mutedTextColor
                font.pixelSize: EduStyle.Style.bodyFontSize
            }
        }

        StyledComboBox {
            id: themeModeSelector
            objectName: "themeModeSelector"
            Layout.alignment: Qt.AlignVCenter
            Layout.preferredWidth: 140
            Accessible.name: "Apariencia de la aplicación"
            model: ["Sistema", "Claro", "Oscuro"]
            currentIndex: !root.controller ? 0
                : root.controller.themeMode === "system" ? 0
                : root.controller.themeMode === "light" ? 1 : 2
            onActivated: index => {
                if (root.controller)
                    root.controller.setThemeMode(["system", "light", "dark"][index])
            }
        }
    }
}
