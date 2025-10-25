## Isolation levels

This project contains a simple database entity and associated repository with a number of integration
tests to highlight how different database isolation levels affect transaction reads.

The tests try to avoid using transactions spring typically provides us as these do sensible things, and
we use transaction templates with the relevant isolation level and spin of threads to allow concurrent
data access.