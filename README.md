# automated-export-system-api-tests

API test suite for the automated-export-system service.

This repository contains Scala/ScalaTest API integration tests covering the
Automated Export System (AES) services.

## Prerequisites

- [Service Manager 2](https://github.com/hmrc/service-manager)
- sbt
- JDK 11+

## Running the tests locally

Start the required services using Service Manager 2:

```bash
sm2 --start AUTOMATED_EXPORT_SYSTEM_API_TESTS
```

Then run the test suite:

```bash
./run-tests.sh
```

`run-tests.sh` is also the command used by the Jenkins pipeline
(`automated-export-system-api-tests` job), configured with the
`AUTOMATED_EXPORT_SYSTEM_API_TESTS` Service Manager profile.

## Scalafmt

Check all project files are formatted as expected as follows:

```bash
sbt scalafmtCheckAll scalafmtCheck
```

Format `*.sbt` and `project/*.scala` files as follows:

```bash
sbt scalafmtSbt
```

Format all project files as follows:

```bash
sbt scalafmtAll
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").