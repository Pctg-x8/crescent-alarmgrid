val scala3Version = "3.3.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "cdk",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "software.amazon.awscdk" % "aws-cdk-lib" % "2.72.1",
      "io.ct2" %% "cdkhelper" % "0.1.0-SNAPSHOT",
    ),
    resolvers += "cdkhelper package" at "https://maven.pkg.github.com/Pctg-x8/scala-cdkhelper",
    credentials += Credentials(Path.userHome / ".sbt" / "github_packages_credentials"),
  )
