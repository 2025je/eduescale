import QtQuick
import QtQuick.Controls
import EduEscale.Gui.Styles 1.0 as EduStyle

Dialog {
    padding: EduStyle.Style.dialogPadding

    background: Rectangle {
        radius: EduStyle.Style.cardRadius
        color: EduStyle.Style.surfaceColor
        border.width: 1
        border.color: EduStyle.Style.borderColor
    }
}
