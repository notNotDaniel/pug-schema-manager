package pug.schema

import java.math.BigInteger
import java.security.MessageDigest

import scala.annotation.tailrec

import cats.implicits._
import doobie._
import doobie.implicits._
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

/** Definition of the current version of a part of the schema */
abstract class SchemaComponent(val component: String) {
  val log: SelfAwareStructuredLogger[ConnectionIO] = LoggerFactory[ConnectionIO].getLogger

  /** The DB-level schema which contains this component. This can be used, e.g. in PostgreSQL,
    * to further namespace components. It is not supported by some databased, e.g. MySQL, in
    * which case it should be None
    */
  def dbSchemaName: Option[String] = None

  /** Current version, running will attempt to advance the version to this point */
  def currentVersion: Int

  /** Minimal version compatible with this one. This is set in the DB when we're upgraded,
    * to allow older compatible apps (or instances of the same app) to continue running.
    *
    * Defaults to the current version (no backwards comparability)
    */
  def minCompat: Int = currentVersion

  /** The current (complete) schema definition.
    *
    * This is applied if bootstrapping the schema from scratch.
    */
  val currentSchema: MigrationSteps

  /** Partial function from (fromVersion, toVersion) to the DB Actions required to migrate */
  val migrations: PartialFunction[(Int, Int), MigrationSteps] = PartialFunction.empty

  /** Get a name, prefixed with the DB schema name if necessary */
  protected def nameWithSchema(name: String):String =
    dbSchemaName.map(schema => s"$schema.$name").getOrElse(name)

  /** Collect the sequence of migrations that will be applied to bring the schema up to date.
    * The sequence is constructed by starting with the current version and finding the migration
    * which advances it as far as possible, then repeating from that new starting point, until
    * the current version is reached.
    */
  protected def findMigrationPath(from: Int, to: Int): List[(Int,Int)] = {
    @tailrec
    def tryNext(next: Int): List[(Int,Int)] = {
      if (from >= next)
        List()
      else if (!migrations.isDefinedAt((from, next)))
        tryNext(next-1)
      else
        (from,next)::findMigrationPath(next, to)
    }

    tryNext(to)
  }

  /** Execute a migration step by executing each SQL operation in sequence */
  def executeStep(steps: MigrationSteps): ConnectionIO[Unit] =
    steps.effects.sequence_

  /** Apply all the actions (in sequence) required to migrate from the given version to the
    * current one. Migrations are combined by searching for the migration from the starting version
    * to the latest possible version, applying that version, and repeating from there.
    */
  def migrateToCurrent(from: Int): ConnectionIO[Int] =
    findMigrationPath(from, currentVersion) match {
      case List() =>
        new Exception(s"No migration path for $component schema from version $from to $currentVersion")
          .raiseError[ConnectionIO, Int]

      case steps =>
        for {
          _ <- steps.foldLeft(().pure[ConnectionIO]) { (io, step) =>
            io.flatMap { _ =>
              for {
                _ <- log.info(s"Applying $component schema migration from ${step._1} -> ${step._2}")
                _ <- executeStep(migrations(step))
              } yield ()
            }
          }
          _ <- log.info(s"Successfully upgraded $component schema to current version ($currentVersion)")
        } yield currentVersion
    }

  /** Allow the specification of the code to be used for the checksum */
  def checksumSource(): Option[String] = None

  /** Determine the checksum for the current version of the schema */
  def currentChecksum(): Option[String] = checksumSource().map { schemaStr =>
    val bytes = MessageDigest.getInstance("MD5").digest(schemaStr.getBytes("UTF-8"))
    String.format("%032x", new BigInteger(1, bytes))
  }

  /** Display using the component name */
  override def toString = s"$component Schema"
}
