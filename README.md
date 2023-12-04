![CI](https://github.com/notNotDaniel/pug-schema-manager/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/works.pugcode/pug-schema-manager_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/works.pugcode/pug-schema-manager_2.13)
[![javadoc](https://javadoc.io/badge2/works.pugcode/pug-schema-manager_2.13/javadoc.svg)](https://javadoc.io/doc/works.pugcode/pug-schema-manager_2.13)

# Doobie Schema Manager

A simple toolkit for using [Doobie](https://tpolecat.github.io/doobie/index.html)
(and thus [Cats](https://typelevel.org/cats/)) to manage your database schemata. 

## Usage

The simplest way to use the schema manager is to embed it directly into your Cats app.
See [Examples.scala](src/test/scala/pug/schema/Examples.scala).

The examples can be run from the command line using `sbt`
```bash
sbt "Test/runMain pug.schema.RunStep1"
sbt "Test/runMain pug.schema.RunStep2"
sbt "Test/runMain pug.schema.RunStep3"
```
