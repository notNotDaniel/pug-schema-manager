package pug.schema

import cats.effect.{ ExitCode, IO }
import munit.CatsEffectSuite
import doobie._
import doobie.implicits._

class ExamplesSuite extends CatsEffectSuite with doobie.munit.IOChecker {
  val transactor = Transactor.fromDriverManager[IO](
    "org.h2.Driver",
    "jdbc:h2:mem:examples;USER=sa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
  )

  test("Example step 1") {
    assertIO(RunStep1.run(), ExitCode.Success)
  }

  test("Example step 2") {
    assertIO(RunStep2.run(), ExitCode.Success)
  }

  test("Example step 3") {
    assertIO(RunStep3.run(), ExitCode.Success)
  }
}
