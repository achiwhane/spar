import java.util.concurrent.{ExecutorService, Future, TimeUnit}

package object Spar {
  type Par[A] = ExecutorService => Future[A]

  private case class UnitFuture[A](get: A) extends Future[A] {
    override def isDone =true
    override def get(timeout: Long, unit: TimeUnit): A = get
    override def isCancelled: Boolean = false
    override def cancel(mayInterruptIfRunning: Boolean): Boolean = false
  }

  private case class MapFuture[A, B, C](futA: Future[A], futB: Future[B], f: (A, B) => C) extends Future[C] {
    override def cancel(mayInterruptIfRunning: Boolean): Boolean =
      cancel(mayInterruptIfRunning)

    override def get(): C = {
      f(futA.get, futB.get)
    }

    override def get(timeout: Long, unit: TimeUnit): C = {
      val startTime = unit.convert(System.nanoTime(), TimeUnit.NANOSECONDS)
      val a = futA.get(timeout, unit)
      val endTime = unit.convert(System.nanoTime(), TimeUnit.NANOSECONDS)

      val b = futB.get(timeout - (endTime - startTime), unit)

      f(a, b)
    }

    override def isCancelled: Boolean =
      isCancelled

    override def isDone: Boolean = isDone
  }


  // TODO: use implicit conversions
  object Par {
    def unit[A](a: A): Par[A] = _ => UnitFuture(a)

    def lazyUnit[A](a: => A): Par[A] = fork(unit(a))

    def run[A](s: ExecutorService)(a: Par[A]): Future[A] = a(s)

    def fork[A](a: => Par[A]): Par[A] = es => es.submit { () => a(es).get }

    def map2[A, B, C](a: Par[A], b: Par[B])(f: (A, B) => C): Par[C] =
      es => {
        val futureA = a(es)
        val futureB = b(es)

        MapFuture(futureA, futureB, f)
      }

    def map[A, B](pa: Par[A])(f: A => B): Par[B] =
      map2(pa, unit(()))((a, _) => f(a))


    def asyncF[A, B](f: A => B): A => Par[B] =
      a => lazyUnit(f(a))

    def sequence[A](jobs: List[Par[A]]): Par[List[A]] =
      (es: ExecutorService) => {
        es.submit({
          () => {
            jobs.map(job => job(es)).map(_.get)
          }
        })
      }

    def parMap[A, B](xs: List[A])(f: A => B): Par[List[B]] =
      fork((es: ExecutorService) => {
        val jobs = xs.map(x => asyncF(f)(x))
        sequence(jobs)(es)
      })

    def parFilter[A](as: List[A])(f: A => Boolean): Par[List[A]] =
      fork((es: ExecutorService) => {
        val jobs = as.map(x => asyncF(f)(x))

        es.submit(
          () => {
            def helper(remainingJobs: List[Future[Boolean]], originalList: List[A], acc: List[A]): List[A] =
              remainingJobs match {
                case Nil => acc.reverse
                case x :: xs => helper(xs, originalList.tail, if (x.get) originalList.head :: acc else acc)
              }

            helper(jobs.map(job => job(es)), as, List[A]())
          })
        })

  }
}
