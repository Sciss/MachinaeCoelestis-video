package de.sciss.coelestis

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import de.sciss.file._
import de.sciss.numbers
import jwave.transforms.FastWaveletTransform
import jwave.transforms.wavelets.daubechies.Daubechies8
import numbers.Implicits._

object WaveletProcess {
  final val DISPLACE = 64.0 // 8.0
  final val NOISE_IN = 0.1
  final val NOISE_OUT = 0.1
  final val BOOST    = 1.41 // 2.0 // 4.0 // 1.3
  final val CUT      = 0.01 // 0.1 // NOISE * BOOST

  def main(args: Array[String]): Unit = {
    val wavelet = new Daubechies8
    val trans   = new FastWaveletTransform(wavelet)
    // val in2     = readImage2D(file("image_out2/frame_rsmp-500.png"))

    var delay: Array[Array[Double]] = null

    for (i <- 1 to 100) {
      val in1     = readImage2D(file(s"image_out2/frame_rsmp-${i + 5000}.png"))
      if (NOISE_IN > 0) addNoise(in1, NOISE_IN)
      val w       = in1(0).length
      val h       = in1   .length

      if (delay == null) delay = Array.ofDim[Double](h.nextPowerOfTwo, w.nextPowerOfTwo)

      // val in2     = readImage2D(userHome / "Documents" / "devel" / "Miniaturen15" / "lyapunov_vid" / "image_out" / s"lya_748101b5-${i + 3000}.png")
      val in2     = readImage2D(file("lya_out") / s"lya_748101b5-${i + 3000}.png")
      // val in2     = readImage2D(file(s"image_out2/frame_rsmp-${i + 1000}.png"))
      normalize(in2)
      val mat1    = resize(in1, w.nextPowerOfTwo, h.nextPowerOfTwo)
      val mat2    = resize(in2, w.nextPowerOfTwo, h.nextPowerOfTwo)
      val mat1f   = trans.forward(mat1)
      val mat2f   = trans.forward(mat2)
      // normalize(mat1f)
      // normalize(mat2f)
      mix1(mat1f, mat2f, delay)
      mix2(mat1f, mat2f, mat1f)
      mix3(delay, mat1f, mat1f, mul1 = 2.0, mul2 = 2.0)
      val mat3    = trans.reverse(mat1f)
      val mat4    = trans.reverse(delay)
      mix3(mat4, mat3, mat3, mul1 = 4.0)
      val out1    = resize(mat3, w, h)
      if (NOISE_OUT > 0) addNoise(out1, NOISE_OUT)
      // normalize(out1)
      if (BOOST != 1 || CUT != 0) mulAdd(out1, BOOST, -CUT)
      writeImage2D(file(s"test_out/wavelet-F-$i.png"), out1)
    }
  }

  private final val rnd = new util.Random

  def copy(in: Array[Array[Double]], out: Array[Array[Double]]): Unit = {
    val height = in.length
    val width  = in(0).length
    var y = 0
    while (y < height) {
      val ic = in (y)
      val oc = out(y)
      var x = 0
      while (x < width) {
        oc(x) = ic(x)
        x += 1
      }
      y += 1
    }
  }

  def mulAdd(in: Array[Array[Double]], mul: Double, add: Double): Unit = {
    val height = in.length
    val width  = in(0).length
    var y = 0
    while (y < height) {
      val ic = in(y)
      var x = 0
      while (x < width) {
        ic(x) = ic(x) * mul + add
        x += 1
      }
      y += 1
    }
  }

  def addNoise(in: Array[Array[Double]], amt: Double): Unit = {
    val width  = in(0).length
    val height = in   .length
    var y = 0
    while (y < height) {
      val ic = in(y)
      var x = 0
      while (x < width) {
        ic(x) += (rnd.nextDouble() - 0.5) * amt
        x += 1
      }
      y += 1
    }
  }

