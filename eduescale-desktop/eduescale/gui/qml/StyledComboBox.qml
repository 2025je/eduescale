import QtQuick
import QtQuick.Controls
import "styles" as EduStyle

ComboBox {
    id: control

    implicitHeight: EduStyle.Style.comboHeight
    font.pixelSize: EduStyle.Style.bodyFontSize
    padding: 6
    leftPadding: 9
    rightPadding: 28

    contentItem: Text {
        leftPadding: 9
        rightPadding: control.indicator.width + control.spacing
        text: control.displayText
        font: control.font
        color: control.enabled
            ? EduStyle.Style.textColor
            : EduStyle.Style.disabledTextColor
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }

    background: Rectangle {
        radius: EduStyle.Style.controlRadius
        color: !control.enabled
            ? EduStyle.Style.disabledSurfaceColor
            : control.popup.visible
            ? EduStyle.Style.primarySubtleColor
            : EduStyle.Style.surfaceColor
        border.width: 1
        border.color: !control.enabled
            ? EduStyle.Style.disabledBorderColor
            : control.popup.visible
            ? EduStyle.Style.primaryColor
            : EduStyle.Style.borderColor
    }

    delegate: ItemDelegate {
        id: popupDelegate
        objectName: "comboPopupDelegate"
        width: control.width - EduStyle.Style.comboPopupPadding * 2
        implicitHeight: EduStyle.Style.comboItemHeight
        text: control.textAt(index)
        font: control.font
        highlighted: control.highlightedIndex === index
        hoverEnabled: true

        contentItem: Text {
            leftPadding: 8
            rightPadding: 8
            text: popupDelegate.text
            font: popupDelegate.font
            color: EduStyle.Style.textColor
            verticalAlignment: Text.AlignVCenter
            elide: Text.ElideRight
        }

        background: Rectangle {
            radius: EduStyle.Style.controlRadius - 2
            color: popupDelegate.highlighted
                ? EduStyle.Style.primarySubtleColor
                : popupDelegate.hovered
                ? EduStyle.Style.subtleSurfaceColor
                : "transparent"
        }
    }

    popup: Popup {
        id: comboPopup
        objectName: control.objectName + "Popup"
        y: control.height - 1
        width: control.width
        padding: EduStyle.Style.comboPopupPadding
        implicitHeight: Math.min(
            listView.contentHeight,
            EduStyle.Style.comboPopupMaxHeight
        ) + topPadding + bottomPadding

        contentItem: ListView {
            id: listView
            clip: true
            implicitHeight: contentHeight
            model: comboPopup.visible ? control.delegateModel : null
            currentIndex: control.highlightedIndex
            ScrollIndicator.vertical: ScrollIndicator {}
        }

        background: Rectangle {
            radius: EduStyle.Style.cardRadius
            color: EduStyle.Style.surfaceColor
            border.width: 1
            border.color: EduStyle.Style.borderColor
        }
    }
}
