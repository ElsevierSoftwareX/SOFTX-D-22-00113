# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.3.1] - 2022-04-29

### Added

- Releases folder with APKs.
- Android Studio project icon.
- GNU General Public License v3.0.

## [2.3.0] - 2022-03-22

### Added

- Data sensor class hierarchy to reduce duplicated code.
- Source code is now documented as much as possible.

### Changed

- External sensors available now use our new data sensor class hierarchy.
- Android's managed sensors now use our new data sensor class hierarchy.
- Main activity's user interface is now driven by a recycler view instead of a series of text views.
- Organize code into folders.
- Split application logic from presentation.
- Source code is splitted into smaller fragments as much as possible.
- Project is now under version control via Git, hosted in GitLab.

### Removed

- Obsolete MIMU22BT external data sensor.
- Obsolete LPMSB external data sensor.
- Section about "changelog" vs "CHANGELOG".

### Fixed

- Follow the same coding style across the whole project.
- Naming across the whole project is now consistent.
- Reduce warnings hinted by Android Studio as much as possible.
