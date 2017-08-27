// *****************************************************************************
// Projects
// *****************************************************************************

import com.typesafe.sbt.SbtGit.GitCommand
import de.heikoseeberger.sbtheader.FileType
import play.twirl.sbt.Import.TwirlKeys

// Calculate the current year for usage in copyright notices and license headers.
lazy val currentYear: Int = java.time.OffsetDateTime.now().getYear

lazy val tenseiFrontend = project
  .in(file("."))
  .settings(settings)
  .settings(
    name := "tensei-frontend",
    shellPrompt := GitCommand.prompt,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, BuildInfoKey.action("buildTime") { java.time.ZonedDateTime.now() }),
    buildInfoPackage := "com.wegtam.tensei",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      cache,
      filters,
      ws,
      library.akkaCamel,
      library.akkaCluster,
      library.akkaClusterTools,
      library.akkaSlf4j,
      library.akkaQuartzScheduler,
      library.c3p0,
      library.camelRestlet,
      library.camelFtp,
      library.dfasdlCore,
      library.dfasdlUtils,
      library.h2,
      library.jBCrypt,
      library.logbackClassic,
      library.play2auth,
      library.playSlick,
      library.playSlickEvolutions,
      library.postgresql,
      library.tenseiApi,
      library.webJarBootbox,
      library.webJarBootstrap,
      library.webJarD3js,
      library.webJarHandlebars,
      library.webJarJQuery,
      library.webJarJQueryUi,
      library.webJarNvD3,
      library.webJarPlay,
      library.akkaTestkit         % Test,
      library.scalaCheck          % Test,
      library.scalaTestPlusPlay   % Test
    )
  )
  .enablePlugins(AutomateHeaderPlugin, BuildInfoPlugin, GitVersioning, PlayScala)
  .settings(
    daemonUser := "tensei-frontend",
    daemonGroup := "tensei-frontend",
    debianPackageProvides in Debian += "tensei-frontend",
    debianPackageDependencies in Debian ++= Seq("openjdk-8-jre-headless", "pwgen"),
    debianPackageRecommends in Debian ++= Seq("libhyperic-sigar-java", "postgresql", "tensei-server"),
    defaultLinuxInstallLocation := "/opt",
    maintainer := "Wegtam GmbH <devops@wegtam.com>",
    maintainerScripts in Debian := maintainerScriptsAppend((maintainerScripts in Debian).value)(
      DebianConstants.Postinst -> List(
        s"stopService ${normalizedName.value}",
        s"rm -f /var/run/${normalizedName.value}/running.pid",
        s"rm -f /var/run/${normalizedName.value}/RUNNING_PID",
        s"mkdir ${defaultLinuxInstallLocation.value}/${normalizedName.value}/.database",
        s"chown ${daemonUser.value}:${daemonGroup.value} ${defaultLinuxInstallLocation.value}/${normalizedName.value}/.database",
        s"chmod +x ${defaultLinuxInstallLocation.value}/${normalizedName.value}/conf/init-production-conf.sh",
        s"${defaultLinuxInstallLocation.value}/${normalizedName.value}/conf/init-production-conf.sh ${defaultLinuxInstallLocation.value}/${normalizedName.value}/conf/production.conf",
        s"startService ${normalizedName.value}"
      ).mkString(" && ")
    ),
    requiredStartFacilities in Debian := Option("$network"),
    packageSummary := "Tensei-Data Frontend",
    packageDescription := "The tensei frontend is the user interface of a Tensei-Data system.",
    // Disable packaging of api-docs.
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    sources in (Compile,doc) := Seq.empty,
    // Exclude jars from unmanaged dependencies folder.
    mappings in Universal := (mappings in Universal).value.filter { case(jar, _) => jar.getParentFile != unmanagedBase.value },
    // Require tests to be run before building a debian package.
    packageBin in Debian := ((packageBin in Debian) dependsOn (test in Test)).value
  )
  .enablePlugins(JavaServerAppPackaging, JDebPackaging, SystemVPlugin)

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka            = "2.4.17"
      val akkaQuartzSched = "1.6.0-akka-2.4.x"
      val c3p0            = "0.9.5.2"
      val camel           = "2.14.4"
      val dfasdlCore      = "1.0"
      val dfasdlUtils     = "1.0.0"
      val h2              = "1.4.193"
      val jBCrypt         = "0.4.1"
      val logback         = "1.1.11"
      val play2auth       = "0.14.2"
      val playSlick       = "2.0.2"
      val postgresql      = "42.0.0"
      val scalaCheck        = "1.13.4"
      val scalaTestPlusPlay = "1.5.1"
      val tenseiApi       = "1.92.0"
      val webJarBootbox   = "4.4.0"
      val webJarBootstrap = "3.3.7"
      val webJarD3js      = "3.4.13"
      val webJarHandlebars = "1.3.0"
      val webJarJQuery     = "2.1.4"
      val webJarJQueryUi   = "1.11.4"
      val webJarNvD3       = "1.7.1"
      val webJarPlay      = "2.5.0"
    }
    val akkaCamel           = "com.typesafe.akka"      %% "akka-camel"            % Version.akka
    val akkaCluster         = "com.typesafe.akka"      %% "akka-cluster"          % Version.akka
    val akkaClusterTools    = "com.typesafe.akka"      %% "akka-cluster-tools"    % Version.akka
    val akkaSlf4j           = "com.typesafe.akka"      %% "akka-slf4j"            % Version.akka
    val akkaTestkit         = "com.typesafe.akka"      %% "akka-testkit"          % Version.akka
    val akkaQuartzScheduler = "com.enragedginger"      %% "akka-quartz-scheduler" % Version.akkaQuartzSched
    val c3p0                = "com.mchange"            %  "c3p0"                  % Version.c3p0
    val camelRestlet        = "org.apache.camel"       %  "camel-restlet"         % Version.camel
    val camelFtp            = "org.apache.camel"       %  "camel-ftp"             % Version.camel
    val dfasdlCore          = "org.dfasdl"             %% "dfasdl-core"           % Version.dfasdlCore
    val dfasdlUtils         = "org.dfasdl"             %% "dfasdl-utils"          % Version.dfasdlUtils
    val h2                  = "com.h2database"         %  "h2"                    % Version.h2
    val jBCrypt             = "de.svenkubiak"          %  "jBCrypt"               % Version.jBCrypt
    val logbackClassic      = "ch.qos.logback"         %  "logback-classic"       % Version.logback
    val play2auth           = "jp.t2v"                 %% "play2-auth"            % Version.play2auth
    val play2authTest       = "jp.t2v"                 %% "play2-auth-test"       % Version.play2auth
    val playSlick           = "com.typesafe.play"      %% "play-slick"            % Version.playSlick
    val playSlickEvolutions = "com.typesafe.play"      %% "play-slick-evolutions" % Version.playSlick
    val postgresql          = "org.postgresql"         %  "postgresql"            % Version.postgresql
    val scalaCheck          = "org.scalacheck"         %% "scalacheck"            % Version.scalaCheck
    val scalaTestPlusPlay   = "org.scalatestplus.play" %% "scalatestplus-play"    % Version.scalaTestPlusPlay
    val tenseiApi           = "com.wegtam.tensei"      %% "tensei-api"            % Version.tenseiApi
    val webJarBootbox       = "org.webjars"            %  "bootbox"               % Version.webJarBootbox
    val webJarBootstrap     = "org.webjars"            %  "bootstrap"             % Version.webJarBootstrap
    val webJarD3js          = "org.webjars"            %  "d3js"                  % Version.webJarD3js
    val webJarHandlebars    = "org.webjars"            %  "handlebars"            % Version.webJarHandlebars
    val webJarJQuery        = "org.webjars"            %  "jquery"                % Version.webJarJQuery
    val webJarJQueryUi      = "org.webjars"            %  "jquery-ui"             % Version.webJarJQueryUi
    val webJarNvD3          = "org.webjars"            %  "nvd3"                  % Version.webJarNvD3
    val webJarPlay          = "org.webjars"            %% "webjars-play"          % Version.webJarPlay
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  resolverSettings ++
  scalafmtSettings

