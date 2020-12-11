package chapter2

def parallel[A, B](a: =>A, b: =>B): (A, B) = {
  var aValue: A = null.asInstanceOf[A]
  var bValue: B = null.asInstanceOf[B]
  val t1 = thread { aValue = a }
  val t2 = thread { bValue = b }
  t1.join()
  t2.join()
  (aValue,bValue)
}

def periodically(duration: Long)(b: =>Unit): Unit = {
  val worker = new Thread {
    override def run(): Unit = {
      while(true) {
        b
        Thread.sleep(duration)
      }
    }
  }
  worker.setName("Worker")
  worker.setDaemon(true)
  worker.start()
}

object logger {
  def log(x: String): Unit = this.synchronized {
    println(x)
  }
}

class SyncVar[T] {
  
  private var content: T = null.asInstanceOf[T]
  private var empty = true
  def get(): T = this.synchronized {
    if (empty) {
      throw new Exception("it has to be non-empty")
    } else {
      empty = true
      val x = content
      content = null.asInstanceOf[T]
      x
    }
  }
  
  def put(x: T): Unit = this.synchronized {
    if(empty) {
      empty = false
      content = x
    } else throw new Exception("it has to be empty")
  }
  
  def isEmpty = this.synchronized {
    empty
  }
  
  def nonEmpty = this.synchronized {
    !empty
  }
  
  def getWait(): T = this.synchronized {
    while(empty) this.wait()
    empty = true
    val x = content
    content = null.asInstanceOf[T]
    this.notify()
    x
  }
  
  def putWait(x: T): Unit = this.synchronized {
    while(!empty) this.wait()
    empty = false
    content = x
    this.notify()
  }
  
}
object Exercise5 {
  def main(args: Array[String]): Unit = {
    val sync = new SyncVar[Int]
    
    val producer = thread {
      var x = 0
      while(x < 15) {
        sync.putWait(x)
        logger.log(s"put x  - ${x} in syncVar")
        x += 1
      }
    }

    val consumer = thread {
      var x = -1
      while (x < 14) {
        x = sync.getWait()
        logger.log(s"get t - ${x} from sync")
      }
    }

    producer.join()
    consumer.join()
    println("done")
  }
}

class SyncQueue[T](size: Int) {
  import scala.collection.mutable.Queue
  
  private var contents = new Queue[T](size)
  
  def get(): T = this.synchronized {
    while(contents.size == 0) this.wait()
    this.notify()
    contents.dequeue()
  }
  
  def put(x: T): Unit = this.synchronized {
    while(contents.size == size) this.wait()
    contents.enqueue(x)
    this.notify()
  }
}

object Exercise6 {
  def main(args: Array[String]): Unit = {
    val sync = new SyncQueue[Int](5)
    val producer = thread {
      var x = 0
      while(x < 15) {
        sync.put(x)
        logger.log(s"put x  - ${x} in syncQueue")
        x += 1
      }
    }

    val consumer = thread {
      var x = -1
      while (x < 14) {
        x = sync.get()
        logger.log(s"get t - ${x} from syncQueue")
        
      }
    }

    producer.join()
    consumer.join()
    println("done")
  }
}

object Exercise7 {
  import SynchronizedNesting.Account
  import scala.util.Random
  def sendAll(senders: Set[Account], receiver: Account): Unit = {
    val receiverUid = receiver.uid
    def adjust(sender: Account): Unit = {
      receiver.money += sender.money
      sender.money = 0
    }
    senders.map { sender =>
      val senderUid = sender.uid
      if (receiverUid < senderUid) {
        receiver.synchronized {
          sender.synchronized {
            adjust(sender)
          }
        }
      } else {
        sender.synchronized {
          receiver.synchronized {
            adjust(sender)
          }
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val accounts = (1 to 100).map(i => Account(name = s"account$i", money = 100)).toSet
    
    val receiver = Account("receiver", 1000)
    
    val t1 = thread {
      sendAll(accounts, receiver)
    }
    t1.join()
    accounts.foreach(a => println(a.money))
    println(s"receiver money is ${receiver.money}")
    
  }
  
}

class PriorityTaskPool(workerThreads: Int = 1, priority: Int = 1) {
  import scala.collection.mutable.PriorityQueue
  given Ordering[(Int, () => Unit)] = Ordering.by(_._1)
  private val tasks = new PriorityQueue[(Int, () => Unit)]
  @volatile private var terminated = false

  def asynchronous(priority: Int)(task: => Unit): Unit = tasks.synchronized {
    tasks.enqueue((priority, () => task))
    tasks.notify()
  }
  
  private def poll: Option[(Int, () => Unit)] = tasks.synchronized {
    while(tasks.isEmpty) tasks.wait()
    Some(tasks.dequeue())
  }
  
  def shutdown(): Unit = tasks.synchronized {
    terminated = true
    tasks.notify()
  }
  
  class Worker extends Thread {
    setDaemon(true)
    override def run(): Unit = {
      while(true) {
        poll.collect {
          case (p, task) if p >= priority || !terminated => task()
        }
      }
    }
  }
  (1 to workerThreads).map { _ =>
    Worker().start()
  }
  
}

object Exercise8 {
  val taskPool = new PriorityTaskPool

  def main(args: Array[String]): Unit = {
    (1 to 100).foreach(i => {
      taskPool.asynchronous(i){ println(s" priority is $i") }
    })
    Thread.sleep(10000)
  }
}

object Exercise9 {
  val taskPool = new PriorityTaskPool(10)

  def main(args: Array[String]): Unit = {
    (1 to 100).foreach { _ =>
      val priority = (Math.random() * 1000).toInt
      taskPool.asynchronous(priority){ println(s" priority is $priority") }
    }
    Thread.sleep(1000)
    
  }
}

object Exercise10 {
  val taskPool = new PriorityTaskPool(10, 1000)

  def main(args: Array[String]): Unit = {
    (1 to 100000).foreach { i =>
      val priority = (Math.random() * 1000).toInt
      taskPool.asynchronous(priority){ println(s" priority is $i") }
    }
    Thread.sleep(1)
    taskPool.shutdown()
  }
}

trait CBiMap[K, V] {
  def put(k: K, v: V): Option[(K, V)]
  def removeKey(k: K): Option[V]
  def removeValue(v: V): Option[K]
  def getValue(k: K): Option[V]
  def getKey(v: V): Option[K]
  def size: Int
  def iterator: Iterator[(K, V)]
}

class ConcurrentBiMap[K, V] extends CBiMap[K, V] {
  @volatile
  private val mapContents = scala.collection.mutable.Map[K, V]()
  
  override def put(k: K, v: V): Option[(K,V)] = mapContents.synchronized {
    mapContents.update(k, v)
    Some((k, v))
  }

  override def removeKey(k: K): Option[V] = mapContents.synchronized {
    mapContents.remove(k)
  }

  override def getKey(v: V): Option[K] = mapContents.synchronized {
    mapContents.filterInPlace((_, value) => value == v).headOption.map(_._1)
  }

  override def getValue(k: K): Option[V] = ???

  override def removeValue(v: V): Option[K] = ???

  override def iterator: Iterator[(K, V)] = ???

  override def size: Int = ???
  
}


