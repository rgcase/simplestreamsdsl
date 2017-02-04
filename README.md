# Simple Streams DSL

This is a small wrapper around the Akka Streams Graph DSL, exposing only a 
small subset of functionality.

A `Graph` is made up of a `Beginning` a `Middle` and an `End`. At the 
moment, a `Beginning` can only be created from an `Iterator`, a `Middle`
can only be created from a function, and an `End` can either ignore the 
elements in the stream, or perform a side-effecting function.

In addition, a `Beginning` and a `Middle` can be combined to create 
another `Beginning`, two `Middle`s can be combined consecutively or 
concurrently to create another `Middle`, and a `Middle` and an `End` can 
be combined to create an `End`.

## Examples
### Linear `Graph`

We'll first see a simple linear `Graph` with a single `Beginning`, `Middle`
and `End`. Names for each stage are optional.

```scala
import com.rgcase.streamsdsl._

val beginning = Beginning.fromIterator(Iterator.range(0, 5), "range 0 to 5")

val middle = Middle.fromFunction((x: Int) => x + 5, "plus 5")

val end = End.foreach((x: Int) => println(x), "println")
```

To build our `Graph`, we pass our `Beginning`, `Middle` and `End` to the
`Graph.build` method. Since this is a wrapper over Akka Streams, to run 
our `Graph` we'll see to pass an `ActorMaterializer`.

```scala
val graph = Graph.build(beginning, middle, end)

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()

graph.run

// 5
// 6
// 7
// 8
// 9
```

### Combining Stages

Combining a `Beginning` with a `Middle`:
```scala
val beginning: Beginning[Int] = Beginning.fromIterator(Iterator.range(0, 5), "range 0 to 5")
val middle: Middle[Int, String] = Middle.fromFunction((x: Int) => x.toString, "toString")

val newBeginning: Beginning[String] = beginning.to(middle)
```

Combining two `Middle`s consecutively:
```scala
val middle1: Middle[Int, Int] = Middle.fromFunction((x: Int) => x + 5, "plus 5")
val middle2: Middle[Int, String] = Middle.fromFunction((x: Int) => x.toString, "toString")

val middle: Middle[Int, String] = middle1.andThen(middle2)
```

Combining two `Middle`s concurrently is a little more complicated. We use a 
`MiddleBuilder` to build a set of concurrent `Middle`s, then build them into
a single `Middle`. For now, it is only possible to combine two `Middle`s at 
a time:
```scala
val middle1: Middle[Int, String] = Middle.fromFunction((x: Int) => x.toString, "toString")
val middle2: Middle[Int, Int] = Middle.fromFunction((x: Int) => x + 5, "plus 5")

val middle: Middle[Int, String] = MiddleBuilder.join(
  middle1,
  middle2,
  (x: String, y: Int) => x * y
).build()
```

Combining a `Middle` with an `End`:
```scala
val middle: Middle[Int, String] = Middle.fromFunction((x: Int) => x.toString, "toString")
val end: End[String] = End.foreach((x: String) => println(x), "println")

val newEnd: End[Int] = end.from(middle)
```

## Drawing the `Graph`
On any stage, or on a complete `Graph`, call the `draw` method for a visualization of
the `Graph`:

```scala
val beginning = Beginning.fromIterator(Iterator.range(0, 5), "range 0 to 5")
val beginning2 = beginning.to((x: Int) => x + 2, "plus 2")
val middle1 = Middle.fromFunction((x: Int) => x + 3, "plus 3")
val middle2 = Middle.fromFunction((x: Int) => x + 5, "plus 5")
val middle3 = MiddleBuilder.join(
  middle1,
  middle2,
  (x: Int, y: Int) => x + y
).build()
val end = End.foreach((x: Int) => println(x), "println")
val graph = Graph.build(beginning2, middle3, end)

graph.draw

// Beginning range 0 to 5
// ↓
// plus 2
// ↓          ↓
// plus 3     plus 5
// ↓
// End foreach println

```

