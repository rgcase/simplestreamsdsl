# Simple Streams DSL

This is a small wrapper around the Akka Streams Graph DSL, exposing only a 
small subset of functionality.

A `Graph` is made up of a `Beginning` a `Middle` and an `End`. At the 
moment, a `Beginning` can only be created from an `Iterator`, a 