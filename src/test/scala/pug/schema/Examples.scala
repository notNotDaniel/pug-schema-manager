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

import doobie.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import pug.schema.TestHelpers._

object Examples {

  /* In the beginning, we have a simple schema, which we'd like to create,
   * iff not yet present in the database.
   */
  object Step1 {
    object PetsSchema extends SchemaComponent("pets") {
      val currentVersion = 1

      val currentSchema = sql"""
          CREATE TABLE cats (
            name CHARACTER VARYING PRIMARY KEY,
            ennui REAL
          )
        """
    }
  }

  /* At some point we realize that there is more to cats than ennui,
   * and we'd like to add some information to reflect that.
   */
  object Step2 {
    object PetsSchema extends SchemaComponent("pets") {
      val currentVersion = 2

      val currentSchema = sql"""
        CREATE TABLE cats (
          name CHARACTER VARYING PRIMARY KEY,
          ennui REAL,
          frolic_factor REAL
        );
      """

      override val migrations = { case (1, 2) =>
        sql"""
          ALTER TABLE cats ADD COLUMN frolic_factor REAL;
          UPDATE cats SET frolic_factor = 1/ennui
        """
      }
    }
  }

  /* Arg!!! In some deployments, cats were assigned an ennui of 0, breaking our migration from
   * version 1 -> 2. Fortunately, the schema manager will apply the migration which advances the
   * version as far as possible, which we can use to advance past the problematic migration.
   */
  object Step3 {
    object PetsSchema extends SchemaComponent("pets") {
      val currentVersion = 3

      val currentSchema = sql"""
        CREATE TABLE cats (
          name CHARACTER VARYING PRIMARY KEY,
          ennui REAL CHECK (ennui > 0),
          frolic_factor REAL CHECK (frolic_factor > 0)
        );
      """

      override val migrations = {
        case (1, 2) => sql"""
          ALTER TABLE cats ADD COLUMN frolic_factor REAL;
          UPDATE cats SET frolic_factor = 1/ennui
        """

        case (2, 3) => sql"""
          UPDATE cats SET
            ennui = CASE WHEN ennui <= 0 THEN null ELSE ennui END,
            frolic_factor = CASE WHEN frolic_factor <= 0 THEN null ELSE frolic_factor END;
          ALTER TABLE cats ADD CHECK (ennui > 0);
          ALTER TABLE cats ADD CHECK (frolic_factor > 0);
        """

        // If we have not been able to advance from version 1, do some additional fixup,
        // then apply those steps.
        case (1, 3) =>
          sql"""UPDATE cats SET ennui = null WHERE ennui <= 0""" ++
            migrations((1, 2)) ++
            migrations((2, 3))
      }
    }
  }
}

class RunExample(examples: SchemaComponent*) extends IOApp {
  val schemaManagement = new SchemaManagement()
  def transactor = testTransactor("examples", true)

  def run(args: List[String] = List()): IO[ExitCode] =
    for {
      _ <- schemaManagement.bootstrap(examples: _*).transact(transactor)
    } yield ExitCode.Success
}

object RunStep1 extends RunExample(Examples.Step1.PetsSchema)
object RunStep2 extends RunExample(Examples.Step1.PetsSchema, Examples.Step2.PetsSchema)
object RunStep3 extends RunExample(Examples.Step1.PetsSchema, Examples.Step3.PetsSchema)
