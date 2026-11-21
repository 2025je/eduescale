import QtQuick
import QtQuick.Controls
import "qrc:/Icons.qml" as Icons

StyledButton {
    text: Icons.Icons.navHistory + " Registros"
    Accessible.name: "Ver registros de diagnóstico"
    ToolTip.visible: hovered
    ToolTip.text: "Ver registros de diagnóstico"
}
