import sbt.*

object Dependencies {

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "api-test-runner"         % "0.9.0",
    "uk.gov.hmrc"            %% "http-verbs-test-play-30" % "15.8.0",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "7.0.2",
    "org.playframework"      %% "play-pekko-http-server"  % "3.0.10"
  ).map(_ % Test)

}
