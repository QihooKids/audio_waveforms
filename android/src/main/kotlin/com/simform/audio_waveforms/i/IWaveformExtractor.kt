package com.simform.audio_waveforms.i

interface IWaveformExtractor {
    fun startDecode()
    fun data():List<Float>
    fun stop()
}