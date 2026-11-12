pragma Singleton

import QtQuick

QtObject {
    property bool darkTheme: false

    readonly property color canvasColor: darkTheme ? "#111714" : "#F3F3F3"
    readonly property color surfaceColor: darkTheme ? "#1A211D" : "#FFFFFF"
    readonly property color subtleSurfaceColor: darkTheme ? "#222B26" : "#F8F8F8"
    readonly property color tabTrackColor: darkTheme ? "#29332E" : "#EAEEEB"
    readonly property color disabledSurfaceColor: darkTheme ? "#2A332E" : "#E7EAE8"
    readonly property color disabledBorderColor: darkTheme ? "#45514A" : "#C9D0CC"
    readonly property color disabledTextColor: darkTheme ? "#A5B0A9" : "#4D5852"
    readonly property color primaryColor: darkTheme ? "#78D9B2" : "#0F6B4E"
    readonly property color primaryHoverColor: darkTheme ? "#94E4C2" : "#0B5A40"
    readonly property color primaryActiveColor: darkTheme ? "#B1EBD0" : "#094A34"
    readonly property color primarySubtleColor: darkTheme ? "#233A30" : "#E8F2ED"
    readonly property color primarySubtleHoverColor: darkTheme ? "#2D493B" : "#D8E9E0"
    readonly property color primaryBorderColor: darkTheme ? "#476C58" : "#B7D6C5"
    readonly property color primaryEmphasisColor: darkTheme ? "#B9EBD0" : "#174C39"
    readonly property color borderColor: darkTheme ? "#3B4740" : "#D6D6D6"
    readonly property color textColor: darkTheme ? "#F0F3F1" : "#1A1A1A"
    readonly property color mutedTextColor: darkTheme ? "#B3BDB7" : "#616161"
    readonly property color dangerColor: darkTheme ? "#FF8A80" : "#B42318"
    readonly property color dangerSurfaceColor: darkTheme ? "#442622" : "#FCE8E6"
    readonly property color dangerHoverColor: darkTheme ? "#59332D" : "#FFF4F2"
    readonly property color dangerBorderColor: darkTheme ? "#805047" : "#E6B8B3"

    readonly property int appTitleFontSize: 22
    readonly property int sectionTitleFontSize: 18
    readonly property int bodyFontSize: 12
    readonly property int smallFontSize: 11
    readonly property int captionFontSize: 10
    readonly property int cardRadius: 8
    readonly property int controlRadius: 6
    readonly property int comboHeight: 36
    readonly property int comboItemHeight: 36
    readonly property int comboPopupPadding: 4
    readonly property int comboPopupMaxHeight: 320
    readonly property int checkBoxIndicatorSize: 18
    readonly property int textInputHeight: 36
    readonly property int progressTrackHeight: 8
    readonly property int tabButtonHeight: 36
    readonly property int tabIndicatorHeight: 2
    readonly property int tabIndicatorInset: 12
    readonly property int scrollBarWidth: 12
    readonly property int scrollBarThumbWidth: 8
    readonly property int dialogPadding: 16
    readonly property int compactButtonHeight: 32
    readonly property int actionButtonHeight: 40
    readonly property int mapStyleSelectorWidth: 148
    readonly property int mapStyleSelectorHeight: 36
}
