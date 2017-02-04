package com.rgcase.streamsdsl

import akka.stream.{ ActorMaterializer, FlowShape }
import akka.{ Done, NotUsed }
import akka.stream.scaladsl._
import Graph.downArrow

import scala.concurrent.Future

sealed trait Beginning[In] { self ⇒
  protected[streamsdsl] val stage: Source[In, NotUsed]

  protected[streamsdsl] val drawLines: List[String]

  def draw = drawLines.mkString("\n") + "\n"

  def to[Out](f: In ⇒ Out): Beginning[Out] = to(f, "")
  def to[Out](f: In ⇒ Out, name: String): Beginning[Out] = new Beginning[Out] {
    val stage = self.stage.via(Flow.fromFunction(f))
    lazy val drawLines = self.drawLines ++ List(
      downArrow,
      name
    )
  }

  def to[Out](middle: Middle[In, Out]): Beginning[Out] = to(middle, "")
  def to[Out](middle: Middle[In, Out], name: String): Beginning[Out] = new Beginning[Out] {
    val stage = self.stage.via(middle.stage)
    lazy val drawLines = self.drawLines ++ List(
      downArrow,
      name
    )
  }

}

object Beginning {
  def fromIterator[In](iter: Iterator[In]): Beginning[In] = fromIterator(iter, "")
  def fromIterator[In](iter: Iterator[In], name: String): Beginning[In] = new Beginning[In] {
    val stage = Source.fromIterator(() ⇒ iter)
    lazy val drawLines = List("", s"Beginning $name")
  }
}

sealed trait Middle[In, Out] { self ⇒
  protected[streamsdsl] val stage: Flow[In, Out, NotUsed]
  protected[streamsdsl] val drawLines: List[String]
  def draw = drawLines.mkString("\n") + "\n"

  def andThen[Out2](middle: Middle[Out, Out2]): Middle[In, Out2] = andThen(middle, "")
  def andThen[Out2](middle: Middle[Out, Out2], name: String): Middle[In, Out2] = new Middle[In, Out2] {
    val stage = self.stage.via(middle.stage)
    lazy val drawLines = self.drawLines ++ List(
      downArrow,
      name
    )
  }
}

object Middle {
  def fromFunction[In, Out](f: In ⇒ Out): Middle[In, Out] = fromFunction(f, "")
  def fromFunction[In, Out](f: In ⇒ Out, name: String): Middle[In, Out] = new Middle[In, Out] {
    val stage = Flow.fromFunction(f)
    lazy val drawLines = List(
      downArrow,
      name
    )
  }

  def fromFlow[In, Out](flow: Flow[In, Out, NotUsed]): Middle[In, Out] = fromFlow(flow, "")
  def fromFlow[In, Out](flow: Flow[In, Out, NotUsed], name: String): Middle[In, Out] = new Middle[In, Out] {
    val stage = flow
    lazy val drawLines = List(
      downArrow,
      name
    )
  }
}

sealed trait MiddleBuilder[In, Out] { self ⇒
  protected[streamsdsl] val flow: Flow[In, Out, NotUsed]
  protected[streamsdsl] val drawLines: List[String]

  def build(): Middle[In, Out] = new Middle[In, Out] {
    val stage = self.flow
    val drawLines = self.drawLines
  }
}

object MiddleBuilder {
  def join[In, Out1, Out2, Out](mid1: Middle[In, Out1], mid2: Middle[In, Out2], f: (Out1, Out2) ⇒ Out) =
    new MiddleBuilder[In, Out] {
      val flow = Flow.fromGraph(GraphDSL.create() { implicit b: GraphDSL.Builder[NotUsed] ⇒
        import GraphDSL.Implicits._

        val bcast = b.add(Broadcast[In](2))
        val zip = b.add(Zip[Out1, Out2]())

        bcast.out(0).via(mid1.stage) ~> zip.in0
        bcast.out(1).via(mid2.stage) ~> zip.in1

        FlowShape(bcast.in, zip.out)
      }).map(f.tupled)
      lazy val drawLines = {
        val maxStageLength = mid1.drawLines.map(_.length).max
        val (left, right) = padLines(mid1.drawLines.map(padLine(_, maxStageLength)), mid2.drawLines)
        left.zip(right).map { case (l, r) ⇒ l + "     " + r }
      }
    }

  private def padLine(line: String, max: Int) = if (line.length >= max) line else line + " " * (max - line.length)
  private def padLines(left: List[String], right: List[String]): (List[String], List[String]) = {
    val leftLength = left.size
    val rightLength = right.size

    if (leftLength > rightLength) (left, right ++ List.fill(leftLength - rightLength)(""))
    else (left ++ List.fill(rightLength - leftLength)(""), right)
  }
}

sealed trait End[In] { self ⇒
  protected[streamsdsl] val stage: Sink[In, Future[Done]]
  protected[streamsdsl] val drawLines: List[String]

  def draw = drawLines.mkString("\n") + "\n"

  def from[From](middle: Middle[From, In]): End[From] = from(middle, "")
  def from[From](middle: Middle[From, In], name: String): End[From] = new End[From] {
    val stage =
      middle.stage
        .mapMaterializedValue(_ ⇒ Future.successful(Done))
        .to(self.stage)

    lazy val drawLines = List(
      downArrow,
      name
    ) ++ self.drawLines
  }
}

object End {
  def foreach[In](f: In ⇒ Unit): End[In] = foreach(f, "")
  def foreach[In](f: In ⇒ Unit, name: String): End[In] = new End[In] {
    val stage = Sink.foreach(f)
    lazy val drawLines = List(
      downArrow,
      s"End $name"
    )
  }
}

sealed trait Graph {

  protected[streamsdsl] val graph: RunnableGraph[Future[Done]]
  protected[streamsdsl] val drawLines: List[String]
  def draw = drawLines.mkString("\n") + "\n"

  def run(implicit materializer: ActorMaterializer) = graph.run()
}

object Graph {

  val downArrow = "\u2193"

  def build[In, Out](beginning: Beginning[In], mid: Middle[In, Out], end: End[Out]): Graph =
    new Graph {
      val graph =
        beginning.stage
          .via(mid.stage).mapMaterializedValue(_ ⇒ Future.successful(Done))
          .to(end.stage)

      lazy val drawLines =
        beginning.drawLines ++
          mid.drawLines ++
          end.drawLines
    }
}