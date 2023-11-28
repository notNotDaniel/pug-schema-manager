package pug.schema

import scala.language.implicitConversions

import doobie.{ ConnectionIO, Fragment }

/** One step in a migration */
case class MigrationSteps(effects: ConnectionIO[Unit]*) // FIXME: replace with any

object MigrationSteps {

  /** Build a MigrationStep from a `Seq[ConnectionIO[Unit]]` */
  implicit def toMigrationStep(effects: Seq[ConnectionIO[Unit]]): MigrationSteps  =
    new MigrationSteps(effects: _*)

  /** Build a MigrationStep from a `ConnectionIO[Unit]` */
  implicit def toMigrationStep(step: ConnectionIO[Unit]): MigrationSteps  =
    new MigrationSteps(step)

  /** Build a MigrationStep from a `Fragment` which is executed as an UPDATE */
  implicit def toMigrationStepFromFrag(step: Fragment): MigrationSteps =
    new MigrationSteps(step.update.run.map(_ => ()))

  /** Build a MigrationStep from a `Seq[Fragment]` where each fragment is executed as an UPDATE */
  implicit def toMigrationStepFromFrags(steps: Seq[Fragment]): MigrationSteps  = {
    new MigrationSteps(steps.map(step => step.update.run.map(_ => ())): _*)
  }
}
