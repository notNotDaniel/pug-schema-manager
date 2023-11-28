package pug.schema

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite

import doobie._
import doobie.implicits._
import doobie.h2._
import _root_.munit._

class SchemaManagementSuite extends CatsEffectSuite with doobie.munit.IOChecker {

  val transactor = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "sa",
    ""
  )

  val schemaManagement = new SchemaManagement()

  test("Bootstrap schema-manager schema") {
    assertIO_ {
      schemaManagement.bootstrap()
        .transact(transactor)
    }
  }

  test("Fail on invalid SQL") {
    interceptIO[Exception] {
      object brokenComponent extends SchemaComponent("broken") {
        override def currentVersion = 1

        override val currentSchema = Seq(fr"""
            This is not valid SQL!
        """.update.run.map(_ => ())
        )
      }

      schemaManagement.initializeComponent(brokenComponent)
        .transact(transactor)
    }
  }

  test("Initialize a new schema component") {
    assertIO_ {
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 1

        override val currentSchema = Seq(fr"""
            CREATE TABLE foo (what TEXT NOT NULL);
        """.update.run.map(_ => ())
        )
      }

      schemaManagement.initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Check initial schema version") {
    assertIO(
      schemaManagement.getComponentVersion("test")
        .transact(transactor),
      Some(1)
    )
  }

  test("Fail if there is no upgrade path") {
    interceptIO[Exception] { // TODO: custom exception for this
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = Seq(fr"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """.update.run.map(_ => ())
        )
      }

      schemaManagement.initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Fail an invalid step") {
    interceptIO[Exception] { // TODO: custom exception for thi
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = Seq(fr"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """.update.run.map(_ => ())
        )

        override val migrations = {
          case (1, 2) => Seq(fr"""
            this is not valid SQL!
          """.update.run.map(_ => ())
          )
        }
      }

      schemaManagement.initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Upgrade via a single step") {
    assertIO_ {
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = Seq(fr"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """.update.run.map(_ => ())
        )

        override val migrations = {
          case (1, 2) => Seq(fr"""
            ALTER TABLE foo RENAME TO baz
          """.update.run.map(_ => ())
          )
        }
      }

      schemaManagement.initializeComponent(testComponent)
        .transact(transactor)
    }
  }
  test("Check updated schema version") {
    assertIO(
      schemaManagement.getComponentVersion("test")
        .transact(transactor),
      Some(2)
    )
  }

  test("Take the furthest step possible") {
    assertIO_ { // TODO: custom exception for thi
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 5

        override val currentSchema = Seq(fr"""
            CREATE TABLE qux (what TEXT NOT NULL);
        """.update.run.map(_ => ())
        )

        override val migrations = {
          case (1, 2) => Seq(fr"""
            ALTER TABLE foo RENAME TO baz
          """.update.run.map(_ => ())
          )
          case (2, 3) => Seq(fr"""
            this is not valid SQL!
          """.update.run.map(_ => ())
          )
          case (3, 4) => Seq(fr"""
            this is not valid SQL!
          """.update.run.map(_ => ())
          )
          case (2, 4) => Seq(fr"""
            ALTER TABLE baz RENAME TO blark
          """.update.run.map(_ => ())
          )
          case (4, 5) => Seq(fr"""
            ALTER TABLE blark RENAME TO qux
          """.update.run.map(_ => ())
          )
        }
      }

      schemaManagement.initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Check final schema version") {
    assertIO(
      schemaManagement.getComponentVersion("test")
        .transact(transactor),
      Some(5)
    )
  }
}
