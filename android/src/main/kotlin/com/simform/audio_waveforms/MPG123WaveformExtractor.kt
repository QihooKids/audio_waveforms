package com.simform.audio_waveforms

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.simform.audio_waveforms.i.IWaveformExtractor
import io.flutter.plugin.common.MethodChannel
import me.rosuh.libmpg123.MPG123
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MPG123WaveformExtractor(
    private val path: String,
    private val expectedPoints: Int,
    private val result: MethodChannel.Result,
) :IWaveformExtractor {
    private var task : ExtractorTask? = null

    override fun startDecode() {
        task = ExtractorTask(path, expectedPoints, result).apply {
            start()
        }
    }

    override fun data(): List<Float> {
        return task?.waveformData ?: emptyList()
    }

    override fun cancel() {
        task?.cancel()
    }

}

class ExtractorTask (
    private val path: String,
    private val expectedPoints: Int,
    private val result: MethodChannel.Result,
) : Thread(){

    private val TAG = "ExtractorTask"

    @Volatile
    var isRunning = true
    val decoder = MPG123(path)
    val shortBuffer = ArrayList<Int>()
    val waveformData = ArrayList<Float>()

    override fun run() {
        try {
            val startTime = System.currentTimeMillis();
            Log.d(TAG, "start....expectedPoints:$expectedPoints")
            while (isRunning){
                val pcm: ShortArray? = decoder.readFrame()
                if (pcm == null || pcm.isEmpty()) {
                    break
                } else {
                    shortBuffer.add(convertToWaveForm(pcm))
                }
            }
            if (isRunning) {
                filterAndNormalize(shortBuffer)
                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "success...${waveformData.size}, cost:${System.currentTimeMillis() - startTime}")
                    result.success(waveformData)
                }
            } else {
                result.error(
                    Constants.LOG_TAG,
                    "canceled",
                    "An error is thrown while decoding the audio file"
                )
            }
        } catch (e: Exception) {
            result.error(
                Constants.LOG_TAG,
                e.message,
                "An error is thrown while decoding the audio file"
            )
        } finally {
            decoder.close()
        }

    }

    private fun filterAndNormalize(shortBuffer: java.util.ArrayList<Int>) {
        val mergeCount = shortBuffer.size / expectedPoints
        val mergeList = ArrayList<Int>()
        var max = 0
        val resultList = ArrayList<Int>()
        shortBuffer.forEachIndexed { index, i ->
            if (index%mergeCount == 0) {
                if (mergeList.isNotEmpty()){
                    val result = convertToWaveForm(mergeList)
                    resultList.add(result)
                    if(result > max){
                        max = result
                    }
                }
                mergeList.clear()
            }
            mergeList.add(i)
        }
        waveformData.addAll(resultList.map {
            Log.d(TAG, "item:$it, max:$max")
            it/max.toFloat()/4
        })
    }

    private fun convertToWaveForm(array: ArrayList<Int>): Int {
        var value = 0.0
        array.forEach {
            value += abs(it.toInt()).toDouble().pow(2)
        }
        return sqrt(value).toInt()
    }

    private fun convertToWaveForm(array: ShortArray): Int {
        var value = 0.0
        array.forEach {
            value += abs(it.toInt()).toDouble().pow(2)
        }
        return sqrt(value).toInt()
    }

    fun cancel(){
        isRunning = false
        decoder.close()
    }

}
