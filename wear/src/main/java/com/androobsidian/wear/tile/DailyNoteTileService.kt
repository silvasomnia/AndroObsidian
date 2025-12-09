package com.androobsidian.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders.*
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.androobsidian.wear.data.CachedNote
import com.androobsidian.wear.data.NoteRepository
import com.androobsidian.wear.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyNoteTileService : TileService() {

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        return try {
            val note = NoteRepository.getInstance(this).getNote()
            val deviceParams = requestParams.deviceConfiguration

            val tile = Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    Timeline.Builder()
                        .addTimelineEntry(
                            TimelineEntry.Builder()
                                .setLayout(Layout.Builder().setRoot(createLayout(note, deviceParams)).build())
                                .build()
                        )
                        .build()
                )
                .build()

            Futures.immediateFuture(tile)
        } catch (e: Exception) {
            // Return a safe fallback tile on any error
            val deviceParams = requestParams.deviceConfiguration
            val tile = Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    Timeline.Builder()
                        .addTimelineEntry(
                            TimelineEntry.Builder()
                                .setLayout(Layout.Builder().setRoot(createLayout(null, deviceParams)).build())
                                .build()
                        )
                        .build()
                )
                .build()
            Futures.immediateFuture(tile)
        }
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> {
        return Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun createLayout(note: CachedNote?, deviceParams: DeviceParameters): LayoutElement {
        val context: Context = this
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val updateTime = note?.receivedAt?.let { timeFormat.format(Date(it)) } ?: ""
        val headerText = if (note != null) "${note.date} • $updateTime" else "No Note"

        return PrimaryLayout.Builder(deviceParams)
            .setPrimaryLabelTextContent(
                Text.Builder(context, headerText)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFFAAAAAA.toInt()))
                    .build()
            )
            .setContent(
                // Smaller font (60% of body2 ~14sp = ~9sp) for more content on tile
                androidx.wear.protolayout.LayoutElementBuilders.Text.Builder()
                    .setText(note?.lastLines?.ifBlank { "No entries yet" } ?: "Open phone app to sync")
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(9f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .setMaxLines(10)
                    .build()
            )
            .setPrimaryChipContent(
                androidx.wear.protolayout.material.CompactChip.Builder(
                    context,
                    "Open Full",
                    Clickable.Builder()
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setPackageName(packageName)
                                        .setClassName(MainActivity::class.java.name)
                                        .build()
                                )
                                .build()
                        )
                        .build(),
                    deviceParams
                ).build()
            )
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
