package pe.eduescale.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolDataReader
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.filterSchools
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var importButton: Button
    private lateinit var locationButton: Button
    private lateinit var filterButton: Button
    private lateinit var searchField: EditText
    private lateinit var statusView: TextView
    private lateinit var detailsView: TextView
    private lateinit var detailsLocationView: TextView
    private lateinit var emptyDetailsView: TextView
    private lateinit var detailsContent: LinearLayout
    private lateinit var detailsScrollView: ScrollView
    private lateinit var detailsPanel: LinearLayout
    private lateinit var detailsToggleButton: TextView
    private lateinit var closeDetailsButton: TextView
    private lateinit var detailsActions: LinearLayout
    private lateinit var navigationButton: TextView
    private lateinit var shareLocationButton: Button

    private val importExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingFilterUpdate: Runnable? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var schoolFile: SchoolFile? = null
    private var selectedSchool: SchoolRecord? = null
    private var detailsMinimized = false
    private var detailsDismissed = false
    private var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    private var visibleSchools: List<SchoolRecord> = emptyList()
    private var advancedFilters = SchoolFilters()

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importFile(uri)
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableMyLocation()
        } else {
            Toast.makeText(
                this,
                "Permite el acceso a la ubicación para mostrarla en el mapa.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val root = FrameLayout(this)

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val topPanel = createTopPanel()
        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ).apply {
            setMargins(dp(12), dp(12), dp(12), 0)
        }
        root.addView(topPanel, topParams)

        detailsView = TextView(this).apply {
            setTextColor(Color.rgb(28, 38, 43))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        detailsLocationView = TextView(this).apply {
            setTextColor(Color.rgb(101, 116, 108))
            textSize = 13f
            maxLines = 2
            visibility = View.GONE
        }
        emptyDetailsView = TextView(this).apply {
            text = "Toca un marcador para ver la información del colegio."
            setTextColor(Color.rgb(86, 101, 94))
            textSize = 14f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.rgb(243, 247, 244), dp(14))
        }
        navigationButton = TextView(this).apply {
            text = "Cómo llegar"
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setTextColor(Color.rgb(23, 107, 80))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.rgb(235, 244, 238), dp(12))
            visibility = View.GONE
            setOnClickListener {
                selectedSchool?.let(::showNavigationOptions)
            }
        }
        shareLocationButton = Button(this).apply {
            text = "Compartir"
            isAllCaps = false
            textSize = 13f
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(23, 107, 80), dp(12))
            visibility = View.GONE
            setOnClickListener {
                selectedSchool?.let(::shareSchoolLocation)
            }
        }
        detailsActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                navigationButton,
                LinearLayout.LayoutParams(0, dp(48), 1f),
            )
            addView(
                shareLocationButton,
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f,
                ).apply {
                    marginStart = dp(8)
                },
            )
            visibility = View.GONE
        }
        detailsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        detailsScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(
                detailsContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            visibility = View.GONE
        }
        val detailsScrollHeight = (
            resources.displayMetrics.heightPixels /
                resources.displayMetrics.density * 0.34f
            ).toInt().coerceIn(170, 300)
        val detailsEyebrow = TextView(this).apply {
            text = "FICHA DEL COLEGIO"
            textSize = 10f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(23, 107, 80))
        }
        detailsToggleButton = TextView(this).apply {
            text = "−"
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = "Minimizar ficha"
            setTextColor(Color.rgb(54, 64, 69))
            background = roundedBackground(Color.rgb(243, 247, 244), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                detailsMinimized = !detailsMinimized
                updateDetailsPanelState()
            }
        }
        closeDetailsButton = TextView(this).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = "Cerrar ficha del colegio"
            setTextColor(Color.rgb(86, 101, 94))
            background = roundedBackground(Color.rgb(243, 247, 244), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedSchool = null
                detailsMinimized = false
                detailsDismissed = true
                updateDetailsPanelState()
            }
        }
        val detailsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                detailsEyebrow,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                detailsToggleButton,
                LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginStart = dp(6)
                },
            )
            addView(
                closeDetailsButton,
                LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginStart = dp(6)
                },
            )
        }
        detailsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(14))
            visibility = View.GONE
            val handle = View(this@MainActivity).apply {
                background = roundedBackground(Color.rgb(210, 220, 214), dp(4))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(
                handle,
                LinearLayout.LayoutParams(dp(34), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(12)
                },
            )
            addView(
                detailsHeader,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(6)
                },
            )
            addView(
                detailsView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(3)
                },
            )
            addView(
                detailsLocationView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(8)
                },
            )
            addView(
                emptyDetailsView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(10)
                },
            )
            addView(
                detailsScrollView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(detailsScrollHeight),
                ).apply {
                    bottomMargin = dp(8)
                },
            )
            addView(
                detailsActions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val detailsParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply {
            setMargins(dp(12), 0, dp(12), dp(42))
        }
        root.addView(detailsPanel, detailsParams)

        val attribution = TextView(this).apply {
            text = "© OpenStreetMap contributors"
            textSize = 11f
            setTextColor(Color.rgb(39, 49, 53))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = panelBackground()
        }
        val attributionParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            setMargins(dp(8), 0, dp(8), dp(6))
        }
        root.addView(attribution, attributionParams)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topParams.topMargin = dp(12) + systemBars.top
            detailsParams.bottomMargin = dp(42) + systemBars.bottom
            attributionParams.bottomMargin = dp(6) + systemBars.bottom
            root.requestLayout()
            insets
        }

        setContentView(root)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        mapView.getMapAsync(::configureMap)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let(::importFile)
        }
    }

    private fun createTopPanel(): View {
        val brandMark = ImageView(this).apply {
            setImageResource(R.drawable.edutrack_logo)
            contentDescription = "Logo de Edutrack"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@MainActivity).apply {
                    text = "EduEscale"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(28, 38, 43))
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "Instituciones educativas del Perú"
                    textSize = 11f
                    setTextColor(Color.rgb(100, 112, 107))
                },
            )
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(brandMark, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(
                titleBlock,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(10)
                },
            )
        }

        importButton = Button(this).apply {
            text = "Abrir archivo"
            isAllCaps = false
            setTextColor(Color.parseColor(PUBLIC_MARKER_COLOR))
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(215, 227, 220))
            }
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                openDocument.launch(arrayOf("*/*"))
            }
        }

        statusView = TextView(this).apply {
            text = "Importa un archivo .eduescale para comenzar."
            textSize = 12f
            setTextColor(Color.rgb(100, 112, 107))
            maxLines = 2
        }

        searchField = EditText(this).apply {
            hint = "Buscar colegio por nombre"
            textSize = 13f
            setTextColor(Color.rgb(28, 38, 43))
            setHintTextColor(Color.rgb(126, 137, 132))
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            gravity = Gravity.CENTER_VERTICAL
            background = searchFieldBackground()
            setPadding(dp(12), 0, dp(12), 0)
            isEnabled = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(text: Editable?) {
                    scheduleFilterUpdate()
                }
            })
        }
        filterButton = Button(this).apply {
            text = "Filtros"
            contentDescription = "Abrir filtros avanzados"
            isAllCaps = false
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(PUBLIC_MARKER_COLOR))
            background = GradientDrawable().apply {
                setColor(Color.rgb(235, 244, 238))
                cornerRadius = dp(12).toFloat()
            }
            isEnabled = false
            setOnClickListener { showAdvancedFilters() }
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                searchField,
                LinearLayout.LayoutParams(0, dp(48), 1f),
            )
            addView(
                filterButton,
                LinearLayout.LayoutParams(dp(88), dp(48)).apply {
                    marginStart = dp(8)
                },
            )
        }

        locationButton = Button(this).apply {
            text = "◎  Mi ubicación"
            contentDescription = "Mostrar y centrar mi ubicación"
            isAllCaps = false
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(PUBLIC_MARKER_COLOR))
                cornerRadius = dp(14).toFloat()
            }
            elevation = dp(3).toFloat()
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { showMyLocation() }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                importButton,
                LinearLayout.LayoutParams(0, dp(50), 1f),
            )
            addView(
                locationButton,
                LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                    marginStart = dp(8)
                },
            )
        }
        val markerLegend = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(245, 248, 246))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), dp(5), dp(6), dp(5))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(createLegendCaption("COLOR"))
                    addView(createLegendItem("Pública", PUBLIC_MARKER_COLOR, 12))
                    addView(createLegendItem("Particular", PRIVATE_MARKER_COLOR, 12))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(createLegendCaption("TAMAÑO"))
                    addView(createLegendItem("Inicial", "#364045", 7))
                    addView(createLegendItem("Primaria", "#364045", 10))
                    addView(createLegendItem("Secundaria", "#364045", 13))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelBackground()
            elevation = dp(6).toFloat()
            addView(
                header,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                statusView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(8)
                },
            )
            addView(
                searchRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(8)
                },
            )
            addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                markerLegend,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    private fun configureMap(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        mapLibreMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(-9.19, -75.015))
            .zoom(4.0)
            .build()
        mapLibreMap.addOnMapClickListener { coordinate ->
            val screenPoint = mapLibreMap.projection.toScreenLocation(coordinate)
            val feature = mapLibreMap.queryRenderedFeatures(screenPoint, SCHOOL_LAYER)
                .firstOrNull()
            val schoolId = feature?.getStringProperty(SCHOOL_ID_PROPERTY)?.toIntOrNull()
            val school = schoolId?.let(schoolsById::get)
            if (school == null) {
                false
            } else {
                showSchoolDetails(school)
                true
            }
        }
        mapLibreMap.setStyle(Style.Builder().fromJson(OSM_STYLE)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            if (hasLocationPermission()) {
                enableMyLocation()
            }
        }
    }

    private fun showSchoolDetails(school: SchoolRecord) {
        val selectionChanged = selectedSchool?.id != school.id
        val panelWasVisible = detailsPanel.visibility == View.VISIBLE

        selectedSchool = school
        detailsDismissed = false
        detailsMinimized = false
        renderSchoolDetails(school)
        detailsView.text = school.nombre?.takeIf(String::isNotBlank) ?: "Colegio sin nombre"
        detailsLocationView.text = listOf(
            school.distrito,
            school.provincia,
            school.departamento,
        ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
        updateDetailsPanelState()
        detailsPanel.animate().cancel()

        if (!panelWasVisible) {
            detailsPanel.alpha = 0f
            detailsPanel.translationY = dp(12).toFloat()
            detailsPanel.visibility = View.VISIBLE
            detailsPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else if (selectionChanged) {
            detailsPanel.alpha = 0.55f
            detailsPanel.scaleX = 0.98f
            detailsPanel.scaleY = 0.98f
            detailsPanel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            detailsPanel.alpha = 1f
            detailsPanel.translationY = 0f
            detailsPanel.scaleX = 1f
            detailsPanel.scaleY = 1f
        }
    }

    private fun showMyLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
            return
        }
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (!hasLocationPermission()) {
            return
        }
        val currentMap = map ?: return
        val currentStyle = style ?: return

        try {
            val locationComponent = currentMap.locationComponent
            if (!locationComponent.isLocationComponentActivated) {
                val locationOptions = LocationComponentOptions.builder(this)
                    .pulseEnabled(true)
                    .pulseColor(Color.rgb(24, 132, 255))
                    .foregroundTintColor(Color.rgb(12, 72, 160))
                    .backgroundTintColor(Color.WHITE)
                    .build()
                locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, currentStyle)
                        .locationComponentOptions(locationOptions)
                        .build(),
                )
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.NORMAL
            locationComponent.cameraMode = CameraMode.TRACKING
        } catch (error: SecurityException) {
            Toast.makeText(
                this,
                "Android no permitió acceder a la ubicación.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun importFile(uri: Uri) {
        importButton.isEnabled = false
        statusView.text = "Leyendo archivo..."
        importExecutor.execute {
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo abrir el archivo seleccionado.")
                val parsed = input.use { stream ->
                    SchoolDataReader.read(stream)
                }
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        return@post
                    }
                    importButton.isEnabled = true
                    schoolFile = parsed
                    advancedFilters = SchoolFilters()
                    searchField.setText("")
                    cancelPendingFilterUpdate()
                    visibleSchools = parsed.schools
                    selectedSchool = null
                    schoolsById = parsed.schools.associateBy(SchoolRecord::id)
                    searchField.isEnabled = parsed.schools.isNotEmpty()
                    filterButton.isEnabled = parsed.schools.isNotEmpty()
                    updateFilterButtonText()
                    navigationButton.visibility = View.GONE
                    shareLocationButton.visibility = View.GONE
                    statusView.text = buildStatus(parsed)
                    detailsView.text = if (parsed.schools.isEmpty()) {
                        "El archivo se leyó, pero no contiene colegios con coordenadas válidas."
                    } else {
                        "Toca un marcador para ver la información del colegio."
                    }
                    detailsPanel.visibility = View.VISIBLE
                    updateMapData()
                }
            } catch (error: IOException) {
                showImportError(error.message ?: "No se pudo leer el archivo.")
            } catch (error: IllegalArgumentException) {
                showImportError(error.message ?: "El archivo no es válido.")
            } catch (error: SecurityException) {
                showImportError("Android no permitió leer el archivo seleccionado.")
            }
        }
    }

    private fun showImportError(message: String) {
        mainHandler.post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            importButton.isEnabled = true
            selectedSchool = null
            navigationButton.visibility = View.GONE
            shareLocationButton.visibility = View.GONE
            statusView.text = "No se pudo importar el archivo"
            detailsView.text = message
            detailsPanel.visibility = View.VISIBLE
        }
    }

    private fun buildStatus(data: SchoolFile): String =
        "${data.totalCount} colegios · ${data.schools.size} en el mapa · " +
            "${data.withoutCoordinates} sin coordenadas"

    private fun scheduleFilterUpdate() {
        cancelPendingFilterUpdate()
        val update = Runnable {
            pendingFilterUpdate = null
            applyFilters()
        }
        pendingFilterUpdate = update
        mainHandler.postDelayed(update, FILTER_UPDATE_DELAY_MS)
    }

    private fun cancelPendingFilterUpdate() {
        pendingFilterUpdate?.let(mainHandler::removeCallbacks)
        pendingFilterUpdate = null
    }

    private fun applyFilters() {
        val data = schoolFile ?: return
        visibleSchools = filterSchools(
            data.schools,
            searchField.text.toString(),
            advancedFilters,
        )
        updateFilterButtonText()
        statusView.text = if (
            searchField.text.isBlank() &&
            advancedFilterCount() == 0
        ) {
            buildStatus(data)
        } else {
            "${visibleSchools.size} resultados · ${data.schools.size} colegios con ubicación"
        }

        if (
            selectedSchool != null &&
            visibleSchools.none { it.id == selectedSchool?.id }
        ) {
            selectedSchool = null
            detailsMinimized = false
            detailsPanel.animate().cancel()
            detailsPanel.alpha = 1f
            detailsPanel.translationY = 0f
            detailsPanel.scaleX = 1f
            detailsPanel.scaleY = 1f
        }
        if (selectedSchool == null) {
            emptyDetailsView.text = if (visibleSchools.isEmpty()) {
                "No se encontraron colegios con esa búsqueda y esos filtros."
            } else {
                "Toca un marcador para ver la información del colegio."
            }
        }
        updateDetailsPanelState()
        updateMapData()
    }

    private fun updateDetailsPanelState() {
        val hasSelection = selectedSchool != null
        val showDetails = hasSelection && !detailsMinimized

        detailsToggleButton.visibility = if (hasSelection) View.VISIBLE else View.GONE
        detailsToggleButton.text = if (detailsMinimized) "＋" else "−"
        detailsToggleButton.contentDescription =
            if (detailsMinimized) "Expandir ficha" else "Minimizar ficha"
        closeDetailsButton.visibility = if (hasSelection) View.VISIBLE else View.GONE
        detailsView.visibility = if (hasSelection) View.VISIBLE else View.GONE
        detailsLocationView.visibility =
            if (showDetails && !detailsLocationView.text.isNullOrBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        emptyDetailsView.visibility =
            if (!hasSelection && !detailsDismissed) View.VISIBLE else View.GONE
        detailsScrollView.visibility = if (showDetails) View.VISIBLE else View.GONE
        detailsActions.visibility = if (showDetails) View.VISIBLE else View.GONE
        navigationButton.visibility = if (showDetails) View.VISIBLE else View.GONE
        shareLocationButton.visibility = if (showDetails) View.VISIBLE else View.GONE
        detailsPanel.visibility = if (detailsDismissed) View.GONE else View.VISIBLE
    }

    private fun advancedFilterCount(): Int =
        listOf(
            advancedFilters.departamento,
            advancedFilters.provincia,
            advancedFilters.distrito,
        ).count { it != null } +
            advancedFilters.gestiones.size +
            advancedFilters.niveles.size

    private fun updateFilterButtonText() {
        val count = advancedFilterCount()
        filterButton.text = if (count == 0) "Filtros" else "Filtros ($count)"
    }

    private fun showAdvancedFilters() {
        val schools = schoolFile?.schools ?: return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        val scrollView = ScrollView(this).apply {
            addView(content)
        }
        val departments = schools.mapNotNull(SchoolRecord::departamento).distinct().sorted()
        val departmentSpinner = addFilterSpinner(
            content,
            "Departamento",
            ALL_DEPARTMENTS,
            departments,
            advancedFilters.departamento,
        )
        val initialDepartment = selectedFilterValue(departmentSpinner, ALL_DEPARTMENTS)
        val provinces = provinceOptions(schools, initialDepartment)
        val initialProvince = advancedFilters.provincia?.takeIf(provinces::contains)
        val provinceSpinner = addFilterSpinner(
            content,
            "Provincia",
            ALL_PROVINCES,
            provinces,
            initialProvince,
        )
        val districts = districtOptions(schools, initialDepartment, initialProvince)
        val initialDistrict = advancedFilters.distrito?.takeIf(districts::contains)
        val districtSpinner = addFilterSpinner(
            content,
            "Distrito",
            ALL_DISTRICTS,
            districts,
            initialDistrict,
        )

        var currentDepartment = initialDepartment
        var currentProvince = initialProvince
        departmentSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val department = selectedFilterValue(
                        departmentSpinner,
                        ALL_DEPARTMENTS,
                    )
                    if (department == currentDepartment) {
                        return
                    }
                    currentDepartment = department
                    currentProvince = null
                    val availableProvinces = provinceOptions(schools, department)
                    setSpinnerOptions(
                        provinceSpinner,
                        ALL_PROVINCES,
                        availableProvinces,
                        null,
                    )
                    setSpinnerOptions(
                        districtSpinner,
                        ALL_DISTRICTS,
                        districtOptions(schools, department, null),
                        null,
                    )
                }
            }
        provinceSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val province = selectedFilterValue(provinceSpinner, ALL_PROVINCES)
                    if (province == currentProvince) {
                        return
                    }
                    currentProvince = province
                    setSpinnerOptions(
                        districtSpinner,
                        ALL_DISTRICTS,
                        districtOptions(
                            schools,
                            selectedFilterValue(departmentSpinner, ALL_DEPARTMENTS),
                            province,
                        ),
                        null,
                    )
                }
            }

        val hint = TextView(this).apply {
            text = "Combina ubicación, gestión y nivel. Si no marcas opciones, se incluyen todos."
            textSize = 12f
            setTextColor(Color.rgb(100, 112, 107))
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(hint)
        val managementLabel = createDialogSectionLabel("Gestión")
        content.addView(managementLabel)
        val publicCheckBox = CheckBox(this).apply {
            text = "Pública"
            isChecked = "publica" in advancedFilters.gestiones
        }
        val privateCheckBox = CheckBox(this).apply {
            text = "Particular"
            isChecked = "privada" in advancedFilters.gestiones
        }
        content.addView(publicCheckBox)
        content.addView(privateCheckBox)

        val levelLabel = createDialogSectionLabel("Nivel educativo")
        content.addView(levelLabel)
        val initialCheckBox = createLevelCheckBox(
            "Inicial",
            "inicial" in advancedFilters.niveles,
        )
        val primaryCheckBox = createLevelCheckBox(
            "Primaria",
            "primaria" in advancedFilters.niveles,
        )
        val secondaryCheckBox = createLevelCheckBox(
            "Secundaria",
            "secundaria" in advancedFilters.niveles,
        )
        content.addView(initialCheckBox)
        content.addView(primaryCheckBox)
        content.addView(secondaryCheckBox)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Filtros avanzados")
            .setView(scrollView)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Limpiar", null)
            .setPositiveButton("Aplicar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                advancedFilters = SchoolFilters(
                    departamento = selectedFilterValue(
                        departmentSpinner,
                        ALL_DEPARTMENTS,
                    ),
                    provincia = selectedFilterValue(provinceSpinner, ALL_PROVINCES),
                    distrito = selectedFilterValue(districtSpinner, ALL_DISTRICTS),
                    gestiones = buildSet {
                        if (publicCheckBox.isChecked) add("publica")
                        if (privateCheckBox.isChecked) add("privada")
                    },
                    niveles = buildSet {
                        if (initialCheckBox.isChecked) add("inicial")
                        if (primaryCheckBox.isChecked) add("primaria")
                        if (secondaryCheckBox.isChecked) add("secundaria")
                    },
                )
                applyFilters()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                advancedFilters = SchoolFilters()
                applyFilters()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addFilterSpinner(
        parent: LinearLayout,
        label: String,
        placeholder: String,
        options: List<String>,
        selected: String?,
    ): Spinner {
        parent.addView(createDialogSectionLabel(label))
        val spinner = Spinner(this)
        setSpinnerOptions(spinner, placeholder, options, selected)
        parent.addView(
            spinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ),
        )
        return spinner
    }

    private fun setSpinnerOptions(
        spinner: Spinner,
        placeholder: String,
        options: List<String>,
        selected: String?,
    ) {
        val values = listOf(placeholder) + options
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selectedPosition = selected?.let(values::indexOf)?.takeIf { it > 0 } ?: 0
        spinner.setSelection(selectedPosition, false)
    }

    private fun selectedFilterValue(spinner: Spinner, placeholder: String): String? =
        (spinner.selectedItem as? String)?.takeIf { it != placeholder }

    private fun provinceOptions(
        schools: List<SchoolRecord>,
        department: String?,
    ): List<String> =
        schools.asSequence()
            .filter { department == null || it.departamento == department }
            .mapNotNull(SchoolRecord::provincia)
            .distinct()
            .sorted()
            .toList()

    private fun districtOptions(
        schools: List<SchoolRecord>,
        department: String?,
        province: String?,
    ): List<String> =
        schools.asSequence()
            .filter { department == null || it.departamento == department }
            .filter { province == null || it.provincia == province }
            .mapNotNull(SchoolRecord::distrito)
            .distinct()
            .sorted()
            .toList()

    private fun createDialogSectionLabel(label: String): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(28, 38, 43))
            setPadding(0, dp(10), 0, dp(2))
        }

    private fun createLevelCheckBox(label: String, checked: Boolean): CheckBox =
        CheckBox(this).apply {
            text = label
            isChecked = checked
        }

    private fun updateMapData() {
        val currentMap = map ?: return
        val currentStyle = style ?: return
        if (schoolFile == null) {
            return
        }

        currentStyle.getLayer(SCHOOL_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getSource(SCHOOL_SOURCE)?.let(currentStyle::removeSource)

        if (visibleSchools.isEmpty()) {
            return
        }

        val features = visibleSchools.map { school ->
            Feature.fromGeometry(Point.fromLngLat(school.longitud, school.latitud)).apply {
                addStringProperty(SCHOOL_ID_PROPERTY, school.id.toString())
                addStringProperty(MARKER_COLOR_PROPERTY, markerColor(school.gestion))
                addNumberProperty(MARKER_RADIUS_PROPERTY, markerRadius(school.niveles))
            }
        }
        currentStyle.addSource(
            GeoJsonSource(
                SCHOOL_SOURCE,
                FeatureCollection.fromFeatures(features),
            ),
        )
        currentStyle.addLayer(
            CircleLayer(SCHOOL_LAYER, SCHOOL_SOURCE).withProperties(
                circleColor(Expression.get(MARKER_COLOR_PROPERTY)),
                circleRadius(Expression.get(MARKER_RADIUS_PROPERTY)),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2f),
            ),
        )
        focusSchools(currentMap, visibleSchools)
    }

    private fun focusSchools(map: MapLibreMap, schools: List<SchoolRecord>) {
        if (schools.isEmpty()) {
            return
        }
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        schools.forEach { school ->
            val position = LatLng(school.latitud, school.longitud)
            bounds.include(position)
            minLatitude = minOf(minLatitude, school.latitud)
            maxLatitude = maxOf(maxLatitude, school.latitud)
            minLongitude = minOf(minLongitude, school.longitud)
            maxLongitude = maxOf(maxLongitude, school.longitud)
        }

        val samePoint = maxLatitude - minLatitude < MIN_COORDINATE_SPAN &&
            maxLongitude - minLongitude < MIN_COORDINATE_SPAN
        if (schools.size == 1 || samePoint) {
            val school = schools.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(school.latitud, school.longitud),
                    SINGLE_POINT_ZOOM,
                ),
            )
        } else {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(56)),
            )
        }
    }

    private fun markerColor(gestion: String?): String =
        when (gestion) {
            "publica" -> PUBLIC_MARKER_COLOR
            "privada" -> PRIVATE_MARKER_COLOR
            else -> UNKNOWN_MARKER_COLOR
        }

    private fun markerRadius(niveles: List<String>): Double =
        when {
            "secundaria" in niveles -> SECONDARY_MARKER_RADIUS
            "primaria" in niveles -> PRIMARY_MARKER_RADIUS
            "inicial" in niveles -> INITIAL_MARKER_RADIUS
            else -> UNKNOWN_MARKER_RADIUS
        }

    private fun renderSchoolDetails(school: SchoolRecord) {
        detailsContent.removeAllViews()

        val management = school.gestion
        val managementLabel = when (management) {
            "publica" -> "Gestión pública"
            "privada" -> "Gestión particular"
            null -> "Gestión no disponible"
            else -> management
        }
        val managementColors = when (management) {
            "publica" -> Color.rgb(232, 244, 237) to Color.rgb(23, 107, 80)
            "privada" -> Color.rgb(255, 242, 223) to Color.rgb(154, 91, 0)
            else -> Color.rgb(239, 243, 241) to Color.rgb(86, 101, 94)
        }
        detailsContent.addView(
            createDetailsBadge(
                managementLabel,
                managementColors.first,
                managementColors.second,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            },
        )

        detailsContent.addView(createDetailsSectionLabel("NIVELES EDUCATIVOS"))
        if (school.niveles.isEmpty()) {
            detailsContent.addView(
                TextView(this).apply {
                    text = "No informado en este archivo. Reexporta desde EduEscale Desktop actualizado para incluirlo."
                    textSize = 12f
                    setTextColor(Color.rgb(86, 101, 94))
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = roundedBackground(Color.rgb(243, 247, 244), dp(10))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(14)
                },
            )
        } else {
            val levelBadges = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            school.niveles.forEach { level ->
                val label = when (level) {
                    "inicial" -> "Inicial"
                    "primaria" -> "Primaria"
                    "secundaria" -> "Secundaria"
                    else -> level.replaceFirstChar(Char::uppercase)
                }
                levelBadges.addView(
                    createDetailsBadge(
                        label,
                        Color.rgb(239, 243, 241),
                        Color.rgb(54, 64, 69),
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd = dp(6)
                    },
                )
            }
            detailsContent.addView(
                levelBadges,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(14)
                },
            )
        }

        val statistics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                createStatisticCard("Alumnos", school.alumnos),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                },
            )
            addView(
                createStatisticCard("Secciones", school.secciones),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        detailsContent.addView(
            statistics,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            },
        )

        addDetailRow("Dirección", school.direccion)
        addDetailRow("Localidad", school.localidad)
        addDetailRow("Centro poblado (CP)", school.cp)
        addDetailRow("Turno", school.turno)
    }

    private fun createDetailsSectionLabel(label: String): TextView =
        TextView(this).apply {
            text = label
            textSize = 10f
            letterSpacing = 0.06f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(101, 116, 108))
            setPadding(0, 0, 0, dp(7))
        }

    private fun createDetailsBadge(
        label: String,
        backgroundColor: Int,
        textColor: Int,
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBackground(backgroundColor, dp(20))
        }

    private fun createStatisticCard(label: String, value: String?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.rgb(243, 247, 244), dp(14))
            addView(
                TextView(this@MainActivity).apply {
                    text = value?.takeIf(String::isNotBlank) ?: "—"
                    textSize = 21f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(28, 73, 55))
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = label.uppercase()
                    textSize = 9f
                    letterSpacing = 0.06f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(101, 116, 108))
                },
            )
        }

    private fun addDetailRow(label: String, value: String?) {
        val detail = value?.takeIf(String::isNotBlank) ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(
                TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    setTextColor(Color.rgb(101, 116, 108))
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = detail
                    textSize = 14f
                    setTextColor(Color.rgb(28, 38, 43))
                },
            )
        }
        detailsContent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        detailsContent.addView(
            View(this).apply {
                setBackgroundColor(Color.rgb(232, 238, 234))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            ),
        )
    }

    private fun showNavigationOptions(school: SchoolRecord) {
        AlertDialog.Builder(this)
            .setTitle("Cómo llegar en auto")
            .setItems(arrayOf("Google Maps", "Waze")) { _, choice ->
                val url = when (choice) {
                    0 -> googleMapsDirectionsUrl(school)
                    1 -> wazeDirectionsUrl(school)
                    else -> return@setItems
                }
                openNavigationUrl(url)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openNavigationUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No se encontró una aplicación o navegador para abrir la ruta.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun shareSchoolLocation(school: SchoolRecord) {
        val schoolName = school.nombre?.takeIf(String::isNotBlank) ?: "Ubicación del colegio"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, schoolName)
            putExtra(
                Intent.EXTRA_TEXT,
                "$schoolName\n${googleMapsUrl(school)}",
            )
        }
        startActivity(Intent.createChooser(sendIntent, "Compartir ubicación"))
    }

    private fun googleMapsUrl(school: SchoolRecord): String {
        val coordinates = Uri.encode("${school.latitud},${school.longitud}")
        return "https://www.google.com/maps/search/?api=1&query=$coordinates"
    }

    private fun googleMapsDirectionsUrl(school: SchoolRecord): String =
        Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter(
                "destination",
                "${school.latitud},${school.longitud}",
            )
            .appendQueryParameter("travelmode", "driving")
            .build()
            .toString()

    private fun wazeDirectionsUrl(school: SchoolRecord): String =
        Uri.parse("https://waze.com/ul").buildUpon()
            .appendQueryParameter("ll", "${school.latitud},${school.longitud}")
            .appendQueryParameter("navigate", "yes")
            .build()
            .toString()

    private fun panelBackground(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(232, 238, 234))
        }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun searchFieldBackground(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.rgb(245, 248, 246))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(232, 238, 234))
        }

    private fun createLegendCaption(label: String): TextView =
        TextView(this).apply {
            text = label
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
            setTextColor(Color.rgb(116, 128, 122))
            setPadding(0, 0, dp(8), 0)
        }

    private fun createLegendItem(label: String, color: String, sizeDp: Int): View {
        val marker = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.rgb(54, 64, 69))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), dp(12), dp(2))
            addView(
                marker,
                LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)),
            )
            addView(
                labelView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(5)
                },
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cancelPendingFilterUpdate()
        importExecutor.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val SCHOOL_SOURCE = "eduescale-schools"
        const val SCHOOL_LAYER = "eduescale-school-markers"
        const val SCHOOL_ID_PROPERTY = "school_id"
        const val MARKER_COLOR_PROPERTY = "marker_color"
        const val MARKER_RADIUS_PROPERTY = "marker_radius"
        const val PUBLIC_MARKER_COLOR = "#176B50"
        const val PRIVATE_MARKER_COLOR = "#D97706"
        const val UNKNOWN_MARKER_COLOR = "#7B8794"
        const val INITIAL_MARKER_RADIUS = 6.0
        const val PRIMARY_MARKER_RADIUS = 8.0
        const val SECONDARY_MARKER_RADIUS = 10.0
        const val UNKNOWN_MARKER_RADIUS = 7.0
        const val FILTER_UPDATE_DELAY_MS = 250L
        const val ALL_DEPARTMENTS = "Todos los departamentos"
        const val ALL_PROVINCES = "Todas las provincias"
        const val ALL_DISTRICTS = "Todos los distritos"
        const val SINGLE_POINT_ZOOM = 12.0
        const val MIN_COORDINATE_SPAN = 0.00001
        const val OSM_STYLE = """
            {
              "version": 8,
              "sources": {
                "openstreetmap": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": 19,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                {
                  "id": "openstreetmap-raster",
                  "type": "raster",
                  "source": "openstreetmap",
                  "minzoom": 0,
                  "maxzoom": 19
                }
              ]
            }
        """
    }
}
