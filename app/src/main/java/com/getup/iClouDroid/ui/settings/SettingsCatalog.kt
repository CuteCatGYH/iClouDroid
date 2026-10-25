package com.getup.iClouDroid.ui.settings

/**
 * Identity of one settings row.
 *
 * The screen renders groups from [SettingsCatalog]; adding a setting means adding an enum entry,
 * one line in [SettingsCatalog.groups], and one branch in the screen's row builder. The enum (not
 * a string key) is what keeps the `when` exhaustive, so a new item cannot be added and then
 * silently not rendered.
 */
enum class SettingsItem {
    /** Theme mode: Follow system / Light / Dark. */
    THEME_MODE,

    /** Material You Monet dynamic color from system wallpaper. */
    DYNAMIC_COLOR,

    /** Cloud media provider settings shortcut. */
    CLOUD_MEDIA_PROVIDER,

    /** Long-running: walks the whole cloud library. Shows a warning before starting. */
    FULL_SCAN,

    /**
     * Whether the month jump panel shows a thumbnail per month.
     *
     * Turning it off also stops the scan from downloading any cover — this is not a display-only
     * switch (see [com.getup.iClouDroid.data.settings.AppSettings.monthCoverEnabled]).
     */
    MONTH_COVER_PREVIEW,

    /** How often the full walk repeats in the background. */
    SCAN_FREQUENCY,

    /** Interval in days; only meaningful while the frequency is 自定义. */
    CUSTOM_INTERVAL
}

/** One titled block of rows. */
data class SettingsGroup(
    val title: String,
    val description: String? = null,
    val items: List<SettingsItem>
)

/**
 * Declarative layout of the settings screen.
 *
 * Kept data-only (no composables) so the structure is reviewable at a glance and a future
 * [SettingsItem] can be slotted in without touching layout code.
 */
object SettingsCatalog {

    val groups: List<SettingsGroup> = listOf(
        SettingsGroup(
            title = "外观与个性化",
            description = "设置应用的深浅主题模式与 Android 12+ Monet 动态壁纸取色效果。",
            items = listOf(SettingsItem.THEME_MODE, SettingsItem.DYNAMIC_COLOR)
        ),
        SettingsGroup(
            title = "系统整合",
            description = "配置 iClouDroid 与 Android 系统的多媒体生态深度联动。",
            items = listOf(SettingsItem.CLOUD_MEDIA_PROVIDER)
        ),
        SettingsGroup(
            title = "照片库",
            description = "全盘扫描会遍历整个 iCloud 照片库，用于发现并清理在其它设备上已删除的照片。",
            items = listOf(SettingsItem.FULL_SCAN, SettingsItem.MONTH_COVER_PREVIEW)
        ),
        SettingsGroup(
            title = "自动扫描",
            description = "按设定周期在后台（凌晨 3 点）自动执行全盘扫描与增量同步。",
            items = listOf(SettingsItem.SCAN_FREQUENCY, SettingsItem.CUSTOM_INTERVAL)
        )
    )
}
