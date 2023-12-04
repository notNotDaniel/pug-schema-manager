/*
 * Copyright 2020 Pugilistic Codeworks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pug.schema

import munit.CatsEffectSuite
import doobie.implicits._
import pug.schema.TestHelpers._

class SchemaManagementSuite extends CatsEffectSuite with doobie.munit.IOChecker {
  val schemaManagement = new SchemaManagement()
  val transactor = testTransactor("mgmtTests")

  test("Bootstrap schema-manager schema") {
    assertIO_ {
      schemaManagement
        .bootstrap()
        .transact(transactor)
    }
  }

  test("Bootstrap schema-manager schema a second time") {
    assertIO_ {
      schemaManagement
        .bootstrap()
        .transact(transactor)
    }
  }

  test("Fail on invalid SQL") {
    interceptIO[Exception] {
      object brokenComponent extends SchemaComponent("broken") {
        def currentVersion = 1

        val currentSchema = sql"""
            This is not valid SQL!
        """
      }

      schemaManagement
        .initializeComponent(brokenComponent)
        .transact(transactor)
    }
  }

  test("Initialize a new schema component") {
    assertIO_ {
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 1

        override val currentSchema = sql"""
            CREATE TABLE foo (what TEXT NOT NULL);
        """
      }

      schemaManagement
        .initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Check initial schema version") {
    assertIO(
      schemaManagement
        .getComponentVersion("test")
        .transact(transactor),
      Some(1)
    )
  }

  test("Fail if there is no upgrade path") {
    interceptIO[Exception] { // TODO: custom exception for this
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = sql"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """
      }

      schemaManagement
        .initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Fail an invalid step") {
    interceptIO[Exception] { // TODO: custom exception for thi
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = sql"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """

        override val migrations = { case (1, 2) =>
          sql"""
            this is not valid SQL!
          """
        }
      }

      schemaManagement
        .initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Upgrade via a single step") {
    assertIO_ {
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 2

        override val currentSchema = sql"""
            CREATE TABLE baz (what TEXT NOT NULL);
        """

        override val migrations = { case (1, 2) =>
          sql"""
            ALTER TABLE foo RENAME TO baz
          """
        }
      }

      schemaManagement
        .initializeComponent(testComponent)
        .transact(transactor)
    }
  }
  test("Check updated schema version") {
    assertIO(
      schemaManagement
        .getComponentVersion("test")
        .transact(transactor),
      Some(2)
    )
  }

  test("Take the furthest step possible") {
    assertIO_ { // TODO: custom exception for thi
      object testComponent extends SchemaComponent("test") {
        override def currentVersion = 5

        override val currentSchema = sql"""
            CREATE TABLE qux (what TEXT NOT NULL);
        """

        override val migrations = {
          case (1, 2) => sql"""
            ALTER TABLE foo RENAME TO baz
          """

          case (2, 3) => sql"""
            this is not valid SQL!
          """

          case (3, 4) => sql"""
            this is not valid SQL!
          """

          case (2, 4) => sql"""
            ALTER TABLE baz RENAME TO blark
          """

          case (4, 5) => sql"""
            ALTER TABLE blark RENAME TO qux
          """
        }
      }

      schemaManagement
        .initializeComponent(testComponent)
        .transact(transactor)
    }
  }

  test("Check final schema version") {
    assertIO(
      schemaManagement
        .getComponentVersion("test")
        .transact(transactor),
      Some(5)
    )
  }
}
