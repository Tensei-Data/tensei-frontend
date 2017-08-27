# Tensei Frontend

The graphical user interface for a Tensei (転成) system.
It provides a web based user interface and additional features like
cronjobs and triggers.

## Resources

The main website for Tensei-Data is located at: https://www.wegtam.com/products/tensei-data

### Mailing lists

[![Google-Group tensei-data](https://img.shields.io/badge/group-tensei--data-brightgreen.svg)](https://groups.google.com/forum/#!forum/tensei-data)
[![Google-Group tensei-data-dev](https://img.shields.io/badge/group-tensei--data--dev-orange.svg)](https://groups.google.com/forum/#!forum/tensei-data-dev)

## System requirements

To achieve more fine grained statistics about the application it is
recommended to have the hyperic sigar library installed. To use it you
have to set the `java.library.path` property when running the 
application.

The frontend uses an embedded H2 database by default. If you want to 
switch to a PostgreSQL db then you'll need at least version 9.4.

## System architecture and provisioning

The Tensei-Data system is build upon three components:

1. Tensei-Server
2. Tensei-Frontend
3. At least one Tensei-Agent

To be able to run Tensei-Data you have to start at least one of each components.

For development purposes it is feasible to simply start each one from the sbt prompt via the `run` task.

### Provisioning / Deployment

To be able to provision the system components a packaging configuration for the [sbt native packager](https://github.com/sbt/sbt-native-packager) plugin is included. The recommended way is to create debian packages via the `debian:packageBin` sbt task. Resulting debian packages can be installed on a debian or ubuntu system. Before the package is build the test suite will be executed.

    % sbt clean debian:packageBin

We recommend to use the `gdebi` tool on ubuntu because it will automatically fetch required dependencies.

The packages include system startup scripts that will launch them upon system boot.