lazy val commonSettings =
  Seq(
    headerLicense := Some(HeaderLicense.AGPLv3(s"2014 - $currentYear", "Contributors as noted in the AUTHORS.md file")),
    headerMappings := headerMappings.value + (FileType("html") -> HeaderCommentStyle.TwirlStyleBlockComment),
    unmanagedSources.in(Compile, headerCreate) ++= sources.in(Compile, TwirlKeys.compileTemplates).value,
    organization := "com.wegtam.tensei",
    organizationName := "Wegtam GmbH",
    startYear := Option(2014),
    licenses += ("AGPL-V3", url("https://www.gnu.org/licenses/agpl.html")),
    mappings.in(Compile, packageBin) += baseDirectory.in(ThisBuild).value / "LICENSE" -> "LICENSE",
    git.useGitDescribe := true,
    scalaVersion in ThisBuild := "2.11.11",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-target:jvm-1.8",
      "-unchecked",
      "-Xfuture",
      "-Xlint",
      "-Ydelambdafy:method",
      "-Ybackend:GenBCode",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      //"-Ywarn-unused-import", // This causes a lot of false warnings in play templates and routing files.
      "-Ywarn-value-discard"
    ),
    javacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-source", "1.8",
      "-target", "1.8"
    ),
    incOptions := incOptions.value.withNameHashing(nameHashing = true),
    wartremoverWarnings in (Compile, compile) ++= Warts.unsafe.filterNot(_ == Wart.DefaultArguments), // Exclude default arguments wart because of play templates.
    wartremoverExcluded += sourceDirectory.value / "conf" / "routes",
    wartremoverExcluded ++= routes.in(Compile).value
  )

lazy val resolverSettings =
  Seq(
    resolvers += Resolver.bintrayRepo("wegtam", "dfasdl"),
    resolvers += Resolver.bintrayRepo("wegtam", "tensei-data"),
    resolvers += "maven-restlet" at "http://maven.restlet.org"
  )

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtOnCompile.in(Sbt) := false,
    scalafmtVersion := "1.2.0"
  )

