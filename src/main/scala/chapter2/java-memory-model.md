###Java Memory Model

- A language memory model is a specification that describes the circumstances under which
  write to a variable becomes visible to other threads. 
- A common misconception  -> we assume that a write to a variable c changes the corresponding
  memory location immediately after the processor executes it and other processors see the
  new value instantaneously. This is not how processors & compilers work. Writes rarely
  end up in main memory immediately. Compilers use registers/caches to postpone avoid memory
  writes to achieve optimal performance
- Scala inherits its memory model from JVM, which precisely specifies a set of `happens-before`
  relationships between different actions in a program.
  Different actions are,
   - Volatile variable reads & writes.
   - Acquiring and releasing object monitors
   - starting threads and waiting for their termination.
- If an action A happens before action B, then action B sees action A memory writes. It is the JVM's task
  to ensure this behavior irrespective of the machine it runs.
- #### Rules of Happens-before relationships
    
    - Program Order: Each action in a thread happens-before every other subsequent action in the Program
      order of that thread
    - Monitor locking: Unlocking a monitor happens-before every subsequent locking of that monitor.
    - Volatile fields: Write to a volatile field happens before every subsequent read of that volatile field.
    - Thread start: A call to `start()` on a thread happens-before any actions in the started thread.
    - Thread termination: Any action in a thread happens-before another thread completes a join on that thread.
    - Transitivity: if action A, happens-before B and B happens-before C, then A happens-before C
- Additionally, JMM guarantees that volatile R/W as well as monitor lock/unlocks are never re-ordered.
- Also, a non-volatile read cannot be reordered to appear before a volatile read(or monitor lock) that
  precedes it in the program order.
- A non-volatile write cannot be reordered to appear before a volatile write(or monitor unlock).
- If we are planning to implement higher level concurrency API's from the above primitives,
  It is the task of the programmer to ensure that every write isin happens-before relationship with every read.
  If we violate it, we will be introducing data races!!!!!!
- `final` variables do not need any synchronization b/w threads because of its immutability nature.
- Even though scala collection, List/Vector are immutable, we still need synchronization for it to be shared
  across threads. Because of non-final fields in its internal implementation.