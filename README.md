![CI](https://github.com/notNotDaniel/pug-schema-manager/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/works.pugcode/pug-schema-manager_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/works.pugcode/pug-schema-manager_2.13)
[![javadoc](https://javadoc.io/badge2/works.pugcode/pug-schema-manager_2.13/javadoc.svg)](https://javadoc.io/doc/works.pugcode/pug-schema-manager_2.13)

# Pug Schema Manager

A purely functional toolkit for using [Doobie](https://tpolecat.github.io/doobie/index.html)
to manage your database schemata.

## Documentation

Please see the [Documentation Site](https://notnotdaniel.github.io/pug-schema-manager/) for usage,
or check out the [Examples.scala](src/test/scala/pug/schema/Examples.scala) used in tests.

If you have the repository checked out locally, you can run the examples using `sbt`
```bash
sbt "Test/runMain pug.schema.RunStep1"
sbt "Test/runMain pug.schema.RunStep2"
sbt "Test/runMain pug.schema.RunStep3"
```
