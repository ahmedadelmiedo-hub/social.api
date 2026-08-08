package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.*
import android.os.Environment
import java.io.File
import java.nio.ByteBuffer

object MediaGenerator {

    fun generateSceneBasedH264VideoWithAudio(
        context: Context,
        topicTitle: String,
        sceneImages: List<File?>,
        sceneTexts: List<String>,
        audioFile: File?,
        outputFile: File,
        secondsPerImage: Int = 3
    ) {
        try {
            val tempVideo = File(context.cacheDir, "temp_video_only_${System.currentTimeMillis()}.mp4")
            createVideoFromImages(sceneImages.filterNotNull(), tempVideo, secondsPerImage)

            if (audioFile != null && audioFile.exists() && audioFile.length() > 1024) {
                val tempAacMp4 = File(context.cacheDir, "temp_aac_${System.currentTimeMillis()}.mp4")
                transcodeMp3ToAacMp4(audioFile, tempAacMp4)
                muxVideoAndAudio(tempVideo, tempAacMp4, outputFile)
                tempVideo.delete()
                tempAacMp4.delete()
            } else {
                tempVideo.copyTo(outputFile, overwrite = true)
                tempVideo.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaGenerator", "Video generation failed: ${e.message}", e)
        }
    }

    fun generateH264Mp4Video(context: Context, topic: String, outputFile: File, durationSeconds: Int = 5) {
        val dummyBitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(dummyBitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#0F0B1E"))
        createVideoFromBitmaps(listOf(dummyBitmap), outputFile, durationSeconds * 30)
    }

    private fun createVideoFromImages(imageFiles: List<File>, outFile: File, secondsPerImage: Int) {
        val bitmaps = imageFiles.mapNotNull { try { BitmapFactory.decodeFile(it.absolutePath) } catch (_: Exception) { null } }
        if (bitmaps.isEmpty()) {
            val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.BLACK)
            createVideoFromBitmaps(listOf(bmp), outFile, 150)
            return
        }
        createVideoFromBitmaps(bitmaps, outFile, bitmaps.size * secondsPerImage * 30)
    }

    private fun createVideoFromBitmaps(bitmaps: List<Bitmap>, outFile: File, totalFrames: Int) {
        val width = 720
        val height = 1280
        val fps = 30
        val bitrate = 2000000
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        var frameIndex = 0
        var bitmapIndex = 0
        val framesPerImage = if (bitmaps.isNotEmpty()) totalFrames / bitmaps.size else totalFrames
        while (frameIndex < totalFrames) {
            val currentBitmap = bitmaps[bitmapIndex % bitmaps.size]
            val scaled = Bitmap.createScaledBitmap(currentBitmap, width, height, true)
            val yuv = getNV21(width, height, scaled)
            val inputBufIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufIndex >= 0) {
                val inputBuf = encoder.getInputBuffer(inputBufIndex)!!
                inputBuf.clear()
                inputBuf.put(yuv)
                encoder.queueInputBuffer(inputBufIndex, 0, yuv.size, (frameIndex * 1000000L / fps), 0)
                frameIndex++
                if (frameIndex % framesPerImage == 0) bitmapIndex++
            }
            var outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (outIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    if (!muxerStarted) {
                        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    muxer?.writeSampleData(trackIndex, encoder.getOutputBuffer(outIndex)!!, bufferInfo)
                }
                encoder.releaseOutputBuffer(outIndex, false)
                outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
        val inIndex = encoder.dequeueInputBuffer(10000)
        if (inIndex >= 0) encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        var outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
        while (outIndex >= 0) {
            if (muxerStarted && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                muxer?.writeSampleData(trackIndex, encoder.getOutputBuffer(outIndex)!!, bufferInfo)
            }
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            encoder.releaseOutputBuffer(outIndex, false)
            outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
        }
        encoder.stop(); encoder.release()
        try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
    }

    private fun transcodeMp3ToAacMp4(mp3File: File, outFile: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(mp3File.absolutePath)
        val audioTrack = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        extractor.selectTrack(audioTrack)
        val inputFormat = extractor.getTrackFormat(audioTrack)
        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxTrack = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val decoderBufferInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var encoderDone = false
        while (!encoderDone) {
            if (!extractorDone) {
                val inIndex = decoder.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        extractorDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            var decOutIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 10000)
            while (decOutIndex >= 0) {
                val decodedData = decoder.getOutputBuffer(decOutIndex)
                if (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderDone = true
                if (decodedData != null && decoderBufferInfo.size > 0 && !decoderDone) {
                    val encInIndex = encoder.dequeueInputBuffer(10000)
                    if (encInIndex >= 0) {
                        val encBuf = encoder.getInputBuffer(encInIndex)!!
                        encBuf.clear()
                        encBuf.put(decodedData)
                        encoder.queueInputBuffer(encInIndex, 0, decoderBufferInfo.size, decoderBufferInfo.presentationTimeUs, 0)
                    }
                } else if (decoderDone) {
                    val encInIndex = encoder.dequeueInputBuffer(10000)
                    if (encInIndex >= 0) encoder.queueInputBuffer(encInIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                decoder.releaseOutputBuffer(decOutIndex, false)
                if (decoderDone) break
                decOutIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 0)
            }
            val encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (encOutIndex >= 0) {
                val encoded = encoder.getOutputBuffer(encOutIndex)!!
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                } else {
                    if (!muxerStarted) {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        android.util.Log.d("MediaGenerator", "Audio track added")
                    }
                    if (bufferInfo.size > 0) muxer.writeSampleData(muxTrack, encoded, bufferInfo)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                }
                encoder.releaseOutputBuffer(encOutIndex, false)
            }
        }
        try { decoder.stop(); decoder.release() } catch (_: Exception) {}
        try { encoder.stop(); encoder.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}
        try { muxer.stop(); muxer.release() } catch (_: Exception) {}
    }

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

    private fun getNV21(inputWidth: Int, inputHeight: Int, scaled: Bitmap): ByteArray {
        val argb = IntArray(inputWidth * inputHeight)
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)
        var yIndex = 0
        var uvIndex = inputWidth * inputHeight
        for (j in 0 until inputHeight) {
            for (i in 0 until inputWidth) {
                val rgb = argb[j * inputWidth + i]
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
