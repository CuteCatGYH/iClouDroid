package com.getup.iClouDroid.feature.photos.data

import org.json.JSONObject

/**
 * Parsed EXIF/GPS for one asset, as emitted by `icloudcore.FetchPhotoMetadata`.
 *
 * Every field is nullable, and `null` means "the camera did not record this". That is the whole
 * point of the type: the info panel renders only what is present, so absence has to be
 * representable. Never substitute 0 for a missing number — `f/0` and `ISO 0` are not "unknown",
 * they are wrong.
 *
 * Reference for the source fields: docs/REFERENCE.md §2A.
 */
data class PhotoExif(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,

    /** Aperture, e.g. 1.85 for f/1.85. */
    val fNumber: Double? = null,
    /** Shutter speed in seconds, e.g. 0.001353 (~1/739s). */
    val exposureTime: Double? = null,
    val iso: Int? = null,
    /** Focal length in mm. */
    val focalLength: Double? = null,
    /** 35mm-equivalent focal length in mm. */
    val focalLength35: Int? = null,
    val exposureBias: Double? = null,

    /** Apple's EXIF capture time, verbatim (`"2026:10:21 11:03:54"`). */
    val dateTimeOriginal: String? = null,

    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val colorModel: String? = null,
    val profileName: String? = null,

    /** Null when the record has no `{GPS}` block — the common case (~68% of samples). */
    val gps: PhotoGps? = null,

    /** False when the record carries no `mediaMetaDataEnc` at all (screenshots, imports). */
    val hasMetadata: Boolean = false
)

/** Position block. Only present when the photo actually has a fix. */
data class PhotoGps(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    /** Compass heading of the camera at capture, degrees. */
    val imgDirection: Double? = null,
    /** Horizontal accuracy, metres. */
    val horizontalError: Double? = null,
    val timestamp: String? = null
)

object PhotoExifParser {
    /**
     * Parses the JSON emitted by `icloudcore.FetchPhotoMetadata`.
     *
     * Returns null only for a blank/unparseable payload. A well-formed object with no camera
     * fields (a screenshot) still returns a [PhotoExif] with `hasMetadata = false`, because the
     * caller needs to distinguish "no metadata" from "not fetched yet".
     */
    fun parse(json: String?): PhotoExif? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            PhotoExif(
                cameraMake = o.optStringOrNull("cameraMake"),
                cameraModel = o.optStringOrNull("cameraModel"),
                lensModel = o.optStringOrNull("lensModel"),
                fNumber = o.optDoubleOrNull("fNumber"),
                exposureTime = o.optDoubleOrNull("exposureTime"),
                iso = o.optIntOrNull("iso"),
                focalLength = o.optDoubleOrNull("focalLength"),
                focalLength35 = o.optDoubleOrNull("focalLength35")?.toInt(),
                exposureBias = o.optDoubleOrNull("exposureBias"),
                dateTimeOriginal = o.optStringOrNull("dateTimeOriginal"),
                pixelWidth = o.optIntOrNull("pixelWidth"),
                pixelHeight = o.optIntOrNull("pixelHeight"),
                colorModel = o.optStringOrNull("colorModel"),
                profileName = o.optStringOrNull("profileName"),
                gps = o.optJSONObject("gps")?.let { g ->
                    // A GPS object without a usable coordinate pair is not a location.
                    val lat = g.optDoubleOrNull("latitude") ?: return@let null
                    val lng = g.optDoubleOrNull("longitude") ?: return@let null
                    PhotoGps(
                        latitude = lat,
                        longitude = lng,
                        altitude = g.optDoubleOrNull("altitude"),
                        speed = g.optDoubleOrNull("speed"),
                        imgDirection = g.optDoubleOrNull("imgDirection"),
                        horizontalError = g.optDoubleOrNull("hPositioningError"),
                        timestamp = g.optStringOrNull("timestamp")
                    )
                },
                hasMetadata = o.optBoolean("hasMetadata", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    // org.json's optDouble/optInt return 0.0/0 for a missing key, which is indistinguishable from
    // a genuine zero. Since 0 is never a valid aperture/shutter/ISO, presence must be checked.
    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
