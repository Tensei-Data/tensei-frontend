// The official Play plugin which includes a bunch of stuff.
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.12")
// Needed web plugins for the play framework.
addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-less"         % "1.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-jshint"       % "1.0.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-rjs"          % "1.0.8")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest"       % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-mocha"        % "1.1.0")
//addSbtPlugin("org.irundaia.sbt" % "sbt-sassify"      % "1.4.6")
// The sbt-sass plugin for SASS/SCSS support.
// Be sure to install sass (gem install sass)!!
addSbtPlugin("org.madoushi.sbt" % "sbt-sass"         % "1.0.0")

// Other plugins.
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"       % "0.7.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"             % "0.9.3")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"          % "2.0.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager" % "1.2.2")
addSbtPlugin("com.jsuereth"       % "sbt-pgp"             % "1.0.1")
addSbtPlugin("com.lucidchart"     % "sbt-scalafmt"        % "1.10")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"       % "1.5.0")
addSbtPlugin("org.wartremover"    % "sbt-wartremover"     % "2.1.1")

libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25" // Needed by sbt-git

// Needed to build debian packages via java (for sbt-native-packager).
libraryDependencies += "org.vafer" % "jdeb" % "1.5" artifacts Artifact("jdeb", "jar", "jar")

