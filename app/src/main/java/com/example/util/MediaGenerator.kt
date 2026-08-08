private fun muxVideoAndAudio(videoFile: File, audioFile: File, outFile: File) {
    val vExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
    val aExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
    val vTrack = (0 until vExtractor.trackCount).first { vExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
    val aTrack = (0 until aExtractor.trackCount).first { aExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    vExtractor.selectTrack(vTrack)
    aExtractor.selectTrack(aTrack)
    val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val vMuxTrack = muxer.addTrack(vExtractor.getTrackFormat(vTrack))
    val aMuxTrack = muxer.addTrack(aExtractor.getTrackFormat(aTrack))
    muxer.start()
    val bufferInfo = MediaCodec.BufferInfo()

    val vBuffer = ByteBuffer.allocate(1024 * 1024)
    while (true) {
        val sampleSize = vExtractor.readSampleData(vBuffer, 0)
        if (sampleSize < 0) break
        vBuffer.position(0)
        vBuffer.limit(sampleSize)
        bufferInfo.set(0, sampleSize, vExtractor.sampleTime, vExtractor.sampleFlags)
        muxer.writeSampleData(vMuxTrack, vBuffer, bufferInfo)
        vExtractor.advance()
    }

    val aBuffer = ByteBuffer.allocate(1024 * 1024)
    while (true) {
        val sampleSize = aExtractor.readSampleData(aBuffer, 0)
        if (sampleSize < 0) break
        aBuffer.position(0)
        aBuffer.limit(sampleSize)
        bufferInfo.set(0, sampleSize, aExtractor.sampleTime, aExtractor.sampleFlags)
        muxer.writeSampleData(aMuxTrack, aBuffer, bufferInfo)
        aExtractor.advance()
    }

    vExtractor.release(); aExtractor.release()
    muxer.stop(); muxer.release()
}