  def mix2(a: Array[Array[Double]], b: Array[Array[Double]], out: Array[Array[Double]] = null,
           skew: Double = 1.2, displace: Double = DISPLACE): Array[Array[Double]] = {
    val width  = a(0).length
    val height = a   .length
    val c = if (out == null) Array.ofDim[Double](height, width) else out

    val wFact = math.pow(width , skew)
    val hFact = math.pow(height, skew)

    var y = 0
    while (y < height) {
      // val ac = a(y)
      val bc = b(y)
      val cc = c(y)
      var x = 0

      val h1 = (y + 1).nextPowerOfTwo
      val h2 = (h1 + 1) >> 1
      // assert (h2 <= y)

      while (x < width) {
        // val d = ac(x) hypotx bc(x) // ac(x) absdif bc(x)
        // cc(x) = if (((x ^ y) % 2) == 0) ac(x) else bc(x)
        val w1 = (x + 1).nextPowerOfTwo
        val w2 = (w1 + 1) >> 1

        val _displace = bc(x) * displace// 2
        val displaceX = x + _displace * w2 / wFact
        val wxj       = displaceX % 1.0
        val wxi       = 1.0 - wxj

        val displaceY = y + _displace * h2 / hFact
        val wyj       = displaceY % 1.0
        val wyi       = 1.0 - wyj

        // assert (w2 <= x)

//        val dxi       = (displaceX.toInt + width) % width
//        val dxj       = (dxi + 1) % width
//        val dyi       = (displaceY.toInt + height) % height
//        val dyj       = (dyi + 1) % height
        val dxi       = ((displaceX.toInt     - w2 + w2) % w2) + w2
        val dxj       = ((displaceX.toInt + 1 - w2 + w2) % w2) + w2
        val dyi       = ((displaceY.toInt     - h2 + h2) % h2) + h2
        val dyj       = ((displaceY.toInt + 1 - h2 + h2) % h2) + h2
        val d = a(dyi)(dxi) * wyi * wxi +
                a(dyi)(dxj) * wyi * wxj +
                a(dyj)(dxi) * wyj * wxi +
                a(dyj)(dxj) * wyj * wxj
        cc(x) = d

        x += 1
      }
      y += 1
    }
    c
  }

  def mix1(a: Array[Array[Double]], b: Array[Array[Double]], out: Array[Array[Double]] = null,
           lag: Double = 0.95): Array[Array[Double]] = {
    val width  = a(0).length
    val height = a   .length
    val c = if (out == null) Array.ofDim[Double](height, width) else out
    var y = 0
    while (y < height) {
      val ac = a(y)
      val bc = b(y)
      val cc = c(y)
      var x = 0

      while (x < width) {
        // val d = ac(x) hypotx bc(x)
        // val d = ac(x) absdif bc(x)
        // val d = ac(x) trunc bc(x)

        // val d = ac(x) trunc  bc(x)
        // val d = ac(x) excess bc(x)
        // val d = if (x < 2 && y < 2) ac(x) else ac(x) * bc(x)
        // val d0 = (ac(x) atan2 bc(x)) / math.Pi
        // val d = d0 * ac(x) // math.min(d0, ac(x))
        val d0    = ac(x) fold2 bc(x)
        val prev  = cc(x)

//        val w     = if (d0 > prev) atk else rls
//        val d     = d0 * w + prev * (1 - w)
        // val d = d0 + prev * 0.95
        val d = if (d0 > prev) d0 else d0 * (1 - lag) + prev * lag

        // cc(x) = if (((x ^ y) % 2) == 0) ac(x) else bc(x)
        cc(x) = d

        x += 1
      }
      y += 1
    }
    c
  }

  /** Binary maximum filter */
  def mix3(a: Array[Array[Double]], b: Array[Array[Double]], out: Array[Array[Double]] = null,
           mul1: Double = 1.0, mul2: Double = 1.0): Array[Array[Double]] = {
    val width  = a(0).length
    val height = a   .length
    val c = if (out == null) Array.ofDim[Double](height, width) else out
    var y = 0
    while (y < height) {
      val ac = a(y)
      val bc = b(y)
      val cc = c(y)
      var x = 0

      while (x < width) {
        // val d = ac(x) hypotx bc(x)
        // val d = ac(x) absdif bc(x)
        // val d = ac(x) trunc bc(x)

        // val d = ac(x) trunc  bc(x)
        // val d = ac(x) excess bc(x)
        // val d = if (x < 2 && y < 2) ac(x) else ac(x) * bc(x)
        // val d0 = (ac(x) atan2 bc(x)) / math.Pi
        // val d = d0 * ac(x) // math.min(d0, ac(x))
        val ad    = ac(x) * mul1
        val bd    = bc(x) * mul2
        val d     = ad max bd
        // val prev  = cc(x)

        //        val w     = if (d0 > prev) atk else rls
        //        val d     = d0 * w + prev * (1 - w)
        // val d = d0 + prev * 0.95
        // val d = if (d0 > prev) d0 else d0 * (1 - lag) + prev * lag

        // cc(x) = if (((x ^ y) % 2) == 0) ac(x) else bc(x)
        cc(x) = d

        x += 1
      }
      y += 1
    }
    c
  }

