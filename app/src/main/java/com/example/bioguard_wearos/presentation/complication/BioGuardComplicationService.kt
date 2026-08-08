package com.example.bioguard_wearos.presentation.complication

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BioGuardComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        private const val TAG = "BIOGUARD_COMPLICATION"
    }

    @Inject
    lateinit var sensorDataRepository: SensorDataRepository

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("75 BPM").build(),
            contentDescription = PlainComplicationText.Builder("Pulso BioGuard").build()
        ).setTitle(PlainComplicationText.Builder("BioGuard").build()).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(TAG, "Solicitud de complicación recibida: ${request.complicationType}")
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        val currentBpm = sensorDataRepository.sensorData.value.bpm
        val bpmText = if (currentBpm > 0f) "${currentBpm.toInt()} BPM" else "-- BPM"

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(bpmText).build(),
            contentDescription = PlainComplicationText.Builder("Estado de Salud BioGuard").build()
        ).setTitle(PlainComplicationText.Builder("BioGuard").build()).build()
    }
}
