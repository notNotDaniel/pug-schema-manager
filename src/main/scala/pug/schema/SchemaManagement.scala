package pug.schema

import java.math.BigInteger
import java.security.MessageDigest

import cats.implicits._
import doobie._
import doobie.implicits._
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

/** One step in a migration */
case class MigrationSteps(effects: ConnectionIO[Unit]*) // FIXME: replace with any

object MigrationSteps {

  /** Build a MigrationStep from a `Seq[ConnectionIO[Unit]]` */
  implicit def toMigrationStep(steps: Seq[ConnectionIO[Unit]]) =
    new MigrationSteps(steps: _*)

  /** Build a MigrationStep from a `ConnectionIO[Unit]` */
  implicit def toMigrationStep(step: ConnectionIO[Unit]) =
    new MigrationSteps(step)

  /** Build a MigrationStep from a `Fragment` which is executed as an UPDATE */
  implicit def toMigrationStepFromFrag(step: Fragment) =
    new MigrationSteps(step.update.run.map(_ => ()))

  /** Build a MigrationStep from a `Seq[Fragment]` where each fragment is executed as an UPDATE */
  implicit def toMigrationStepFromFrags(steps: Seq[Fragment]) = {
    val effects: Seq[ConnectionIO[Unit]] = steps.map(step => step.update.run.map(_ => ()))
    new MigrationSteps(effects: _*)
  }
}


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
    def tryNext(next: Int): List[(Int,Int)] = {
      if (from >= next)
        List()
      else if (!migrations.isDefinedAt(from, next))
        tryNext(next-1)
      else
        (from,next)::findMigrationPath(next, to)
    }

    tryNext(to)
  }

  /** Execute a migration step by executing each SQL operation in sequence */
  def executeStep(step: MigrationSteps): ConnectionIO[Unit] = {
    step.effects.foldLeft(().pure[ConnectionIO]) { (io, sql) =>
      io.flatMap(_ => sql)
    }.map(_ => ())
  }

  /** Apply all the actions (in sequence) required to migrate from the given version to the
    * current one. Migrations are combined by searching for the migration from the starting version
    * to the latest possible version, applying that version, and repeating from there.
    */
  def migrateToCurrent(from: Int): ConnectionIO[Int] = {

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
  }

  /** Allow the specification of the code to be used for the checksum */
  def checksumSource(): Option[String] = None

  /** Determine the checksum for the current version of the schema */
  def currentChecksum(): Option[String] = checksumSource().map { schemaStr =>
    val bytes = MessageDigest.getInstance("MD5").digest(schemaStr.getBytes("UTF-8"))
    String.format("%032x", new BigInteger(1, bytes))
  }
}

class SchemaManagement(schema: Option[String] = None) {
  protected val log: SelfAwareStructuredLogger[ConnectionIO] = LoggerFactory[ConnectionIO].getLogger

  def schemaMetadataTableName = "schema_metadata"

  val createSchemaMetadataTable =
    fr"""CREATE TABLE""" ++ Fragment.const (schemaMetadataTableName) ++ fr"""(
        component   VARCHAR PRIMARY KEY,
        version     INTEGER NOT NULL,
        min_compat  INTEGER NOT NULL,
        checksum    VARCHAR
    )"""

  protected def booleanCount(desc: String)(results: Seq[Int]): Boolean = results match {
    case Seq(0) => false
    case Seq(1) => true
    case x      => throw new Exception(s"Expected 0 or 1 entries for $desc, found $x")
  }

  protected def schemaExists(schemaName: String): ConnectionIO[Boolean] =
    sql" select count(*) from information_schema.schemata where schema_name = $schemaName"
      .query[Int]
      .to[Seq] map booleanCount(s"$schemaName in information_schema.schemata")

  protected def tableExistsInAnySchema(tableName: String): ConnectionIO[Boolean] =
    sql"select count(*) from information_schema.tables where table_name = $tableName"
      .query[Int]
      .to[Seq] map booleanCount(s"$tableName in information_schema.tables")

  protected def tableExistsInSchema(schemaName: String, tableName: String): ConnectionIO[Boolean] =
    sql"select count(*) from information_schema.tables where table_schema = $schemaName and table_name = $tableName"
      .query[Int]
      .to[Seq] map booleanCount(s"$schemaName.$tableName in information_schema.tables")

  protected def createSchemaIfNecessary(schemaOpt: Option[String]): ConnectionIO[Unit] = schemaOpt match {
    case Some(schemaName) =>
      schemaExists(schemaName).flatMap {
        case true  => ().pure[ConnectionIO]
        case false => sql"create schema if not exists #$schemaName".update.run.map(_ => ())
      }

    case None => ().pure[ConnectionIO]
  }