  /** Binary addition filter */
  def mix4(a: Array[Array[Double]], b: Array[Array[Double]], out: Array[Array[Double]] = null,
           mul1: Double = 1.0, mul2: Double = 1.0): Array[Array[Double]] = {
    val width  = a(0).length
    val height = a   .length
    val c = if (out == null) Array.ofDim[Double](height, width) else out
    var y = 0
    while (y < height) {
      val ac = a(y)
      val bc = b(y)
      val cc = c(y)
      var x = 0

      while (x < width) {
        val ad    = ac(x) * mul1
        val bd    = bc(x) * mul2
        val d     = ad + bd
        cc(x) = d
        x += 1
      }
      y += 1
    }
    c
  }

  def resize(in: Array[Array[Double]], wOut: Int, hOut: Int): Array[Array[Double]] = {
    val wIn = in(0).length
    val hIn = in   .length
    val out = Array.ofDim[Double](hOut, wOut)
    val hMin  = math.min(hOut, hIn)
    val wMin  = math.min(wOut, wIn)
    var y = 0
    while (y < hMin) {
      var x = 0
      while (x < wMin) {
        out(y)(x) = in(y)(x)
        x += 1
      }
      y += 1
    }
    out
  }

  def bufferToImage(in: Array[Array[Double]], gain: Double = 1.0): BufferedImage = {
    val height  = in.length
    val width   = in(0).length
    val res     = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    var i = 0
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        val d       = in(y)(x)
        val vOut    = (math.max(0.0, math.min(1.0, d * gain)) * 255 + 0.5).toInt
        val rgbOut  = 0xFF000000 | (vOut << 16) | (vOut << 8) | vOut
        res.setRGB(x, y, rgbOut)
        x += 1
        i += 1
      }
      y += 1
    }
    res
  }

  def writeImage2D(out: File, buf: Array[Array[Double]]): Unit = {
    val img = bufferToImage(buf)
    ImageIO.write(img, "png", out)
  }

  def normalize(in: Array[Array[Double]]): Unit = {
    val w         = in(0).length
    val h         = in   .length

    var min = Double.MaxValue
    var max = Double.MinValue
    var y = 0
    while (y < h) {
      var x = 0
      val inc = in(y)
      while (x < w) {
        val d = inc(x)
        if (d < min) min = d
        if (d > max) max = d
        x += 1
      }
      y += 1
    }

    if (min >= max) return

    val mul = 1.0 / max - min
    val add = -min

    while (y < h) {
      var x = 0
      val inc = in(y)
      while (x < w) {
        inc(x) = (inc(x) + add) * mul
        x += 1
      }
      y += 1
    }
  }

  def readImage2D(in: File): Array[Array[Double]] = {
    val imgIn     = ImageIO.read(in)
    val imgCrop   = imgIn // cropImage2(config, imgIn)
    val w         = imgCrop.getWidth
    val h         = imgCrop.getHeight

    val res       = Array.ofDim[Double](h, w)

    var y = 0
    // var t = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val rgbIn = imgCrop.getRGB(x, y)
        val vIn = (((rgbIn & 0xFF0000) >> 16) + ((rgbIn & 0x00FF00) >> 8) + (rgbIn & 0x0000FF)) / 765f // it's gray anyway
        // res(t) = vIn
        res(y)(x) = vIn
        x += 1
        // t += 1
      }
      y += 1
    }
    res
  }
}