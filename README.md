## Isolation levels

This project contains a simple database entity and associated repository with a number of integration
tests to highlight how different database isolation levels affect transaction reads.

The tests try to avoid using transactions spring typically provides us as these do sensible things, and
we use transaction templates with the relevant isolation level and spin of threads to allow concurrent
data access.

This section describes potential read phenomena and how different isolation levels allow or prevent
the negative side effects of the phenomena.\
The examples work with table t1 shown below, populated with the given data.

| id | x  |
|----|----|
| 1  | a |
| 2  | b |

### Dirty reads
A dirty read is where a transaction reads a row that has been updated by another transaction but not committed.
As seen in the example below, this can be bad if the second transaction rolls back changes.

| Transaction 1                                                                                                                                                                                                                                    | Transaction 2                                        |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| begin;<br/>select x from t1 where id = 1;<br/>-- gets 'a'                                                                                                                                                                                        |                                                      |
|                                                                                                                                                                                                                                                  | update t1 set x = 'z' where id = 1;<br/>-- no commit |
| select x from t1 where id = 1;<br/>-- READ UNCOMMITTED gets 'z' (dirty read)<br/>-- READ COMMITTED gets 'a' (dirty read avoided)<br/>-- REPEATABLE READ gets 'a' (dirty read avoided)<br/>-- SERIALIZABLE gets 'a' (dirty read avoided)<br/>end; |                                                      |
|                                                                                                                                                                                                                                                  | rollback;                                            |

### Non-repeatable reads
A non-repeatable read is when a transaction reads a row twice but another transaction commits changes to said row between the reads.

| Transaction 1                                                                                                                                                        | Transaction 2                                                   |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| BEGIN;<br/>SELECT x FROM t1 WHERE id = 1;<br/> -- gets 'a'                                                                                                           |                                                                 |
|                                                                                                                                                                      | BEGIN;<br/>UPDATE t1 SET x = 'z' WHERE id = 1;<br/>COMMIT;<br/> |
| SELECT x FROM t1 WHERE id = 1;<br/>--READ UNCOMMITTED gets 'z'<br/>--READ COMMITTED gets 'z'<br/> --REPEATABLE READ gets 'a'<br/>--SERIALIZABLE gets 'a'<br/>COMMIT; |                                                                 |

### Phantom reads
A phantom read occurs when a transaction retrieves a set of rows twice and new rows are inserted into 
or removed from that set by another transaction that is committed in between.

| Transaction 1                                                                                                                                                                                                                                                    | Transaction 2                                         |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| begin;<br/>select x from t1 where x > 0;<br/>-- gets 'a' and 'b'                                                                                                                                                                                                 |                                                       |
|                                                                                                                                                                                                                                                                  | begin;<br/>insert into t1 values (3,'c');<br/>commit; |
| select x from t1 where x > 0;<br/>--READ UNCOMMITTED gets 'a', 'b', 'c' (phantom read)<br/>--READ COMMITTED gets 'a', 'b', 'c' (phantom read)<br/>--REPEATABLE READ gets 'a', 'b', 'c' (phantom read)<br/>--SERIALIZABLE gets 'a' and 'b' (phantom read avoided) |                                                       |
