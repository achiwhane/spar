import java.util.concurrent.{Executor, ExecutorService, Executors}

import Spar._

object SparTest {
  def main(args: Array[String]): Unit = {
    val es = Executors.newFixedThreadPool(math.max(1, Runtime.getRuntime.availableProcessors() - 1))
    val foo = List[Int](1, 2, 3)

    val mappedFoo = Par.parMap(foo)(_ * 2)(es).get()

    println(mappedFoo)


    val foo2 = List[Int](7, 7, 7, 7, 7, 7,7)
    val filteredFoo2 = Par.parFilter(foo2)(_ != 7)

    println(Par.run(es)(filteredFoo2).get())
  }
}
