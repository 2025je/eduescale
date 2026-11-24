import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import EduEscale.Gui.Styles 1.0 as EduStyle

Frame {
    id: root
    objectName: "dashboardHeader"

    property var viewModel: null
    signal logsRequested()
    signal historyRequested()

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
            source: "qrc:/eduescale/gui/assets/edutrack-logo.png"
            fillMode: Image.PreserveAspectFit
        }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.minimumWidth: 0
            spacing: 2

            Label {
                objectName: "brandTitle"
                Layout.fillWidth: true
                text: "EduEscale"
                elide: Text.ElideRight
                color: EduStyle.Style.textColor
                font.pixelSize: EduStyle.Style.appTitleFontSize
                font.weight: Font.DemiBold
            }

            Label {
                objectName: "brandSubtitle"
                Layout.fillWidth: true
                text: "Panel de control · Instituciones educativas del Perú"
                elide: Text.ElideRight
                color: EduStyle.Style.mutedTextColor
                font.pixelSize: EduStyle.Style.bodyFontSize
            }
        }

        LogsButton {
            objectName: "openLogsButton"
            Layout.alignment: Qt.AlignVCenter
            onClicked: root.logsRequested()
        }

        StyledButton {
            objectName: "exportHistoryButton"
            iconName: "assignment"
            text: "Historial"
            Accessible.name: "Ver historial de exportaciones"
            Layout.alignment: Qt.AlignVCenter
            onClicked: root.historyRequested()
        }

        StyledComboBox {
            id: themeModeSelector
            objectName: "themeModeSelector"
            Layout.alignment: Qt.AlignVCenter
            Layout.preferredWidth: 140
            Accessible.name: "Apariencia de la aplicación"
            model: ["Sistema", "Claro", "Oscuro"]
            iconNames: ["brightness_auto", "light_mode", "dark_mode"]
            currentIndex: !root.viewModel ? 0
                : root.viewModel.themeMode === "system" ? 0
                : root.viewModel.themeMode === "light" ? 1 : 2
            onActivated: index => {
                if (root.viewModel)
                    root.viewModel.setThemeMode(["system", "light", "dark"][index])
            }
        }
    }
}
