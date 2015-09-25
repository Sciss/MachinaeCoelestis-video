package de.sciss.coelestis

import de.sciss.file._
import de.sciss.numbers
import jwave.transforms.FastWaveletTransform
import jwave.transforms.wavelets.daubechies.Daubechies8

object Assemble {
  def main(args: Array[String]): Unit = {
//    mkScene1()
//    mkScene2()
//    mkScene3()
//    mkScene4()
//    mkScene5()
    mkScene6()
  }

  def mkInputFrame (i: Int): File = file("image_out2") / s"frame_rsmp-$i.png"
  def mkOutputFrame(i: Int): File = file("assemble")   / s"frame-$i.png"
  def mkLyaFrame   (i: Int): File = file("lya_out")    / s"lya_748101b5-$i.png"

  def mkLink(in: File, out: File): Unit = {
    import sys.process._
    val code = Seq("ln", "-rs", in.path, out.path).!
    require(code == 0)
  }

  import WaveletProcess.{readImage2D, mulAdd, addNoise, writeImage2D, mix1, mix2, mix3, mix4, normalize, resize}
  import numbers.Implicits._

  /*

    1 ... 2000 fade in   0.0 ... 1.0
    1 ... 4000 noise add 0.1 ... 0.0

   */
  def mkScene1(): Unit = {
    for (i <- 1 to 4000) {
      val out = mkOutputFrame(i)
      if (!out.exists()) {
        val mat = readImage2D(mkInputFrame(i))
        val mul = i.clip(1, 2000).linlin(1, 2000, 0.0, 1.0)
        mulAdd(mat, mul = mul, add = 0)
        val noise = if (i == 1) 0.0 else i.clip(1, 4000).linlin(1, 4000, 0.1, 0.0)
        if (noise > 0) addNoise(mat, noise)
        writeImage2D(out, mat)
      }
    }
  }

  /*

    4001 ... 13700 copy

   */

  def mkScene2(): Unit = {
    for (i <- 4001 to 13700) {
      val out = mkOutputFrame(i)
      if (!out.exists())
        mkLink(in = mkInputFrame(i), out = out)
    }
  }

  /*

    13701 ... 14700 EqP fade out in 13701 ... 14700
                    EqP fade in  in 15501 ... 16500

    after this, input is offset against output by 1800 frames

   */

  def mkScene3(): Unit = {
    for (i <- 13701 to 14700) {
      val out   = mkOutputFrame(i)
      if (!out.exists()) {
        val j     = i + 1800
        val in1   = readImage2D(mkInputFrame(i))
        val in2   = readImage2D(mkInputFrame(j))
        val line  = i.linlin(13701, 14700, 0, 1)
        // mulAdd(in1, mul = (1 - line).sqrt, add = 0.0)
        // mulAdd(in2, mul = line      .sqrt, add = 0.0)
        val mat   = mix3(a = in1, b = in2, mul1 = (1 - line).sqrt, mul2 = line.sqrt)
        writeImage2D(out, mat)
      }
    }
  }

  /*

    14701 ... 18750 copy (in + 1800)

   */

  def mkScene4(): Unit = {
    for (i <- 14701 to 18750) {
      val out = mkOutputFrame(i)
      if (!out.exists()) {
        val j     = i + 1800
        val in    = mkInputFrame(j)
        mkLink(in = in, out = out)
      }
    }
  }

  /*

    18751 ... 24200

      - copy (in + 1800)
      - x-fade to 'proc' 18751 ... 19500
      - where 'proc' is:
        - 18751 ... 23150 fade-in lya
                          fade-in wavelet displace
        - 23151 ... 23900 fade-out all but lya
        - 23950 ... 24200 fade-out all

        - lya index = 24200 - out-index + 1 (i.e. 5450 back to 1)

   */

  def mkScene5(): Unit = {
    val wavelet = new Daubechies8
    val trans   = new FastWaveletTransform(wavelet)

    val w       = 1080
    val h       = 1920
    val delay   = Array.ofDim[Double](h.nextPowerOfTwo, w.nextPowerOfTwo)

    for (i <- 18751 to 24200) {
      val out = mkOutputFrame(i)
      if (!out.exists()) {
        val j         = i + 1800
        val in        = readImage2D(mkInputFrame(j))
        val k         = 24200 - i + 1
        val lya       = readImage2D(mkLyaFrame(k))
        normalize(lya)
        val fade1     = i.linlin(18751, 19500, 0, 1).clip(0, 1) // x-fade to proc
        val fade2     = i.linlin(18751, 23150, 0, 1).clip(0, 1) // lya and displace in
        val fade3     = i.linlin(23150, 23900, 1, 0).clip(0, 1) // out but lya
        val fade4     = i.linlin(23950, 24200, 1, 0).clip(0, 1) // all out
        val noiseIn   = WaveletProcess.NOISE_IN  * fade2
        val displace  = WaveletProcess.DISPLACE  * fade2
        val noiseOut  = WaveletProcess.NOISE_OUT * fade2

        addNoise(in, noiseIn)
        val mat1    = resize(in , w.nextPowerOfTwo, h.nextPowerOfTwo)
        val mat2    = resize(lya, w.nextPowerOfTwo, h.nextPowerOfTwo)
        val mat1f   = trans.forward(mat1)
        val mat2f   = trans.forward(mat2)

        mix1(mat1f, mat2f, delay) // 'background'
        if (fade3 < 1) mulAdd(mat1f, mul = fade3, add = 0)
        mix2(mat1f, mat2f, mat1f, displace = displace)
        mix3(delay, mat1f, mat1f, mul1 = 2.0 * fade2, mul2 = fade2.linlin(0, 1, 1.0, 2.0))
        val mat3    = trans.reverse(mat1f)
        val mat4    = trans.reverse(delay)
        mix3(mat4, mat3, mat3, mul1 = 4.0)
        val out1    = resize(mat3, w, h)
        addNoise(out1, noiseOut)
        val boost   = fade2.linlin(0, 1, 1.0, WaveletProcess.BOOST)
        val cut     = WaveletProcess.CUT * fade2
        if (boost != 1 || cut != 0) mulAdd(out1, boost, -cut)

        mix4(in, out1, out1, mul1 = (1 - fade1) * fade4, mul2 = fade1 * fade4)

        writeImage2D(out, out1)
      }
    }
  }

  /*

    24201 ... 24250 two seconds black

   */

  def mkScene6(): Unit = {
    for (i <- 24201 to 24250) {
      val out = mkOutputFrame(i)
      val in  = file("black_frame.png")
      if (!out.exists()) {
        mkLink(in = in, out = out)
      }
    }
  }
}