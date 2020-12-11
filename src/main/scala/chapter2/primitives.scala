package chapter2

/*
  - It is the task of OS to assign executable parts of program to specific processors. This mechanism is called Multitasking.
    - Co-operative multi tasking - Programs are able to decide when to stop using the processor and yield contol to other programs.
              This is very difficult to achieve/implement.
    - Pre emptive multi tasking - (ost common). Each program is repitively assigned slices of execution time at a specific processor.
           This slices are called time slices.
  - Process: Instance of computer program that is being executed.The memory and other computational resources of one processor are
             are isolated from other processes.
  - Threads: Independent computations occuring in the same process. Normally threads > processors.
        Each thread describes the current state of program stack and the program counter during its execution.
        Program Stack contains sequence of method invocations that are currently being executed along with local variables and method params.
        Program counter describes the position of the current instruction in current method.
    **Note - When we say a thread is performing an action such doing some writes, what we mean is the processor executing that thread performs the action.
           How? - A processor advances the computation on the thread by manipulating the state of its stack by executing the instruction at the current program counter.
  - OS threads: are a programming facility provided by the OS, usually exposed through OS specific programming interface.
            Separate OS threads with in the same process share a region of memory and communicate by read/writing parts of that memory.
    In JVM context - threads === OS threads.
 */

/*
  - Thread states during its existence
    - When Thread object is created, it is initially in `new` state.
    - After the newly created thread object starts executing, it goes to `runnable` state
    - After the thread is done executing it goes to terminated state and cannot execute any more.
  - When start method is called on thread object -> This will eventually(no defined time) result in running the `run` method.
    Why? - First OS is notified that the thread must start executing. Then OS decides to assign the new thread to some processor(out of programs control)
  - If join method on a thread(say new) is called on another thread(say main). This halts the execution of main thread till new thread completes its execution.
    This is done by putting main thread in `waiting` state. This in turn causes it to relinquish its control over the processor.
    Also, All the writes to memory performed by the thread being joined occur before the join call returns and are visible to threads that call the join method.
  
 */

def thread(body: =>Unit): Thread = {
  val t = new Thread {
    override def run() = body
  }
  t.start()
  t
}
object ThreadCreation {
  
  class MyThread extends Thread {
    override def run(): Unit = println("New thread running")
  }
  def main(args: Array[String]): Unit = {
    val t: Thread =thread {
      println("new thread running")
    }
    println("in main thread")
    t.join()
    println(s"New thread joined and main is running")
  }
}


object ThreadUnProtectedUid {
  def main(args: Array[String]): Unit = {
    var uidCount = 0L
    def getUniqueId() = {
      val freshUid = uidCount + 1
      uidCount = freshUid
      freshUid
    }
    
    def printUniqueIds(n: Int): Unit = {
      val uids = for(i <- 0 until n) yield getUniqueId()
      println(s"uniqu Ids are $uids")
    }
    val t = thread { printUniqueIds(5) }
    // This causes race conditions, as getUniqueIds read/write is not atomic
    printUniqueIds(5)
    t.join
    // If we have printUniqueIds after join, then we will see uniqueIds. 
  }
}

/*
  synchronized: call ensures that the subsequent block of code can only execute if there is no other thread simultaneously executing this synchronized block of code or 
                any other synchronized block of code called on the same this object.
  - How JVM accomplishes this -> Every object created inside JVM has a special entity called intrinsic lock/monitor. This is used to ensure only on thread is executing
    the synchronized block by gaining ownership to the monitor. Once the thread completes executing the synchronized block of code, it release the monitor.
  - This is ond of the fundamental way of inter thread communication in JVM.
  - Writes in synchronized block of code are generally more expensive compared to unprotected writes, so there is a minor performance penalty.
  - Similar to join, all memory writes are visible to other threads which subsequently execute synchronized on the same object.
 */

object ThreadProtectedUid {
  var uidCount = 0L
  def getUniqueId() = this.synchronized {
    val freshUid = uidCount + 1
    uidCount = freshUid
    freshUid
  }
  def main(args: Array[String]): Unit = {
    

    def printUniqueIds(n: Int): Unit = {
      val uids = for(i <- 0 until n) yield getUniqueId()
      println(s"uniqu Ids are $uids")
    }
    val t = thread { printUniqueIds(15) }
    printUniqueIds(15)
    t.join
    
  }
}


object SynchronizedNesting {
  import scala.collection.mutable
  import ThreadProtectedUid._
  private val transfers = mutable.ArrayBuffer[String]()
  def logTransfer(name: String, n: Int) = transfers.synchronized {
    transfers += s"transfer to account $name and amount $n"
  }
  
  class Account(val name: String, var money: Int) {
    val uid = getUniqueId()
  }
  def add(account: Account, n: Int) = account.synchronized {
    account.money += n
    logTransfer(account.name, n)
  }

  def main(args: Array[String]): Unit = {
    val jack = Account("Jack", 100)
    val jill = Account("Jill", 100)
    val t1 = thread { add(jill, 5) }
    val t2 = thread { add(jack, 5) }
    val t3 = thread { add(jill, 70)}
    t1.join()
    t2.join()
    t3.join()
    println(s"jack account is ${jack.money}")
    println(s"jill account is ${jill.money}")
    println(s"transfers are $transfers")
  }
}

/*
  Deadlock - is a general situation in which two or more executions wait for each other to complete an action before proceeding with their own action.
  In concurrent programming when two threads obtain two separate monitors at the same time and then attempt to acquire the other threads monitor, then deadlock occurs
 */

object synchronizedDeadlock {
  import SynchronizedNesting.Account
  def send(sender: Account, receiver: Account, n: Int) = sender.synchronized {
    receiver.synchronized {
      sender.money -= n
      receiver.money += n
    }
  }

