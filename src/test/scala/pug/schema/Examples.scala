package pug.schema

import doobie._
import doobie.implicits._
import cats.effect.{ ExitCode, IO, IOApp }

object Examples {
  val schemaManagement = new SchemaManagement()
  val transactor = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;USER=sa"
  )

  /* In the beginning, we have a simple schema, which we'd like to create,
   * iff not yet present in the database.
   */
  object Step1 {

    // The initial definition of our
    object PetsSchema extends SchemaComponent("pets") {
        val currentVersion = 1

        val currentSchema = sql"""
          CREATE TABLE cats (
            name text PRIMARY KEY,
            ennui real
          )
        """
    }

    object App extends IOApp {
      def run(args: List[String]): IO[ExitCode] =
        for {
          _ <- schemaManagement.bootstrap(PetsSchema).transact(transactor)
          v <- schemaManagement.getComponentVersion(PetsSchema).transact(transactor)
          _ <- IO.println(s"$PetsSchema present at version $v")
        } yield ExitCode.Success
    }
  }

  /* At some point we realize that there is more to cats than ennui,
   * and we'd like to add some information to reflect that.
   */
  object Step2 {

    // The initial definition of our
    object PetsSchema extends SchemaComponent("pets") {
      val currentVersion = 2

      val currentSchema = sql"""
        CREATE TABLE cats (
          name text PRIMARY KEY,
          ennui real,
          frolic_factor real
        );
      """

      override val migrations = {
        case (1, 2) => sql"""
          ALTER TABLE cats ADD COLUMN frolic_factor real;
          UPDATE cats SET frolic_factor = 1/ennui
        """
      }
    }

    object App extends IOApp {
      def run(args: List[String]): IO[ExitCode] =
        for {
          _ <- schemaManagement.bootstrap(PetsSchema).transact(transactor)
          v <- schemaManagement.getComponentVersion(PetsSchema).transact(transactor)
          _ <- IO.println(s"$PetsSchema present at version $v")
        } yield ExitCode.Success
    }
  }

  /* Arg!!! In some deployments, cats were assigned an ennui of 0, breaking our migration from
   * version 1 -> 2. Fortunately, the schema manager will apply the migration which advances the
   * version as far as possible, which we can use to advance past the problematic migration.
   * and we'd like to add some information to reflect that.
   */
  object Step3 {

    // The initial definition of our
    object PetsSchema extends SchemaComponent("pets") {
      val currentVersion = 3

      val currentSchema = sql"""
        CREATE TABLE cats (
          name text PRIMARY KEY,
          ennui real CHECK (ennui > 0),
          frolic_factor real CHECK (frolic_factor > 0),
        );
      """

      override val migrations = {
        case (1, 2) => sql"""
          ALTER TABLE cats ADD COLUMN frolic_factor real;
          UPDATE cats SET frolic_factor = 1/ennui
        """

        case (2, 3) => sql"""
          UPDATE cats SET
            ennui = CASE WHEN ennui <= 0 THEN null ELSE ennui END,
            frolic_factor = CASE WHEN frolic_factor <= 0 THEN null ELSE frolic_factor END;
          ALTER TABLE cats ADD CHECK (ennui > 0);
          ALTER TABLE cats ADD CHECK (frolic_factor > 0);
        """

        // If we have not been able to advance from version 1, so some additional fixup,
        // then apply those steps.
        case (1, 3) => Seq(
          sql"""UPDATE cats SET ennui = CASE WHEN ennui <= 0 THEN null ELSE ennui END""",
          migrations(1,2),
          migrations(2,3),
        )
      }
    }

    object App extends IOApp {
      def run(args: List[String]): IO[ExitCode] =
        for {
          _ <- schemaManagement.bootstrap(PetsSchema).transact(transactor)
          v <- schemaManagement.getComponentVersion(PetsSchema).transact(transactor)
          _ <- IO.println(s"$PetsSchema present at version $v")
        } yield ExitCode.Success
    }
  }
}