  protected def initializeSchemaManagement(): ConnectionIO[Unit] = {
    for {
      // Create the schema if requested, and does not yet exist
      _ <- createSchemaIfNecessary(schema)

      // See if the metadata table exists
      exists <- schema match {
        case None             => tableExistsInAnySchema(schemaMetadataTableName)
        case Some(schemaName) => tableExistsInSchema(schemaName, schemaMetadataTableName)
      }

      // Create the metadata table if necessary
      _ <- if (exists) ().pure[ConnectionIO] else {
        for {
          _ <- log.info("Initializing schema metadata tables")
          _ <- createSchemaMetadataTable.update.run
        } yield ()
      }
    } yield ()
  }

  def getComponentVersion(componentName: String): ConnectionIO[Option[Int]] =
    (fr"SELECT version from" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
          WHERE component = $componentName
    """).query[Int].option

  protected def updateComponentVersion(componentName: String, ver: Int): ConnectionIO[Boolean] =
    (fr"UPDATE" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
          SET version = $ver
          WHERE component = $componentName
    """).update.run.map(_ > 0)

  def initializeComponent(component: SchemaComponent): ConnectionIO[Unit] = {
    val name = component.component
    val checksum = component.currentChecksum()
    val checksumStr = checksum.getOrElse("<none>")
    val currentVersion = component.currentVersion
    (fr"""
      SELECT version, min_compat, checksum
      FROM""" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
      WHERE component = $name
      FOR UPDATE
      """)
      .query[(Int,Int,Option[String])]
      .option
      .flatMap {
        case None =>
          for {
            _ <- log.info(s"$name schema not present, creating version $currentVersion (checksum $checksumStr)...")
            _ <- createSchemaIfNecessary(component.dbSchemaName)
            _ <- component.executeStep(component.currentSchema)
            _ <- (fr"""
                 INSERT INTO """ ++ Fragment.const(schemaMetadataTableName) ++ fr"""
                   (component, version, min_compat, checksum)
                   VALUES ($name, $currentVersion, ${component.minCompat}, $checksum)
              """).update.run.map(_ => ())
          } yield ()

        case Some((dbVer, minCompat, dbChecksumOpt)) =>
          if (dbVer == currentVersion) {
            val name = component.component
            (checksum, dbChecksumOpt) match {
              case (Some(codeChecksum), Some(dbChecksum)) =>
                if (dbChecksum == codeChecksum) {
                  for {
                    _ <- log.info(s"$name schema is current at version $dbVer (checksum $codeChecksum)")
                    _ <- ().pure[ConnectionIO]
                  } yield ()
                }
                else {
                  new Exception(s"$name schema version $dbVer has checksum $dbChecksum in db, expected to be $codeChecksum")
                    .raiseError[ConnectionIO, Unit]
                }

              case (_, None) =>
                for {
                  _ <- log.warn(s"$name schema version $currentVersion has checksum $checksumStr, but no checksum in db, allowing to continue")
                  _ <- ().pure[ConnectionIO]
                } yield ()

              case (None, Some(_)) =>
                for {
                  _ <- log.warn(s"$name schema version $currentVersion has no checksum, but checksum in db, allowing to continue")
                  _ <- ().pure[ConnectionIO]
                } yield ()
            }
          }
          else if (currentVersion < dbVer) {
            if (currentVersion < minCompat) {
              val e = new Exception(s"$name schema version $currentVersion is not compatible with current schema (min compatible version is $minCompat)")
              for {
                _ <- log.error(s"$name schema validation failed: ${e.getMessage}")
                _ <- e.raiseError[ConnectionIO, Unit]
              } yield ()
            }
            else {
              for {
                _ <- log.warn(s"$name schema compatible, allowing to continue (db = $dbVer, min compat = $minCompat, app = $currentVersion)")
                _ <- ().pure[ConnectionIO]
              } yield ()
            }
          }
          else {
            component
              .migrateToCurrent(dbVer)
              .flatMap(v => updateComponentVersion(component.component, v)
              .map(_ => ()))
          }
    }
  }

  def bootstrap(components: SchemaComponent*): ConnectionIO[Unit] = {
    for {
      _ <- initializeSchemaManagement()
      _ <- components.foldLeft(().pure[ConnectionIO]) { case (io,c) =>
          io.flatMap(_ => initializeComponent(c))
        }
    }
    yield ()
  }
}
