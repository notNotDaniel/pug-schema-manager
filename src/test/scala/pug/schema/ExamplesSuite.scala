package pug.schema

import cats.effect.{ ExitCode, IO }
import munit.CatsEffectSuite
import pug.schema.TestHelpers._

class ExamplesSuite extends CatsEffectSuite with doobie.munit.IOChecker {
  def transactor = testTransactor("examples", true)

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
