val Http4sVersion          = "0.23.27"
val CirceVersion           = "0.14.9"
val MunitVersion           = "1.0.0"
val LogbackVersion         = "1.5.6"
val MunitCatsEffectVersion = "2.0.0"
val BouncycastleVersion    = "1.78.1"
val Fs2Version             = "3.10.2"
val MapDbVersion           = "3.1.0"
val ArcadeDBVersion = "24.6.1"

lazy val commonSettings = Seq(
  organization      := "io.github.nandotorterolo",
  version           := "0.0.1-SNAPSHOT",
  scalaVersion      := "2.13.14",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
//  Compile / run / fork := true,
  scalacOptions := Seq(
    // Warnings as errors
//      "-Xfatal-warnings",
    "-Wunused:implicits",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:params",
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.3" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  assembly / assemblyMergeStrategy := {
    case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
    case "module-info.class"                     => MergeStrategy.discard
    case x                                       => (assembly / assemblyMergeStrategy).value.apply(x)
  }
)
lazy val all = (project in file("."))
  .aggregate(common, node, cli)

lazy val common =
  project
    .in(file("common"))
    .settings(
      commonSettings,
      name    := "common",
      version := "0.1",
      libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-circe"        % Http4sVersion,
        "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
        "io.circe"        %% "circe-generic"       % CirceVersion,
        "io.circe"        %% "circe-literal"       % CirceVersion,
        "io.circe"        %% "circe-config"        % "0.10.1",
        "org.bouncycastle" % "bcprov-jdk18on"      % BouncycastleVersion,
        "co.fs2"          %% "fs2-io"              % Fs2Version,
        "org.scodec"      %% "scodec-core"         % "1.11.10",
        "org.scodec"      %% "scodec-bits"         % "1.2.0",
        "org.scodec"      %% "scodec-cats"         % "1.2.0",
        "org.mapdb"        % "mapdb"               % MapDbVersion,
        "org.scalameta"   %% "munit"               % MunitVersion           % Test,
        "org.typelevel"   %% "munit-cats-effect"   % MunitCatsEffectVersion % Test,
        "ch.qos.logback"   % "logback-classic"     % LogbackVersion         % Runtime,
        "org.scalameta"    % "svm-subs"            % "101.0.0"
      )
    )

lazy val node = (project in file("node"))
  .settings(
    name := "node",
    commonSettings,
    Compile / run / fork := true,
    javaOptions ++= Seq(
      "-Darcadedb.server.rootPassword=root1234",
      "-Darcadedb.server.databaseDirectory=/home/fernando/development/thaConstellation/ArcadeDb",
      "-Darcadedb.server.defaultDatabases=Node[node:admin1234]",
    ),
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "io.circe"        %% "circe-literal"       % CirceVersion,
      "io.circe"        %% "circe-config"        % "0.10.1",
      "org.bouncycastle" % "bcprov-jdk18on"      % BouncycastleVersion,
      "co.fs2"          %% "fs2-io"              % Fs2Version,
      "org.scodec"      %% "scodec-core"         % "1.11.10",
      "org.scodec"      %% "scodec-bits"         % "1.2.0",
      "org.scodec"      %% "scodec-cats"         % "1.2.0",
      "org.mapdb"        % "mapdb"               % MapDbVersion,
      "com.arcadedb"     % "arcadedb-server"     % ArcadeDBVersion,
      "com.arcadedb"     % "arcadedb-studio"     % ArcadeDBVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect"   % MunitCatsEffectVersion % Test,
      "ch.qos.logback"   % "logback-classic"     % LogbackVersion         % Runtime,
      "org.scalameta"    % "svm-subs"            % "101.0.0"
    )
  )
  .dependsOn(common % "test->test;compile->compile")

lazy val cli = (project in file("cli"))
  .settings(
    name := "cli",
    commonSettings,
    Compile / run / fork       := false,
    assembly / assemblyJarName := "cli.jar",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "io.circe"        %% "circe-literal"       % CirceVersion,
      "io.circe"        %% "circe-config"        % "0.10.1",
      "org.bouncycastle" % "bcprov-jdk18on"      % BouncycastleVersion,
      "co.fs2"          %% "fs2-io"              % Fs2Version,
      "org.scodec"      %% "scodec-core"         % "1.11.10",
      "org.scodec"      %% "scodec-bits"         % "1.2.0",
      "org.scodec"      %% "scodec-cats"         % "1.2.0",
      "org.mapdb"        % "mapdb"               % MapDbVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect"   % MunitCatsEffectVersion % Test,
      "ch.qos.logback"   % "logback-classic"     % LogbackVersion         % Runtime,
      "org.scalameta"    % "svm-subs"            % "101.0.0"
    )

  )
  .dependsOn(common % "test->test;compile->compile")
