# Milestone 1 Report

## (a) Negative number is set as PORT

### What happens?
When a negative number is set as port in `new SocketServer(int port)`, the constructor accepts it with no errors.
Which is not the best design as port number are required to **NOT** be negative.


### Fix?
The constructor should `throw` an `Exception` when a negative number is provided.
See [SocketServer](src/server/SocketServer.java) class for the correct implementation.

## (b) Not override the `run()` function

### What happens?
There is no error if we do not override the `run()` function

### Why?
This is because the parent class `Thread` has a default implementation of the `run()` function that is automatically inherited.
We however need to override the `run()` if we instead `implement Runnable` interface.