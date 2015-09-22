package de.sciss.coelestis

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import de.sciss.dsp
import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import scopt.OptionParser

import scala.concurrent.blocking

object Resample {
  case class Config(in: File = file("image_out/frame-%d.png"), out: File = file("image_out2/frame_rsmp.png"),
                    firstFrame: Int = 1, lastFrame: Int = 15196, outputOffset: Int = 1,
                    noise: Int = 1 /* 32 */, thresh: Int = 0 /* 160 */, resampleWindow: Int = 37)

  def main(args: Array[String]): Unit = {
    val p = new OptionParser[Config]("Resample") {
      opt[File]("in" ) text "input frame pattern using %d as placeholder for frame number" action { case (v, c) => c.copy(in = v) }
      opt[File]("out") text "output frame name, where -<frame> will be inserted before extension" action { case (v, c) => c.copy(out = v) }
      opt[Int]("first-frame") action { case (v, c) => c.copy(firstFrame = v) }
      opt[Int]("last-frame" ) action { case (v, c) => c.copy(lastFrame  = v) }
      opt[Int]("output-offset") text "output frame numbering offset" action { case (v, c) => c.copy(outputOffset  = v) }
      opt[Int]("noise") text "noise amount from 0 to 100" action { case (v, c) => c.copy(noise = v) }
      opt[Int]("thresh") text "b/w threshold level (or 0 for no thresholding)" action { case (v, c) => c.copy(thresh = v) }
      opt[Int]("window") text "resampling window length in frames (must be odd; max 37)" action { case (v, c) => c.copy(resampleWindow = v) }
    }
    p.parse(args, Config()).fold(sys.exit(1)) { config =>
      val proc = renderImageSequence(config)
      println("_" * 33)
      proc.monitor()
      waitForProcessor(proc, quit = true)
    }
  }

  def renderImage(config: Config, frame: Int, f: File): Processor[Unit] = {
    val res = new RenderImage(config, frame = frame, f = f)
    res.start()
    res
  }

  private final class RenderImage(config: Config, frame: Int, f: File)
    extends ProcessorImpl[Unit, Processor[Unit]] with Processor[Unit] {

    protected def body(): Unit = blocking {
      val fOut  = f.replaceExt("png")
      if (!fOut.exists()) {
        val img = mkImage(config, frame = frame)
        ImageIO.write(img, "png", fOut)
      }
      progress = 1.0
    }
  }

  def renderImageSequence(config: Config): Processor[Unit] = {
    import config.resampleWindow
    require(resampleWindow % 2 == 1, s"resampleWindow ($resampleWindow) must be odd")
    val res = new RenderImageSequence(config = config)
    res.start()
    res
  }

  def mkImage(config: Config, frame: Int): BufferedImage = {
    val (w, h, imgCrop) = readFrame(config, frame)
    val imgUp     = imgCrop // mkResize (config, imgCrop)
    val imgNoise  = mkNoise  (config, imgUp)
    val arr       = mkThresh(config, imgNoise)
    bufferToImage(arr, w, h)
  }

  def bufferToImage(in: Array[Float], width: Int, height: Int, gain: Float = 1f): BufferedImage = {
    val res = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    var i = 0
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        val vOut    = (math.max(0f, math.min(1f, in(i) * gain)) * 255 + 0.5f).toInt
        val rgbOut  = 0xFF000000 | (vOut << 16) | (vOut << 8) | vOut
        res.setRGB(x, y, rgbOut)
        x += 1
        i += 1
      }
      y += 1
    }
    res
  }

//  def cropImage2(config: Config, in: BufferedImage): BufferedImage = {
//    import config.sizeIn
//    cropImage(in, 145 + (430 - sizeIn)/2, 20 + (430 - sizeIn)/2, sizeIn, sizeIn)
//  }

  def mkFIn(config: Config, frame: Int): File = {
    // val fIn   = fBase / "image_in"  / s"Resample${config.id}-%d.png"
    val fIn     = config.in
    val dirIn   = fIn .parent
    val childIn = fIn .name
    dirIn  / childIn.replace("%d", frame.toString)
  }

  def readFrame(config: Config, frame: Int): (Int, Int, Array[Float]) = {
    val fIn1      = mkFIn(config, frame)
    val imgIn     = ImageIO.read(fIn1)
    val imgCrop   = imgIn // cropImage2(config, imgIn)
    val w         = imgCrop.getWidth
    val h         = imgCrop.getHeight

    val res       = new Array[Float](w * h)

    var y = 0
    var t = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val rgbIn = imgCrop.getRGB(x, y)
        val vIn = (((rgbIn & 0xFF0000) >> 16) + ((rgbIn & 0x00FF00) >> 8) + (rgbIn & 0x0000FF)) / 765f // it's gray anyway
        res(t) = vIn
        x += 1
        t += 1
      }
      y += 1
    }

    (w, h, res)
  }

