import QtQuick

/**
 * Módulo de Iconos Descriptivos para EduEscale Desktop
 * Proporciona emojis y funciones para elementos UI
 * Optimizado para renderización correcta sin fuentes especiales
 */
pragma Singleton

QtObject {
    id: icons

    // ============ NAVEGACIÓN (Tabs y Menús) ============
    readonly property string navMap: "🗺"      // Map
    readonly property string navSchools: "🏫"  // Schools
    readonly property string navHistory: "📋"  // History
    readonly property string navFiles: "📁"    // Files
    readonly property string navSettings: "⚙"  // Settings
    readonly property string navHome: "🏠"     // Home
    readonly property string navBack: "←"      // Back (simple arrow)
    readonly property string navExpand: "▼"    // Expand
    readonly property string navCollapse: "▲" // Collapse
    readonly property string navMenu: "≡"      // Menu

    // ============ ESTADOS ============
    readonly property string stateSuccess: "✓"      // Success (checkmark)
    readonly property string statePending: "⧗"      // Pending (hourglass)
    readonly property string stateError: "✗"        // Error (cross)
    readonly property string stateWarning: "⚠"      // Warning
    readonly property string stateInfo: "ℹ"         // Info
    readonly property string stateLoading: "↻"      // Loading (reload)
    readonly property string stateBlocked: "⊗"      // Blocked
    readonly property string statePinned: "📌"      // Pinned
    readonly property string stateFavorite: "★"     // Favorite (star)
    readonly property string stateEmpty: "○"        // Empty

    // ============ ACCIONES ============
    readonly property string actionAdd: "+"         // Add
    readonly property string actionEdit: "✎"        // Edit (pencil)
    readonly property string actionDelete: "−"      // Delete
    readonly property string actionSave: "⬇"        // Save
    readonly property string actionImport: "⬇"      // Import (down arrow)
    readonly property string actionExport: "⬆"      // Export (up arrow)
    readonly property string actionRefresh: "↻"     // Refresh
    readonly property string actionSearch: "⌕"      // Search
    readonly property string actionCopy: "⎘"        // Copy
    readonly property string actionShare: "↗"       // Share
    readonly property string actionOpen: "⬈"        // Open
    readonly property string actionMore: "…"        // More
    readonly property string actionDownload: "⬇"   // Download
    readonly property string actionUpload: "⬆"     // Upload

    // ============ BÚSQUEDA Y FILTROS ============
    readonly property string filterSearch: "⌕"      // Search
    readonly property string filterAdvanced: "≡"     // Advanced
    readonly property string filterDepartment: "⬜"  // Department
    readonly property string filterProvince: "▭"     // Province
    readonly property string filterDistrict: "▪"     // District
    readonly property string filterManagement: "⊙"   // Management
    readonly property string filterLevel: "▤"        // Level
    readonly property string filterClear: "✗"        // Clear
    readonly property string filterSave: "⬇"         // Save
    readonly property string filterSaved: "▸"        // Saved

    // ============ UBICACIÓN Y MAPAS ============
    readonly property string mapLocation: "⊙"        // Location
    readonly property string mapMyLocation: "⊕"      // My Location
    readonly property string mapRoute: "→"            // Route
    readonly property string mapCar: "🚗"             // Car (emoji works well)
    readonly property string mapDistance: "⌐"        // Distance
    readonly property string mapTime: "⏱"            // Time
    readonly property string mapZoomIn: "+"           // Zoom in
    readonly property string mapZoomOut: "−"          // Zoom out
    readonly property string mapCompass: "⬆"         // Compass
    readonly property string mapDownload: "⬇"        // Download

    // ============ INFORMACIÓN ESCOLAR ============
    readonly property string schoolName: "🏫"        // School (emoji works well)
    readonly property string schoolAddress: "⊙"      // Address
    readonly property string schoolLevel: "▤"        // Level
    readonly property string schoolManagement: "⊙"   // Management
    readonly property string schoolPhone: "☎"        // Phone
    readonly property string schoolEmail: "✉"        // Email
    readonly property string schoolWebsite: "⌘"      // Website
    readonly property string schoolCode: "#"         // Code
    readonly property string schoolInfo: "ℹ"         // Info

    // ============ VISITAS ============
    readonly property string visitRecorded: "✓"      // Recorded
    readonly property string visitScheduled: "📅"    // Scheduled (emoji)
    readonly property string visitDenied: "✗"        // Denied
    readonly property string visitObservations: "✎"  // Observations
    readonly property string visitReason: "?>"       // Reason
    readonly property string visitHistory: "▤"       // History
    readonly property string visitPending: "⧗"       // Pending
    readonly property string visitRetry: "↻"         // Retry

    // ============ COMENTARIOS Y NOTAS ============
    readonly property string commentAdd: "✎"         // Add comment
    readonly property string commentEdit: "✎"        // Edit
    readonly property string commentDelete: "−"      // Delete
    readonly property string commentSearch: "⌕"      // Search
    readonly property string commentCount: "▤"       // Count

    // ============ RECORDATORIOS Y ALARMAS ============
    readonly property string reminderSet: "🔔"       // Set (emoji works well)
    readonly property string reminderAck: "✓"        // Acknowledge
    readonly property string reminderDelete: "−"     // Delete
    readonly property string reminderNotify: "→"     // Notify
    readonly property string reminderTime: "⏰"      // Time (emoji)
    readonly property string reminderPending: "⧗"   // Pending

    // ============ ARCHIVOS Y DESCARGAS ============
    readonly property string fileExcel: "📊"         // Excel (emoji)
    readonly property string filePdf: "📄"           // PDF (emoji)
    readonly property string fileZip: "📦"           // ZIP (emoji)
    readonly property string fileBackup: "⬇"         // Backup
    readonly property string fileImport: "⬇"         // Import
    readonly property string fileExport: "⬆"         // Export
    readonly property string fileSize: "▤"           // Size
    readonly property string fileDate: "📅"          // Date (emoji)

    // ============ CONFIGURACIÓN ============
    readonly property string settingsGeneral: "⚙"    // General
    readonly property string settingsTheme: "⊙"      // Theme
    readonly property string settingsLanguage: "⌘"   // Language
    readonly property string settingsAbout: "ℹ"      // About
    readonly property string settingsHelp: "?"       // Help
    readonly property string settingsReset: "↻"      // Reset
    readonly property string settingsBackup: "⬇"     // Backup
    readonly property string settingsAdvanced: "⚙"   // Advanced

    // ============ FUNCIONES AUXILIARES ============
    /**
     * Retorna el icono con estilo para etiqueta
     */
    function withIcon(icon: string, text: string): string {
        return icon + " " + text
    }

    /**
     * Retorna el icono con estilo para botón (con padding)
     */
    function buttonIcon(icon: string): string {
        return icon + " "
    }

    /**
     * Mapea descripción a icono
     */
    function getIcon(key: string): string {
        switch(key) {
            // Navegación
            case "map": return navMap
            case "schools": return navSchools
            case "history": return navHistory
            case "files": return navFiles
            case "settings": return navSettings
            
            // Estados
            case "success": return stateSuccess
            case "pending": return statePending
            case "error": return stateError
            case "loading": return stateLoading
            
            // Acciones
            case "add": return actionAdd
            case "edit": return actionEdit
            case "delete": return actionDelete
            case "export": return actionExport
            case "import": return actionImport
            case "refresh": return actionRefresh
            
            // Visitas
            case "visit_recorded": return visitRecorded
            case "visit_scheduled": return visitScheduled
            case "visit_denied": return visitDenied
            
            default: return ""
        }
    }

    /**
     * Retorna descripción de icono para accesibilidad
     */
    function getAccessibleName(key: string): string {
        switch(key) {
            case "map": return "Icono de mapa"
            case "schools": return "Icono de colegios"
            case "history": return "Icono de historial"
            case "files": return "Icono de archivos"
            case "settings": return "Icono de configuración"
            case "success": return "Exitoso"
            case "error": return "Error"
            case "loading": return "Cargando"
            case "add": return "Agregar"
            case "edit": return "Editar"
            case "delete": return "Eliminar"
            case "export": return "Exportar"
            case "import": return "Importar"
            case "visit_recorded": return "Visita registrada"
            case "visit_scheduled": return "Visita programada"
            case "visit_denied": return "Permiso denegado"
            default: return ""
        }
    }

    /**
     * Retorna color para diferentes tipos de iconos basado en tema
     * Formato: retorna nombre de color para usar en coloreado
     */
    function getIconColor(state: string, darkTheme: bool): string {
        let lightColors = {
            "success": "#10B981",      // Verde
            "pending": "#3B82F6",      // Azul
            "error": "#EF4444",        // Rojo
            "warning": "#FBBF24",      // Amarillo
            "info": "#0EA5E9",         // Azul claro
            "muted": "#9CA3AF"         // Gris
        }

        let darkColors = {
            "success": "#34D399",      // Verde claro
            "pending": "#60A5FA",      // Azul claro
            "error": "#F87171",        // Rojo claro
            "warning": "#FCD34D",      // Amarillo claro
            "info": "#38BDF8",         // Azul más claro
            "muted": "#6B7280"         // Gris más claro
        }

        return darkTheme ? darkColors[state] ?? darkColors["muted"] 
                         : lightColors[state] ?? lightColors["muted"]
    }
}
