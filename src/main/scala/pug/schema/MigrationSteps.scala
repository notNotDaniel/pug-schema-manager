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

import doobie.{ConnectionIO, Fragment}

/** One step in a migration */
case class MigrationSteps(val effects: ConnectionIO[Unit]*) { // FIXME: replace with any
  def ++(other: MigrationSteps): MigrationSteps =
    MigrationSteps(effects ++ other.effects: _*)
}

object MigrationSteps {

  /** Build a MigrationStep from a `Seq[ConnectionIO[Unit]]` */
  implicit def toMigrationStep(effects: Seq[ConnectionIO[Unit]]): MigrationSteps =
    new MigrationSteps(effects: _*)

  /** Build a MigrationStep from a `ConnectionIO[Unit]` */
  implicit def toMigrationStep(step: ConnectionIO[Unit]): MigrationSteps =
    new MigrationSteps(step)

  /** Build a MigrationStep from a `Fragment` which is executed as an UPDATE */
  implicit def toMigrationStepFromFrag(step: Fragment): MigrationSteps =
    new MigrationSteps(step.update.run.map(_ => ()))

  /** Build a MigrationStep from a `Seq[Fragment]` where each fragment is executed as an UPDATE */
  implicit def toMigrationStepFromFrags(steps: Seq[Fragment]): MigrationSteps = {
    new MigrationSteps(steps.map(step => step.update.run.map(_ => ())): _*)
  }
}
