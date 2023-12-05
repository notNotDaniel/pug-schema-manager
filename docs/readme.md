# Doobie Schema Manager

## Quickstart

Add the necessary dependency, e.g. in `build.sbt`

```scala
libraryDependencies += "works.pugcode" % "pug-schema-manager" %% "@VERSION@"
```

Define a schema component. This is a set of related SQL which should be versioned (and migrated)
together. 

At a minimum, this must include the `currentVersion` number (an integer which is 
incremented each time a migration needs to be applied), and `currentSchema`, which is the complete
definition of the current version of the schema, and will be applied if the schema does not yet
exist in any form. 

You may also define a set of migrations, which specify the incremental steps to migrate from previous
versions of the schema to the current one. These are defined as a partial function from a
`(fromVersion, toVersion)` tuple to the migration steps to be performed. Migrations are applied
atomically, in order, to advance the database's current version to the one defined by code. If
migrations exist to advance more than one version, the migration which advances the current version
as far as possible will be applied.

```scala mdoc
import doobie.implicits._
import pug.schema._

object PetsSchema extends SchemaComponent("pets") {
  val currentVersion = 2

  val currentSchema = sql"""
    CREATE TABLE cats (
      name CHARACTER VARYING PRIMARY KEY,
      ennui REAL CHECK (ennui > 0),
      frolic_factor REAL CHECK (frolic_factor > 0)
    );
  """

  override val migrations = {
    case (1, 2) => sql"""
      ALTER TABLE cats ADD COLUMN frolic_factor REAL 
        CHECK (frolic_factor > 0);
    """
  }
}
```

The metadata maintained by the schema manager, as well as any required migrations, can be applied
by executing the effect returned from the `SchemaManager.bootstrap()` method

```scala mdoc
import cats.effect.{ExitCode, IO, IOApp}
import doobie._
import doobie.implicits._
import pug.schema._

object MyPetsApp extends IOApp {
  val schemaManagement = new SchemaManagement()
  def transactor = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:example;USER=sa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
  )

  def run(args: List[String] = List()): IO[ExitCode] =
    for {
      _ <- schemaManagement.bootstrap(PetsSchema).transact(transactor)
    } yield ExitCode.Success
}
```

## Specifying Migration Actions

Migration steps (as well as the initial schema creation) can be specified in a variety of ways:

* Ultimately, all migration steps reduce to a sequence of Doobie `ConnectionIO[Unit]`, which are 
  applied transactionally.
    ```scala mdoc
    val stepX = 
      for {
        id <- sql"insert into my_table (name) values ($name)"
                .update
                .withUniqueGeneratedKeys[Int]("id")
        _  <- sql"insert into my other table (name, id) values ($name, $id)"
                .update
      } yield ()
    ```

* Steps may also be specified as Doobie as simple `sql` fragments, e.g.
    ```scala mdoc
    val stepY = sql"UPDATE foo SET bar = baz"
    ```
