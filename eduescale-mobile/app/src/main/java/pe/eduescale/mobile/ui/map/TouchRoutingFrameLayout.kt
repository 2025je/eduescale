package pe.eduescale.mobile.ui.map

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.FrameLayout
import org.maplibre.android.maps.MapView

internal class TouchRoutingFrameLayout(
    context: Context,
    private val mapView: MapView,
    private val bottomSystemInsetPx: () -> Int,
) : FrameLayout(context) {
    var mapTouchEnabled = false
    var mapTopOverlayBottomPx = 0f
    val mapDownloadControlBounds = RectF()
    private var forwardingMapTouch = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            forwardingMapTouch = shouldForwardMapTouch(event)
        }
        if (!forwardingMapTouch) {
            return super.dispatchTouchEvent(event)
        }

        mapView.dispatchTouchEvent(event)
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            forwardingMapTouch = false
        }
        return true
    }

    private fun shouldForwardMapTouch(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val yDp = event.y / density
        val xDp = event.x / density
        val widthDp = width / density
        val heightDp = height / density
        val startsInMap = event.y >= mapTopOverlayBottomPx &&
            yDp <= heightDp - MAP_TOUCH_BOTTOM_EXCLUSION_DP
        val bottomInsetDp = bottomSystemInsetPx() / density
        val locationButtonBottom = heightDp -
            MAP_NAVIGATION_BAR_HEIGHT_DP -
            bottomInsetDp -
            MAP_LOCATION_BUTTON_BOTTOM_MARGIN_DP
        val locationButtonTop = locationButtonBottom - MAP_LOCATION_BUTTON_SIZE_DP
        val touchesLocationButtonVertically =
            yDp >= locationButtonTop - MAP_CONTROL_TOUCH_SLOP_DP &&
                yDp <= locationButtonBottom + MAP_CONTROL_TOUCH_SLOP_DP
        val startsOnLocationButton =
            xDp >= widthDp - MAP_LOCATION_BUTTON_RIGHT_MARGIN_DP -
                MAP_LOCATION_BUTTON_SIZE_DP - MAP_CONTROL_TOUCH_SLOP_DP &&
                touchesLocationButtonVertically
        val downloadControlTouchSlop = MAP_CONTROL_TOUCH_SLOP_DP * density
        val startsOnDownloadControl = mapDownloadControlBounds.let { bounds ->
            !bounds.isEmpty &&
                event.x >= bounds.left - downloadControlTouchSlop &&
                event.x <= bounds.right + downloadControlTouchSlop &&
                event.y >= bounds.top - downloadControlTouchSlop &&
                event.y <= bounds.bottom + downloadControlTouchSlop
        }
        return mapTouchEnabled &&
            startsInMap &&
            !startsOnLocationButton &&
            !startsOnDownloadControl
    }

    private companion object {
        const val MAP_TOUCH_BOTTOM_EXCLUSION_DP = 104
        const val MAP_NAVIGATION_BAR_HEIGHT_DP = 80
        const val MAP_LOCATION_BUTTON_SIZE_DP = 56
        const val MAP_LOCATION_BUTTON_RIGHT_MARGIN_DP = 16
        const val MAP_LOCATION_BUTTON_BOTTOM_MARGIN_DP = 16
        const val MAP_CONTROL_TOUCH_SLOP_DP = 8
    }
}