//  def mkResize(config: Config, in: BufferedImage): BufferedImage = {
//    import config.sizeOut
//    val resizeOp  = new ResampleOp(sizeOut, sizeOut)
//    resizeOp.filter(in, null)
//  }

  private[this] val rnd = new util.Random

  def mkNoise(config: Config, in: Array[Float]): Array[Float] = if (config.noise <= 0) in else {
//    val noiseOp = new NoiseFilter
//    noiseOp.setAmount(config.noise)
//    noiseOp.setMonochrome(true)
//    noiseOp.filter(in, null)
    val amt  = config.noise / 255f
    val amtH = amt/2
    var i = 0
    val out = new Array[Float](in.length)
    while (i < in.length) {
      val noise = rnd.nextFloat() * amt - amtH
      out(i) = in(i) + noise
      i += 1
    }
    out
  }

  def mkCopy(in: BufferedImage): BufferedImage = cropImage(in, 0, 0, in.getWidth, in.getHeight)

  def mkThresh(config: Config, in: Array[Float], out: Array[Float] = null): Array[Float] =
    if (config.thresh <= 0) in else {
      import config.thresh
//      val threshOp  = new ThresholdFilter(thresh)
//      val out1      = if (out != null) out else new BufferedImage(in.getWidth, in.getHeight, BufferedImage.TYPE_BYTE_BINARY)
//      threshOp.filter(in, out1)
      val res = if (out == null) new Array[Float](in.length) else out
      var i = 0
      val amt = thresh / 255f
      while (i < in.length) {
        res(i) = if (in(i) > amt) 1f else 0f
        i += 1
      }
      res
    }

  def printSum(prefix: String, in: Array[Float]): Unit = println(s"$prefix: ${in.sum}")


  private final class RenderImageSequence(config: Config)
    extends ProcessorImpl[Unit, RenderImageSequence] with Processor[Unit] {

    protected def body(): Unit = blocking {
//      val jsonF = fOut.replaceExt("json")
//      if (!jsonF.exists()) blocking {
//        val json    = Config.format.writes(config).toString()
//        val jsonOut = new FileOutputStream(jsonF)
//        jsonOut.write(json.getBytes("UTF-8"))
//        jsonOut.close()
//      }

      import config._

      val dirOut        = out.parent
      val childOut      = out.base
      val frameInMul    = if (lastFrame >= firstFrame) 1 else -1
      val frameSeq0     = firstFrame to lastFrame by frameInMul
      val frameSeq      = frameSeq0

      val numInFrames   = frameSeq.size // math.abs(lastFrame - firstFrame + 1)
      // val frameOff      = firstFrame // if (lastFrame >= firstFrame) firstFrame else lastFrame
      val numOutFrames  = numInFrames * 2

      var (widthIn0, heightIn0, frame0) = readFrame(config, frameSeq(0))
      val widthIn   = widthIn0
      val heightIn  = heightIn0
      val arrSize   = widthIn * heightIn

      // val imgOut        = new BufferedImage(widthIn, heightIn, BufferedImage.TYPE_BYTE_BINARY)

      def mkFOut(frame: Int): File = dirOut / s"$childOut-${frame + outputOffset - 1}.png"

      // e.g. resampleWindow = 5, winH = 2 ; LLLRR
      val rsmpWin = math.min(resampleWindow, 37)
      val winH    = rsmpWin / 2

      // assert (widthIn == sizeIn && heightIn == sizeIn)

      val frameWindow = Array.tabulate(rsmpWin) { i =>
        val j = i - winH
        if (j <= 0) frame0 else readFrame(config, frameSeq(j) /* j * frameInMul + frameOff*/)._3
      }

      frame0 = null // let it be GC'ed

      val resample    = dsp.Resample(dsp.Resample.Quality.High /* Medium */ /* Low */)
      val imgRsmp     = Array.fill(2)(new Array[Float](arrSize)) // new BufferedImage(widthIn, heightIn, BufferedImage.TYPE_BYTE_GRAY))
      val bufRsmpIn   = new Array[Float](rsmpWin)
      val bufRsmpOut  = new Array[Float](2)

      def performResample(): Unit = {
        var y = 0
        var z = 0
        while (y < heightIn) {
          var x = 0
          while (x < widthIn) {
            var t = 0
            while (t < rsmpWin) {
              val vIn = frameWindow(t)(z)
              bufRsmpIn(t) = vIn
              t += 1
            }
            resample.process(src = bufRsmpIn, srcOff = winH, dest = bufRsmpOut, destOff = 0, length = 2, factor = 2)
            var off = 0
            while (off < 2) {
              // note: gain factor 2 here!
              // val vOut    = (math.max(0f, math.min(1f, bufRsmpOut(off) * 2)) * 255 + 0.5f).toInt
              // val rgbOut  = 0xFF000000 | (vOut << 16) | (vOut << 8) | vOut
              imgRsmp(off)(z) = bufRsmpOut(off) * 2 // .setRGB(x, y, rgbOut)
              off += 1
            }
            x += 1
            z += 1
          }
          y += 1
        }
      }

      var frameIn  = rsmpWin - winH
      var frameOut = 0
      while (frameOut < numOutFrames) {
        val fOut1 = mkFOut(frameOut + 1)
        val fOut2 = mkFOut(frameOut + 2)

        if (!fOut1.exists() || !fOut2.exists()) {
          performResample()
          var off = 0
          while (off < 2) {
            val imgCrop   = imgRsmp(off)

            // printSum(s"rsmp($frameOut)", imgCrop)

            val imgUp     = imgCrop // mkCopy(imgCrop) // mkResize(config, imgCrop)
            val imgNoise  = mkNoise(config, imgUp)
            val imgOut1   = mkThresh(config, imgNoise, null) // imgOut)
            val imgOut2   = bufferToImage(imgOut1, widthIn, heightIn)
            ImageIO.write(imgOut2, "png", if (off == 0) fOut1 else fOut2)
            off += 1
          }
        }

        // handle overlap
        System.arraycopy(frameWindow, 1, frameWindow, 0, rsmpWin - 1)
        if (frameIn < numInFrames) {
          frameWindow(rsmpWin - 1) = readFrame(config, frameSeq(frameIn) /* frameIn * frameInMul + frameOff */)._3
        }

        frameIn  += 1
        frameOut += 2
        progress = frameIn.toDouble / numInFrames
        checkAborted()
      }
    }

    //    println("_" * 33)
    //    p.monitor(printResult = false)
    //
    //    waitForProcessor(p)
  }
}
