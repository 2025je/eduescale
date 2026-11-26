import QtQuick

Text {
    property string symbolName: ""
    property color symbolColor: "white"
    property real symbolSize: 20

    function glyphFor(name) {
        switch ((name || "").toLowerCase()) {
        case "history": return "\uE0AF"
        case "search": return "\uE2C8"
        case "database": return "\uE2C7"
        case "link": return "\uE6B8"
        case "sync": return "\uE627"
        case "add": return "\uE145"
        case "info": return "\uE88E"
        case "hourglass_top": return "\uE88B"
        case "brightness_auto": return "\uE627"
        case "light_mode": return "\uE518"
        case "dark_mode": return "\uE51C"
        case "assignment": return "\uE85D"
        case "cancel": return "\uE5CD"
        case "close": return "\uE5CD"
        case "table_chart": return "\uE265"
        case "picture_as_pdf": return "\uE24D"
        case "archive": return "\uE149"
        case "school": return "\uEA19"
        case "groups": return "\uF233"
        case "view_list": return "\uE14D"
        case "schedule": return "\uE0AF"
        case "location_on": return "\uE0C8"
        case "place": return "\uE0C8"
        case "menu_book": return "\uEA19"
        default: return ""
        }
    }

    text: glyphFor(symbolName)
    textFormat: Text.PlainText
    renderType: Text.NativeRendering
    font.family: "Material Symbols Outlined"
    font.pixelSize: symbolSize
    font.weight: Font.Normal
    font.capitalization: Font.MixedCase
    font.preferShaping: true
    font.features: { "liga": 1, "clig": 1, "dlig": 1 }
    font.letterSpacing: 0
    font.wordSpacing: 0
    color: symbolColor
    horizontalAlignment: Text.AlignHCenter
    verticalAlignment: Text.AlignVCenter
}
