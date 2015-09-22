package de.sciss.coelestis

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import de.sciss.file._
import de.sciss.numbers
import jwave.transforms.FastWaveletTransform
import jwave.transforms.wavelets.daubechies.Daubechies8
import numbers.Implicits._

object WaveletTest {
  def main(args: Array[String]): Unit = {
    val wavelet = new Daubechies8
    val trans   = new FastWaveletTransform(wavelet)
    // val in2     = readImage2D(file("image_out2/frame_rsmp-500.png"))

    for (i <- 1 to 100) {
      val in1     = readImage2D(file(s"image_out2/frame_rsmp-${i + 5000}.png"))
      val w       = in1(0).length
      val h       = in1   .length
      // val in2     = readImage2D(userHome / "Documents" / "devel" / "Miniaturen15" / "lyapunov_vid" / "image_out" / s"lya_e224f03c-${i + 5000}.png")
      val in2     = readImage2D(file(s"image_out2/frame_rsmp-${i + 1000}.png"))
      val mat1    = resize(in1, w.nextPowerOfTwo, h.nextPowerOfTwo)
      val mat2    = resize(in2, w.nextPowerOfTwo, h.nextPowerOfTwo)
      val mat1f   = trans.forward(mat1)
      val mat2f   = trans.forward(mat2)
      mix(mat1f, mat2f, mat1f)
      val mat3    = trans.reverse(mat1f)
      val out1    = resize(mat3, w, h)
      writeImage2D(file(s"test_out/wavelet-B-$i.png"), out1)
    }
  }

  def mix(a: Array[Array[Double]], b: Array[Array[Double]], out: Array[Array[Double]] = null): Unit = {
    val height = a.length
    val width  = a(0).length
    val c = if (out == null) Array.ofDim[Double](height, width) else out
    var y = 0
    while (y < height) {
      val ac = a(y)
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

        val displace  = bc(x) * 2
        val displaceX = x + displace * w2 / width
        val wxj       = displaceX % 1.0
        val wxi       = 1.0 - wxj

        val displaceY = y + displace * h2 / height
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