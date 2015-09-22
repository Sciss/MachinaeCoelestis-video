/*
 * package.scala
 * (Miniaturen 15)
 *
 * Copyright (c) 2015 Hanns Holger Rutz. All rights reserved.
 *
 * This software and music is published under the
 * Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International License
 * (CC BY-NC-ND 4.0)
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.coelestis

import de.sciss.play.json.AutoFormat
import play.api.libs.json.{JsSuccess, JsError, JsArray, JsResult, JsValue, Format}

import scala.language.implicitConversions

package object text {
  object Config {
    implicit val format: Format[Config] = AutoFormat[Config]
  }
  case class Config(width: Int, height: Int, lineWidth: Int,
                    speedLimit: Double, noise: Double, threshold: Int)

  object Situation {
    implicit val format: Format[Situation] = AutoFormat[Situation]
  }
  case class Situation(config: Config, forceParameters: Map[String, Map[String, Float]], text: String) {
    override def toString = s"[$config, $forceParameters, $text]"
  }

  object KeyFrame {
    implicit val format: Format[KeyFrame] = AutoFormat[KeyFrame]

    implicit def fromTuple(tup: (Int, Map[String, Map[String, Float]])): KeyFrame =
      KeyFrame(frame = tup._1, situation = Situation(config = Text.config, forceParameters = tup._2, text = Text.text))
  }
  case class KeyFrame(frame: Int, situation: Situation) {
    override def toString = s"$frame: $situation"
  }

  // type Anim = Vec[(Int, Map[String, Map[String, Float]])]
  object Anim {
    def empty = Vector.empty[KeyFrame]

    implicit val format: Format[Anim] = new Format[Anim] {
      def reads(json: JsValue): JsResult[Anim] = json match {
        case JsArray(seq) =>
          ((JsSuccess(Anim.empty): JsResult[Anim]) /: seq) { (res, js) =>
            res.flatMap(in => KeyFrame.format.reads(js).map(in :+ _))
          }

        case _ => JsError("Not an array")
      }

      def writes(anim: Anim): JsValue = {
        val inner = anim.map(KeyFrame.format.writes)
        JsArray(inner)
      }
    }
  }
  type Anim = Vector[KeyFrame]
}