  def main(args: Array[String]): Unit = {
    val jack = Account("Jack", 100)
    val jill = Account("Jill", 100)
    val t1 = thread {
      for(i <- 0 until 100) send(jack, jill, 1)
    }
    val t2 = thread {
      for(i <- 0 until 100) send(jill, jack, 1)
    }
    t1.join()
    t2.join()
    println(s"jack account is ${jack.money}")
    println(s"jill account is ${jill.money}")
  }
}
/*
  Inorder to avoid deadlocks, we should establish a total order between resources when acquiring them, this ensure no set of threads cyclically wait on resources they previously acquired
  
 */
object SynchronizedOrdering {
  import SynchronizedNesting.Account
  def send(sender: Account, receiver: Account, n: Int) = {
    val (senderUid, receiverUid) = (sender.uid, receiver.uid)
    def adjust = {
      sender.money -= n
      receiver.money += n
    }
    if (senderUid < receiverUid) {
      sender.synchronized {
        receiver.synchronized {
          adjust
        }
      }
    } else {
      receiver.synchronized {
        sender.synchronized {
          adjust
        }
      }
    }
    
  }

  def main(args: Array[String]): Unit = {
    val jack = Account("Jack", 100)
    val jill = Account("Jill", 100)
    val t1 = thread {
      for(i <- 0 until 100) send(jack, jill, 3)
    }
    val t2 = thread {
      for(i <- 0 until 100) send(jill, jack, 1)
    }
    t1.join()
    t2.join()
    println(s"jack account is ${jack.money}")
    println(s"jill account is ${jill.money}")
  }
}


object SynchronizedBadPool {
  import scala.collection.mutable._
  private val tasks = new Queue[() => Unit]
  
  val worker = new Thread {
    def poll(): Option[() => Unit] = tasks.synchronized {
      if (tasks.nonEmpty) {
        Some(tasks.dequeue())
      } else None
    }

    // as the worker thread constantly polls for new tasks, we can say it will be in `busy waiting` state
    override def run(): Unit = while(true) {
      println("inside run method while loop")
      poll().map(task => task())
    }
  }
  worker.setName("Worker")
  worker.setDaemon(true)
  worker.start()
  
  def asynchronous(body: =>Unit) = tasks.synchronized {
    tasks.enqueue(() => body)
  }

  def main(args: Array[String]): Unit = {
    asynchronous { println("Hello World") }
    asynchronous { println("Hello World again!!!") }
    Thread.sleep(5000)
  }
}

/*
  Wait and notify - When a thread T calls wait on an object, it releases the monitor and goes into the waiting state until some thread S calls notify on the same object
  The thread S usually prepares some data for T.
  - An important property of wait method is that it can cause spurious wakeups. Some times JVM is allowed to wake up a thread that called wait even though there is no corresponding notify call.
    To guard against this, we always use wait in conjunction with while loop.
  - Guarded block - A synchronized statement in which some condition is repitively checked before calling `wait` is called a guarded block
 */
object SynchronizedGuardedBlocks {
  val lock = new AnyRef
  var message: Option[String] = None

  def main(args: Array[String]): Unit = {
    val greeter = thread {
      lock.synchronized {
        while(message == None) lock.wait()
        println(s"message is ${message.get}")
      }
    }
    lock.synchronized {
      message = Some("Hello")
      lock.notify();
    }
    greeter.join()
  }
}

object SynchronizedPool {
  import scala.collection.mutable.Queue
  private val tasks = new Queue[() => Unit]

  val worker = new Thread {
    def poll(): Option[() => Unit] = tasks.synchronized {
      while(tasks.isEmpty) tasks.wait()
      Some(tasks.dequeue())
    }

    override def run(): Unit = while(true) {
      println("inside run method while loop")
      poll().map(task => {
        println("calling inside poll method")
        task()
      })
    }
  }
  worker.setName("Worker")
  worker.setDaemon(true)
  worker.start()

  def asynchronous(body: =>Unit) = tasks.synchronized {
    tasks.enqueue(() => body)
    tasks.notify()
  }

  def main(args: Array[String]): Unit = {
    asynchronous { println("Hello World") }
    asynchronous { println("Hello World again!!!") }
    Thread.sleep(5000)
  }
  
}

/*
  GracefulShutdown - In graceful shutdown one thread sets the condition for the termination and then calls notify to wake up a wprler thread.
        The worker thread then releases all its resources and terminates willingly.
 */

object GracefulShutdown {
  import scala.collection.mutable.Queue
  private val tasks = new Queue[() => Unit]
  var terminated = false
  val worker = new Thread {
    
    def poll(): Option[() => Unit] = tasks.synchronized {
      while(tasks.isEmpty && !terminated) tasks.wait()
      if (!terminated) {
        Some(tasks.dequeue())
      } else None
    }

    import scala.annotation.tailrec
    
    @tailrec
    override def run(): Unit = poll() match {
      case Some(task) => {
        task()
        run()
      }
      case None =>
    }
  }
  worker.setName("Worker")
  worker.setDaemon(true)
  worker.start()

  def asynchronous(body: =>Unit) = tasks.synchronized {
    tasks.enqueue(() => body)
    tasks.notify()
  }
  
  def shutdown() = tasks.synchronized {
    terminated = true
    tasks.notify()
  }

  def main(args: Array[String]): Unit = {
    asynchronous { println("Hello World") }
    asynchronous { println("Hello World again!!!") }
  }
}

/*
  Volatile variables - 
   - Lightweight form of synchroniation provided by JVM
   - Writes/Reads from volatile variables cannot be reordered in a single thread
   - Writes to volatile variables are immediately visible to all other threads
 */