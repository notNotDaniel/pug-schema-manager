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

import cats.effect.ExitCode
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
