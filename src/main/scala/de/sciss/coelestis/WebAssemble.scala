package de.sciss.coelestis

import de.sciss.file._

object WebAssemble {

  def main(args: Array[String]): Unit = {
    run()
  }

  import Assemble.mkLink

  def mkInputFrame (i: Int): File = file("assemble"     ) / s"frame-$i.png"

  def outBase = file("web_assemble")

  def run(): Unit = {
    val range1 = 16150 to 17100
    val range2 = 18950 to 20200
    val ranges = Seq(range1, range2)

    ranges.zipWithIndex.foreach { case (range, part0) =>
      val part = part0 + 1
      range.zipWithIndex.foreach { case (in, out0) =>
        val out = out0 + 1
        val partDir = outBase / s"part$part"
        if (!partDir.exists()) partDir.mkdirs()
        val outF = partDir / s"frame-$out.png"
        if (!outF.exists()) mkLink(mkInputFrame(in), outF)
      }
    }

    import sys.process._

    val fps       =  25
    val fadeDurEx = 10.0
    val fadeDurIn =  4.0

    def fadeFrames(dur: Double) = (dur * fps).toInt

    val ffEx = fadeFrames(fadeDurEx)
    val ffIn = fadeFrames(fadeDurIn)

    val partF = ranges.zipWithIndex.map { case (range, part0) =>
      val part = part0 + 1

      val partFmt = "h264" // mp4 not support for concat

      val inF  = outBase / s"part$part/frame-%d.png"
      val outF = outBase / s"web_$part.$partFmt"
      if (!outF.exists()) {
        def mkFilter(tpe: String, kv: (String, String)*): String = kv.map { case (k, v) => s"$k=$v" }
          .mkString(s"$tpe=", ":", "")

        def mkFilters(in: String*): String = in.mkString(",")

        val fadeInFrames  = if (part == 1) ffEx else ffIn
        val fadeOutFrames = if (part == 1) ffIn else ffEx

        val f = mkFilters(
          "transpose=2",
          mkFilter("fade", "type" -> "in" , "start_frame" -> "0", "nb_frames" -> fadeInFrames.toString),
          mkFilter("fade", "type" -> "out", "start_frame" -> (range.size - fadeOutFrames).toString,
            "nb_frames" -> fadeOutFrames.toString)
        )

        val cmd = Seq("avconv",
          "-i", inF.path,
          "-c:v", "libx264",
          "-r", fps.toString,
          "-vf", f,
          // "-f", partFmt,
          outF.path
        )
        println(cmd.mkString(" "))
        val code = cmd.!

        require(code == 0)
      }
      outF
    }

    val finalF = outBase / "out.mp4"
    if (!finalF.exists()) {
      val inPat = partF.mkString("concat:", "|", "")
      val cmd = Seq("avconv",
        "-i", inPat,
        "-c", "copy",
        "-f", "mp4",
        finalF.path
      )
      println(cmd.mkString(" "))
      val code = cmd.!
      require(code == 0)
    }
  }
}
